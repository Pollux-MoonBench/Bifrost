# Changelog

All notable changes to this project will be documented in this file.

## [1.2.0-beta] - 2026-04-19

### Added
- **External-app IPC API** — third-party apps can now control Bifrost LEDs via ordered broadcasts. Features:
  - `ACTION_DISPLAY` — live LED override with configurable effect, colour (left/right independently), intensity, speed, smoothness, priority, and auto-expiring duration terminator
  - `ACTION_CLEAR` — end your app's active override immediately
  - `ACTION_INSTALL_PROFILE` / `ACTION_UNINSTALL_PROFILE` — install and remove named presets scoped to the caller package
  - Priority arbitration between multiple callers (0–100 scale; same-app commands always win)
  - Snapshot/revert lifecycle: Bifrost saves its state before an override and restores it automatically when the override ends
  - Per-UID token-bucket rate limiter (8 burst, 4 sustained/s)
  - Caller verified via `Binder.getCallingUid()` + `PackageManager.getPackagesForUid()` — immune to intent spoofing
  - All commands silently dropped when service is not running (Android 12+ foreground-service-start restrictions respected)
  - Custom permission `com.moonbench.bifrost.permission.CONTROL_LEDS` at `normal` protection level
- **"Allow third-party LED control"** toggle in Behaviour settings; enabling it also sets `allowBackgroundRun` so the service survives activity close
- `PackageRemovedReceiver` — auto-cleans externally installed presets and app-profile mappings when the owning app is uninstalled
- `LedPreset.ownerPackage` field for tracking which external package installed a preset; round-tripped through JSON
- `AppProfileManager.removeMappingsReferencing()` — bulk mapping cleanup used by `PackageRemovedReceiver`
- `PresetController.reloadFromPrefs()` — called on `MainActivity.onResume` so presets installed while the UI is backgrounded appear immediately on next open
- `INTEGRATING.md` — comprehensive developer integration guide with Kotlin and Java examples, recipes, a complete drop-in wrapper class, and an effect reference table

### Changed
- `checkAutoProfileSwitch` returns early while an external override is active, preventing profile-switching from interrupting the caller's effect
- `clearPendingCallbacks` and `cleanupAndStop` now also cancel the external expiry runnable and null out the override state

## [1.1.3-beta] - 2026-04-10

### Added
- Adaptive LED brightness: LED output scales 25–100% with screen brightness via a ContentObserver on `Settings.System.SCREEN_BRIGHTNESS`; toggle in Behaviour settings tab, preference persisted.
- Theme import/export: export/import the full theme bundle (JSON, schema `bifrost_theme_bundle v1`) from the Themes tab; mirrors the preset backup/restore UX.
- Tabbed settings panel: settings reorganized into tabs (Behaviour, Themes, About) for easier navigation.
- OLED Purple theme preset.
- Watercolor logo variant; toggle in settings to switch between logos.

### Changed
- LEDService: added `effectiveBrightness()` helper and `mountScreenBrightnessObserver` / `unmount`; adaptive-brightness extra propagated through `createLedServiceIntent()` and `sendLiveUpdateToLedService()`.
- `ThemeArchiveTransfer` handles JSON bundle serialization/deserialization for theme export/import.
- `BackupArchiveTransfer` refactored alongside `ThemeArchiveTransfer` for consistency.

## [1.1.0] - 2026-03-17
This release consolidates several enhancements and new features added after 1.0.4. It focuses on preset management, app-profile (per-app) behavior, new and customizable animations, export/import of presets, and several UX and stability improvements.

### Added
- Auto-start on boot: option to auto-start Bifrost when the device boots. If auto-start is skipped because the last preset requires screen-capture (MediaProjection) and permission is missing, a notification is shown that opens the app and prompts the user for the required permission.
- Preset export/import (versioned JSON): export presets (including metadata and custom images) to a JSON bundle and import them back. Import supports modes to replace existing presets or append (Add).
- Per-app (app-profile) preset switching: map foreground apps to presets so Bifrost switches presets automatically when a mapped app becomes foreground.
- App-profile UX improvements: first-time popup explaining app-profile, immediate resolution when enabling app-profile, option to mark a preset as the default fallback when no assigned app is foreground, and a toggle to control animation fallback behavior for app-mode.
- Preset artwork editor: upload a custom image, paste/apply emoji, or assign an installed app's icon as a preset visual. The artwork sheet shows previews and allows selecting built-in icons or assigned-app icons.
- Delete-all presets: long-press on the trash/delete icon to remove all presets after confirmation (also removes stored custom images and reverts to a default preset).
- New animation: CPU temperature animation (colors react to CPU temperature readings).
- New battery indicator animation: a dedicated battery indicator animation with options (breathe while charging, charging speed indicator, flash when fully charged).
- Per-preset color overrides: ability to specify custom palettes per preset for the Battery Indicator (low / mid / high) and CPU Temperature (cool / warm / hot) animations. Overrides are selectable in the UI (color swatches) and persist with the preset.
- Persistent notification control: toggle to enable/disable the LEDService persistent notification.
- New horizontal preset presentation

### Changed
- Preset model: presets now store additional metadata (assigned app package name, `isAppProfileDefault` flag, app icon assignment, custom image filename, and color overrides). Export/import JSON format was extended accordingly but remains backward-compatible.
- LEDService: receives optional color override extras and restarts animations when palette overrides change; supports a force-app-profile-resolution action to re-evaluate foreground-app mappings immediately.
- AppProfileManager: improved resolution logic (fallback default preset support, suppression of redundant switches, better handling when Bifrost itself is foreground) and robust mapping persistence.
- UI refinements: smoother cover-flow/carousel behavior (better scroll locking and snap), improved BottomSheet artwork editor, refined switch colors and small visual polish across the app.
- Animations and sampling: optimized animation sampling and audio-reactive processing for smoother visuals and better performance.

### Fixed
- Various stability and crash fixes across UI and background services.
- Edge-case fixes for preset rename, delete-all behavior (cleanup of custom images), animation switching, and permission-related flows (MediaProjection handling at boot and during app-profile switching).