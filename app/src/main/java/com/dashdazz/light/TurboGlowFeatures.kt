package com.dashdazz.light

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/** Read-only capability report. Never probes registers or changes a light. */
data class DeviceReport(
    val model: String,
    val shizuku: Boolean,
    val root: Boolean,
    val aw21024: Boolean,
    val stockConflict: Boolean
)

object DeviceDiagnostics {
    fun snapshot(context: Context): DeviceReport = DeviceReport(
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        shizuku = LightsBridge.hasShizukuPermission(),
        root = LightsBridge.isRootAvailable(),
        aw21024 = File("/sys/class/leds/aw21024_led").exists(),
        stockConflict = ConflictDetector.isStockMusicLightActive(context)
    )
}

/** Explicit HyperOS settings; callers must present their own opt-in UI. */
object SystemRingSettings {
    private const val STRIP_ENABLED = "settings_strip_light_enable"
    private const val BRIGHTNESS = "lamp_effect_brightness"
    const val MIN_BRIGHTNESS = 80
    const val MAX_BRIGHTNESS = 255

    fun stripEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, STRIP_ENABLED, 0) != 0

    fun brightness(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, BRIGHTNESS, MAX_BRIGHTNESS)
            .coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

    fun setStripEnabled(context: Context, enabled: Boolean): Boolean =
        putGlobal(context, STRIP_ENABLED, if (enabled) 2 else 0)

    fun setBrightness(context: Context, value: Int): Boolean =
        putGlobal(context, BRIGHTNESS, value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS))

    fun restoreDefaults(context: Context): Boolean =
        setBrightness(context, MAX_BRIGHTNESS) && setStripEnabled(context, false)

    private fun putGlobal(context: Context, key: String, value: Int): Boolean {
        try {
            if (Settings.Global.putInt(context.contentResolver, key, value)) return true
        } catch (_: SecurityException) {
        }
        if (LightsBridge.isRootAvailable()) {
            return LightsBridge.runRoot("settings put global $key $value")
        }
        return false
    }
}

/** HyperOS music-light package switches are separate from TurboGlow audio capture. */
object SystemRhythmLight {
    private const val GET = "Get_MusicLight_Package_State="
    private const val SET = "Set_MusicLight_Package_State="

    fun states(context: Context): Map<String, Boolean> = runCatching {
        val raw = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getParameters(GET)
        raw.split(';').mapNotNull { item ->
            val parts = item.split('=', limit = 2)
            if (parts.size == 2 && parts[1] in setOf("true", "false")) parts[0] to (parts[1] == "true") else null
        }.toMap()
    }.getOrDefault(emptyMap())

    fun setEnabled(context: Context, packageName: String, enabled: Boolean): Boolean {
        runCatching {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .setParameters("$SET$packageName:$enabled")
        }
        return states(context)[packageName] == enabled
    }
}

data class LightingScene(
    val id: Long,
    val name: String,
    val mode: Int,
    val color: Int,
    val speedMs: Long,
    val brightness: Float,
    val palette: Int,
    val dualStyle: Int,
    val splitA: Int,
    val splitB: Int,
    val spinA: Int,
    val spinB: Int,
    val hueOffset: Int,
    val audioReactive: Boolean,
    val frameMinMs: Long
)

object SceneStore {
    private const val KEY = "turboglow_scenes_v1"
    private const val MAX_SCENES = 24

    fun all(context: Context): List<LightingScene> = runCatching {
        val raw = context.getSharedPreferences("dashdazz_prefs", Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { scene(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(context: Context, name: String, state: DashDazzState): LightingScene {
        val clean = name.trim().take(32).ifBlank { "Scene" }
        val next = LightingScene(System.currentTimeMillis(), clean, state.mode, state.color, state.speedMs,
            state.brightness, state.palette, state.dualStyle, state.splitColorA, state.splitColorB,
            state.spinColorA, state.spinColorB, state.duoHueOffset, state.audioReactive, state.frameMinMs)
        val updated = (all(context).filterNot { it.name.equals(clean, true) } + next).takeLast(MAX_SCENES)
        write(context, updated)
        return next
    }

    fun delete(context: Context, id: Long) = write(context, all(context).filterNot { it.id == id })

    fun apply(context: Context, scene: LightingScene, start: Boolean = true) {
        DashDazzService.updateSettings(context, mode = scene.mode, color = scene.color, speedMs = scene.speedMs,
            brightness = scene.brightness, palette = scene.palette, dualStyle = scene.dualStyle,
            splitColorA = scene.splitA, splitColorB = scene.splitB, spinColorA = scene.spinA,
            spinColorB = scene.spinB, duoHueOffset = scene.hueOffset, audioReactive = scene.audioReactive,
            frameMinMs = scene.frameMinMs)
        if (start) DashDazzService.start(context, scene.mode, scene.color, scene.speedMs, scene.brightness,
            DashDazzService.stateFlow.value.screenMode, false, scene.palette, scene.dualStyle,
            scene.splitA, scene.splitB, scene.spinA, scene.spinB, scene.hueOffset)
    }

    private fun write(context: Context, scenes: List<LightingScene>) {
        val json = JSONArray().apply { scenes.forEach { put(json(it)) } }.toString()
        context.getSharedPreferences("dashdazz_prefs", Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    private fun json(s: LightingScene) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("mode", s.mode); put("color", s.color)
        put("speed", s.speedMs); put("brightness", s.brightness.toDouble()); put("palette", s.palette)
        put("dual", s.dualStyle); put("splitA", s.splitA); put("splitB", s.splitB)
        put("spinA", s.spinA); put("spinB", s.spinB); put("offset", s.hueOffset)
        put("audio", s.audioReactive); put("frame", s.frameMinMs)
    }

    private fun scene(o: JSONObject) = LightingScene(o.getLong("id"), o.getString("name"), o.getInt("mode"),
        o.getInt("color"), o.getLong("speed"), o.getDouble("brightness").toFloat(), o.getInt("palette"),
        o.getInt("dual"), o.getInt("splitA"), o.getInt("splitB"), o.getInt("spinA"), o.getInt("spinB"),
        o.getInt("offset"), o.optBoolean("audio"), o.optLong("frame", Prefs.FRAME_SMOOTH_MS))
}

data class TimeSchedule(val id: Long, val sceneId: Long, val startMinute: Int, val endMinute: Int, val days: Set<Int>, val enabled: Boolean)

object ScheduleStore {
    private const val KEY = "turboglow_schedules_v1"
    fun all(context: Context): List<TimeSchedule> = runCatching {
        val raw = context.getSharedPreferences("dashdazz_prefs", Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val items = JSONArray(raw)
        (0 until items.length()).map { i -> items.getJSONObject(i).let { o ->
            TimeSchedule(o.getLong("id"), o.getLong("scene"), o.getInt("start"), o.getInt("end"),
                o.getString("days").split(',').mapNotNull { it.toIntOrNull() }.toSet(), o.optBoolean("enabled", true))
        } }
    }.getOrDefault(emptyList())
    fun save(context: Context, schedule: TimeSchedule) { write(context, all(context).filterNot { it.id == schedule.id } + schedule); AutomationManager.reschedule(context) }
    fun delete(context: Context, id: Long) { write(context, all(context).filterNot { it.id == id }); AutomationManager.reschedule(context) }
    private fun write(context: Context, schedules: List<TimeSchedule>) {
        val items = JSONArray().apply { schedules.forEach { s -> put(JSONObject().apply { put("id", s.id); put("scene", s.sceneId); put("start", s.startMinute); put("end", s.endMinute); put("days", s.days.joinToString(",")); put("enabled", s.enabled) }) } }
        context.getSharedPreferences("dashdazz_prefs", Context.MODE_PRIVATE).edit().putString(KEY, items.toString()).apply()
    }
}

object AutomationManager {
    const val ACTION_SCHEDULE = "dev.turboglow.ring.SCHEDULE"
    const val ACTION_SCHEDULE_STOP = "dev.turboglow.ring.SCHEDULE_STOP"
    fun reschedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        ScheduleStore.all(context).forEach { s ->
            val start = pending(context, s.id, ACTION_SCHEDULE)
            val stop = pending(context, s.id, ACTION_SCHEDULE_STOP)
            if (!s.enabled) { manager.cancel(start); manager.cancel(stop); return@forEach }
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(s, s.startMinute), start)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(s, s.endMinute), stop)
        }
    }
    fun run(context: Context, id: Long) {
        val schedule = ScheduleStore.all(context).firstOrNull { it.id == id && it.enabled } ?: return
        SceneStore.all(context).firstOrNull { it.id == schedule.sceneId }?.let { SceneStore.apply(context, it) }
        reschedule(context)
    }
    fun stop(context: Context, id: Long) {
        if (ScheduleStore.all(context).any { it.id == id && it.enabled }) DashDazzService.stop(context)
        reschedule(context)
    }
    fun onBatteryChanged(context: Context, intent: Intent) {
        val threshold = Prefs.batteryThreshold(context)
        val sceneId = Prefs.batterySceneId(context)
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) level * 100 / scale else return
        if (threshold !in 1..100 || sceneId <= 0L) return
        if (percent > threshold) {
            Prefs.setBatteryTriggered(context, false)
        } else if (!Prefs.batteryTriggered(context)) {
            Prefs.setBatteryTriggered(context, true)
            SceneStore.all(context).firstOrNull { it.id == sceneId }?.let { SceneStore.apply(context, it) }
        }
    }
    private fun pending(context: Context, id: Long, action: String): PendingIntent {
        val intent = Intent(context, AutomationReceiver::class.java).setAction(action).putExtra("schedule", id)
        val code = 31 * id.hashCode() + action.hashCode()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun nextTime(s: TimeSchedule, minute: Int): Long {
        val now = Calendar.getInstance()
        repeat(8) { offset ->
            val candidate = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset); set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            if (candidate.get(Calendar.DAY_OF_WEEK) in s.days && candidate.timeInMillis > System.currentTimeMillis()) return candidate.timeInMillis
        }
        return System.currentTimeMillis() + 24L * 60L * 60L * 1000L
    }
}

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AutomationManager.ACTION_SCHEDULE -> AutomationManager.run(context, intent.getLongExtra("schedule", -1L))
            AutomationManager.ACTION_SCHEDULE_STOP -> AutomationManager.stop(context, intent.getLongExtra("schedule", -1L))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> AutomationManager.reschedule(context)
        }
    }
}
