# Changelog

Each entry's heading is the exact `versionName` (matches the app's settings
"Version" label and the GitHub release tag `V<versionName>`). Newest first.

Used as the release notes body when publishing via `publish_release.ps1`
(the script pulls the entry matching the current `versionName` straight out
of this file).

## 1.004
- Changed: the "both RGB and DMX selected" indicator is now a steady ring in
  the blended RGB/DMX color with an occasional random flicker, like a
  slightly flaky neon tube - also fixed the custom RGB color popup's slider
  send timing, which was debouncing until the drag paused instead of
  throttling live like every other continuous control in the app.

## 1.003
- Changed: the zone-selector pill's "both RGB and DMX selected" indicator
  was a plain white breathing glow, easy to mistake for a dim single-zone
  border. It's now a full-color rainbow ring continuously rotating around
  the pill.

## 1.002
- Fixed: some real LEDCAR-01 units never showed up to connect. The scanner
  required the device to advertise its BLE service UUID before it would even
  attempt a connection, but some units (confirmed via nRF Connect against a
  live LEDCAR-01-4000) only advertise Flags + Complete Local Name, no
  service UUID at all - so the app silently ignored them every time, not a
  timeout or failed connection. Now also matches on the `LEDCAR-01` name
  prefix, same as the rest of the app already does.

## 1.001
- Initial public release: dual RGB/DMX zone control, HSV color wheel, 200+
  built-in effects, per-zone saved color swatches, live ambient
  car-interior lighting preview (dash neon strip + footwell/door-handle
  glow, independently tinted per zone and brightness-reactive),
  connection-aware control locking with auto-reconnect, and in-app update
  checking against GitHub Releases.
