package com.dashdazz.light

import android.app.TimePickerDialog
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SuperArrow
import top.yukonga.miuix.kmp.basic.SuperSwitch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TurboGlowCapabilityPanels(isRootAvailable: Boolean) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var report by remember { mutableStateOf(DeviceDiagnostics.snapshot(context)) }
    var stripEnabled by remember { mutableStateOf(SystemRingSettings.stripEnabled(context)) }
    var systemBrightness by remember { mutableIntStateOf(SystemRingSettings.brightness(context)) }
    var rhythmStates by remember { mutableStateOf(SystemRhythmLight.states(context)) }
    var scenes by remember { mutableStateOf(SceneStore.all(context)) }
    var schedules by remember { mutableStateOf(ScheduleStore.all(context)) }
    var startMinute by remember { mutableIntStateOf(20 * 60) }
    var endMinute by remember { mutableIntStateOf(8 * 60) }
    var sceneIndex by remember { mutableIntStateOf(0) }
    var batteryThreshold by remember { mutableIntStateOf(Prefs.batteryThreshold(context)) }
    var batterySceneIndex by remember { mutableIntStateOf(0) }
    var days by remember { mutableStateOf((1..7).toSet()) }

    fun refresh() {
        report = DeviceDiagnostics.snapshot(context)
        stripEnabled = SystemRingSettings.stripEnabled(context)
        systemBrightness = SystemRingSettings.brightness(context)
        rhythmStates = SystemRhythmLight.states(context)
        scenes = SceneStore.all(context)
        schedules = ScheduleStore.all(context)
        sceneIndex = sceneIndex.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))
        batterySceneIndex = batterySceneIndex.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))
    }
    fun fmt(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    fun pick(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Device diagnostics", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
            Text(
                "${report.model}\nBinder/Shizuku: ${if (report.shizuku) "ready" else "unavailable"} · Root: ${if (report.root) "ready" else "unavailable"}\nAW21024 node: ${if (report.aw21024) "found" else "not visible"}${if (report.stockConflict) "\nStock rhythmic light is active — disable it to avoid conflicts." else ""}",
                fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            SuperArrow(title = "Refresh capability check", summary = "Read-only; does not change the ring", onClick = { refresh() })
        }
    }

    Spacer(Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("System rhythmic light", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
            Text("HyperOS per-app music lighting. This is separate from TurboGlow audio reaction.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            if (rhythmStates.isEmpty()) {
                Text("No HyperOS rhythm-capable apps reported by this firmware.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(16.dp, 8.dp))
            } else {
                rhythmStates.toSortedMap().forEach { (pkg, enabled) ->
                    SuperSwitch(title = pkg, summary = "System-managed rhythm lighting", checked = enabled, onCheckedChange = { desired ->
                        scope.launch { withContext(Dispatchers.IO) { SystemRhythmLight.setEnabled(context, pkg, desired) }; refresh() }
                    })
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Battery automation", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
            Text("Runs once when battery first reaches the threshold; reset when it rises above it.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            Text("Low-battery threshold: ${if (batteryThreshold == 0) "Off" else "$batteryThreshold%"}", fontSize = 13.sp, modifier = Modifier.padding(16.dp, 8.dp))
            Slider(progress = batteryThreshold.toFloat(), onProgressChange = { batteryThreshold = it.toInt().coerceIn(0, 100) }, minValue = 0f, maxValue = 100f, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (scenes.isNotEmpty()) {
                SuperArrow(title = "Low-battery scene: ${scenes[batterySceneIndex].name}", summary = "Tap to choose next saved scene", onClick = { batterySceneIndex = (batterySceneIndex + 1) % scenes.size })
                SuperArrow(title = "Save battery automation", summary = if (batteryThreshold == 0) "Disables low-battery automation" else "Runs at $batteryThreshold%", onClick = {
                    Prefs.setBatteryAutomation(context, batteryThreshold, scenes[batterySceneIndex].id)
                })
            } else {
                Text("Save a scene before enabling low-battery automation.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(16.dp, 8.dp))
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("System controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
            Text("Optional HyperOS controls. They may briefly interrupt the active effect.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            SuperSwitch(
                title = "Enable system strip light",
                summary = if (isRootAvailable) "Writes the HyperOS global switch" else "Requires granted secure-settings access or root",
                checked = stripEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { withContext(Dispatchers.IO) { SystemRingSettings.setStripEnabled(context, enabled) }; refresh() }
                }
            )
            Text("System brightness: $systemBrightness", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Slider(
                progress = systemBrightness.toFloat(),
                onProgressChange = { systemBrightness = it.toInt().coerceIn(SystemRingSettings.MIN_BRIGHTNESS, SystemRingSettings.MAX_BRIGHTNESS) },
                minValue = SystemRingSettings.MIN_BRIGHTNESS.toFloat(), maxValue = SystemRingSettings.MAX_BRIGHTNESS.toFloat(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            SuperArrow(title = "Apply system brightness", summary = "Applies $systemBrightness and then refreshes the status", onClick = {
                scope.launch { withContext(Dispatchers.IO) { SystemRingSettings.setBrightness(context, systemBrightness) }; refresh() }
            })
            SuperArrow(title = "Restore HyperOS defaults", summary = "Brightness 255; system strip light off", onClick = {
                scope.launch { withContext(Dispatchers.IO) { SystemRingSettings.restoreDefaults(context) }; refresh() }
            })
        }
    }

    Spacer(Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Saved scenes", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
            SuperArrow(title = "Save current scene", summary = "Stores colours, mode, speed, audio, and governor", onClick = {
                val field = EditText(context).apply { hint = "Scene name" }
                AlertDialog.Builder(context).setTitle("Save scene").setView(field)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ -> SceneStore.save(context, field.text.toString(), DashDazzService.stateFlow.value); refresh() }
                    .show()
            })
            scenes.forEach { scene ->
                SuperArrow(title = scene.name, summary = "Tap to apply · long-term settings are preserved", onClick = { SceneStore.apply(context, scene); refresh() })
            }
            if (scenes.isEmpty()) Text("Save a scene to use it in schedules.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(16.dp, 8.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Schedule automation", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
            Text("Schedules start the selected scene. Charging indicator is configured above in Smart Automation.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            SuperArrow(title = "Start: ${fmt(startMinute)}", summary = "Choose start time", onClick = { pick(startMinute) { startMinute = it } })
            SuperArrow(title = "End: ${fmt(endMinute)}", summary = "Stored with this schedule for display and future stop policy", onClick = { pick(endMinute) { endMinute = it } })
            if (scenes.isNotEmpty()) {
                val selected = scenes[sceneIndex]
                SuperArrow(title = "Scene: ${selected.name}", summary = "Tap to choose next saved scene", onClick = { sceneIndex = (sceneIndex + 1) % scenes.size })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { day ->
                    val on = day in days
                    Box(Modifier.weight(1f).background(if (on) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).clickable { days = if (on) days - day else days + day }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(listOf("S", "M", "T", "W", "T", "F", "S")[day - 1], fontSize = 12.sp, color = if (on) Color.White else MiuixTheme.colorScheme.onSurface)
                    }
                }
            }
            SuperArrow(title = "Add time schedule", summary = if (scenes.isEmpty()) "Save a scene first" else "Runs ${scenes[sceneIndex].name} on selected days", onClick = {
                if (scenes.isNotEmpty() && days.isNotEmpty()) {
                    ScheduleStore.save(context, TimeSchedule(System.currentTimeMillis(), scenes[sceneIndex].id, startMinute, endMinute, days, true)); refresh()
                }
            })
            schedules.forEach { schedule ->
                val name = scenes.firstOrNull { it.id == schedule.sceneId }?.name ?: "Missing scene"
                SuperArrow(title = "$name · ${fmt(schedule.startMinute)}–${fmt(schedule.endMinute)}", summary = "Tap to remove", onClick = { ScheduleStore.delete(context, schedule.id); refresh() })
            }
        }
    }
}
