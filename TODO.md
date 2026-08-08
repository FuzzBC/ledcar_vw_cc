# TODO / Roadmap

Future work, not yet scoped into implementation tasks. Newest/highest-level
ideas first — each needs its own research pass before it becomes real work.

## 1. Compatibility with older Android versions (e.g. Junsun head units)

`LedCar` currently targets `minSdk 26`. Cheap Android head
units (Junsun and similar brands) often ship on much older Android builds
(8.1, 7.1, sometimes older forks). Lowering `minSdk` isn't just a number
change - a few BLE APIs currently in use are version-gated:

- `BluetoothDevice.connectGatt(..., TRANSPORT_LE, PHY_LE_1M_MASK, Handler)`
  (the 5-arg overload with an explicit PHY) needs API 26; older devices
  need the classic 3-arg `connectGatt(context, autoConnect, callback)`.
- Runtime permission model differs a lot pre-API 31 (`BLUETOOTH_SCAN`/
  `BLUETOOTH_CONNECT` vs the old `BLUETOOTH`/`BLUETOOTH_ADMIN` + location
  permission dance) - `BleDeviceManager`'s permission request flow would
  need a real branch, not just a manifest `maxSdkVersion` tweak.
- Need an actual low-end device (or an emulator image) to test against -
  can't just lower the number and assume it works.

## 2. IFTTT / Tasker / Automate integration (time-of-day color presets)

Goal: let external automation apps trigger the app - e.g. "sunset → warm
white on both zones", "sunrise → cancel effect". Tasker and Automate both
support sending Android Intents to a target app/broadcast receiver; IFTTT
on Android typically goes through Tasker as a bridge rather than talking to
apps directly.

Needs:
- A documented Intent contract (action name, package, extras for zone/
  color/brightness/mode) - a small `IntentCommandReceiver` (manifest
  `<receiver>`) that maps incoming extras onto the same internal calls
  `MainActivity` already uses (`applyColor`, `broadcastForActiveZones`,
  etc.).
- Decide whether this needs the app in the foreground, or should work
  headless (see #4 below - closely related).
- Optional: a minimal Tasker plugin (`Locale`/Tasker plugin API) instead of
  raw intents, for a nicer Tasker-side picker UI.

## 3. Dedicated layout for Android head units

Head unit screens are usually wide landscape, sometimes lower DPI, often
touch-only with no back/home gesture nav to rely on. The current layout is
phone-portrait-first (see #6 below, which would lock portrait on phones -
head units need the opposite). Needs its own `layout-land` (or a
sw-qualifier) variant, not just a stretched version of the phone layout -
likely a side-by-side arrangement (wheel + zone pill on one side, effects/
saved colors on the other) rather than the current vertical stack.

## 4. Keep-alive on head units for background automation

For #2 to be useful on a head unit, the app needs to keep its BLE
connection alive and keep listening for incoming automation commands even
when not the foreground app - head units are often aggressive about
killing backgrounded apps. Likely needs:
- A foreground `Service` (with a persistent notification, matching
  `BleDeviceManager`'s existing connection-management logic) instead of
  relying on the Activity's lifecycle.
- Battery-optimization / "allow background activity" exemption handling,
  which varies a lot by OEM skin on these devices.

## 5. Receiving commands from Agama Launcher

Agama is a popular third-party launcher for Android head units with its
own plugin/widget ecosystem. Needs research into what Agama actually
exposes (app shortcuts? a documented intent/plugin API? car-settings
integration?) before this is scoped - nothing implemented or confirmed
feasible yet.

---

*Note: item 3 needs a landscape layout for head units, while phones should
stay portrait-locked (already done - see CHANGELOG). Keep those two
changes from fighting each other when both land.*
