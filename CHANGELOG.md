# Changelog

All notable changes to this project will be documented in this file.

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