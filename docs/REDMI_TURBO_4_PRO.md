# Redmi Turbo 4 Pro lighting architecture

TurboGlow targets the camera-ring implementation found on the Redmi Turbo 4
Pro and closely related HyperOS devices. It is a derivative of DashDazz and
retains GPL-3.0-or-later licensing.

## The layers

```text
TurboGlow foreground service
  ├─ Shizuku → miui.lights.ILightsManager Binder service
  │             └─ HyperLightsService → Xiaomi light policy → AW21024 driver
  └─ root → /sys/class/leds/aw21024_led/{reg,hwen,effect}
                └─ I²C AW21024, a 24-channel RGB LED controller
```

The controller exposes multiple RGB channels. TurboGlow's root-only split
and spin modes program controller registers/masks so two physical regions
can be addressed. The normal colour path updates the common RGB registers.
The hardware-breathe path programs the AW21024 engine, so it does not need
continuous app frames.

## What each access path can do

| Access | Mechanism | Reliable features |
|---|---|---|
| Shizuku | Binder transaction 2, `setColorCommon(color, pkg, 3, 0)` | Solid colour, rainbow, app-driven breathe and audio effects |
| Root | AW21024 sysfs registers | Everything above plus hardware breathe, split regions and spin masks |
| Neither | None | UI preview only; no light control |

The Binder service name is `miui.lights.ILightsManager`. `styleType = 3`
selects Xiaomi's music-rhythm light zone, which is the camera ring on the
tested hardware. It performs an immediate state update and is suitable for
the 20–100 ms animation governor.

`setCustomLight` (transaction 4) is useful for the camera-style path but
creates an auto-off timer for every call. Repeating it rapidly can flicker,
so TurboGlow intentionally uses transaction 2 for animated frames.

## Smoothness and battery policy

The animation service is foreground-owned and sends frames from a background
coroutine. The UI writes settings atomically through its state flow, while
the preview uses the last colours confirmed for the hardware path. The three
governors are 20 ms (Smooth), 40 ms (Balanced), and 80 ms (Eco); battery
saver selects Eco. Rooted breathing is delegated to the LED controller.

## Device validation checklist

1. Start Shizuku and grant TurboGlow access, or grant root access.
2. In the Lighting tab select Solid and turn the ring on.
3. If Shizuku works but Dual Ring is hidden, this is expected: direct
   AW21024 register control is root-only.
4. Disable the stock HyperOS rhythmic-light effect before running another
   animation source, otherwise both writers compete for the same zone.

Firmware interfaces are undocumented and can change. This document records
the tested architecture, not a guarantee for every HyperOS build.

## TurboGlow automation

Saved scenes persist the effect configuration (colours, palette, mode, speed,
brightness, audio modifier, dual settings, and frame governor). Time schedules
run a chosen scene on selected days and send a matching stop alarm at the end
of its window. Charging indication is handled by the existing lifecycle power
receiver. Low-battery automation runs its selected scene once when the battery
crosses the configured threshold and resets only after the level rises again.
