# TurboGlow

TurboGlow is a modern, smooth camera-ring controller for the Redmi Turbo 4
Pro and compatible Xiaomi/POCO HyperOS devices using the AW21024 LED
controller. It is a GPL-3.0-or-later derivative of
[DashDazz](https://github.com/olivia-hye/DashDazz), informed by the Binder
research in [HaloLight](https://github.com/fan-world-me/HaloLight).

## Features

- Rainbow, solid, breathe, split, spin, police, and beat-reactive effects
- Audio reaction with beat and frequency-band tracking
- Notification and charging automations, Quick Settings tile, live preview
- Smooth/Balanced/Eco frame governors and hardware breathing where rooted
- Shizuku for the safe Binder animation path; root for direct AW21024 effects
- Device diagnostics, explicit HyperOS brightness/rhythm controls, saved scenes,
  time schedules, charging indication, and low-battery scene automation

## How it controls the ring

For smooth non-root animation, TurboGlow sends colour frames to the Xiaomi
`miui.lights.ILightsManager` Binder service through Shizuku. The working
call is `setColorCommon(..., styleType = 3, ...)`, which targets the system's
music-rhythm camera-ring zone without a per-frame auto-off timer. Root adds
the direct `/sys/class/leds/aw21024_led` register path for split regions and
hardware breathing.

See [the Redmi Turbo 4 Pro architecture note](docs/REDMI_TURBO_4_PRO.md) for
the complete design and limitations.

## Build

```sh
./gradlew :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/`. A release build needs a
new signing key configured in `local.properties`; never commit that key.

## Publishing this fork

The in-app updater uses this repository's GitHub Releases endpoint. It will
only offer an APK signed with the same certificate as the installed app.

Generate a TurboGlow-specific signing key and configure these GitHub Actions
secrets before publishing: `TURBOGLOW_KEYSTORE_BASE64`,
`TURBOGLOW_STORE_PASSWORD`, `TURBOGLOW_KEY_PASSWORD`, and
`TURBOGLOW_KEY_ALIAS`. The key must remain private; losing it prevents future
APK updates from using the same Android signing lineage.

## License and notices

GPL-3.0-or-later. Keep the upstream notices and source availability when
redistributing this derivative.
