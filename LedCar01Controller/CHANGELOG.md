# Changelog

Each entry's heading is the exact `versionName` (matches the app's settings
"Version" label and the GitHub release tag `V<versionName>`). Newest first.

Bundled into the app at build time (`copyChangelog` in `app/build.gradle`,
copied to `app/src/main/assets/CHANGELOG.md`) and used as the release notes
body when publishing via `publish_release.ps1`.

## 1.001
- Initial public release: dual RGB/DMX zone control, HSV color wheel, 200+
  built-in effects, per-zone saved color swatches, live ambient
  car-interior lighting preview (dash neon strip + footwell/door-handle
  glow, independently tinted per zone and brightness-reactive),
  connection-aware control locking with auto-reconnect, and in-app update
  checking against GitHub Releases.
