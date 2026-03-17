# Bifrost -- LED Controller for the AYN Thor

Bifrost is a custom LED controller for the **AYN Thor** handheld (and might work for other handhelds).  
It provides a collection of LED animations that can run in the background, including:

- **Ambilight**
- **Audio Reactive**
- **Ambi Aurora** (a mix of Ambilight + Audio Reactive)
- **Classic animations:** Breath, Rainbow, Pulse, and more

Bifrost aims to bring a vibrant, customizable lighting experience to the  
AYN Thor while keeping performance and battery consumption in mind.

> ⚠️ **Important:** For animations to work, **Bifrost must stay alive in the background**.  
> Closing the app or restricting notification (and screen recording for Ambilight) activity will stop LED updates.

---

# ✨ Features

## Ambilight

Uses Android's screen recording API to sample the screen's left and right average colors.  
For performance, Bifrost captures the screen in **2×1 pixels** and reads the RGB values directly from the buffer.

### Options

- **Custom color sampler** to eliminate pillarboxing/letterboxing and favor more vivid colors (useful for older content)
- **Single color mode** to calculate one shared color for both sticks
- **Saturation boost slider** for more intense colors
- **Hex color input** for precise color selection

When choosing the custom color sampler, Bifrost captures the screen using a **low-resolution 32-pixel grid**, allowing more advanced color analysis while remaining lightweight:

- **Favors saturated colors** to improve vibrancy
- Helps eliminate pillarboxing and letterboxing

---

## Audio Reactive

Analyzes live audio levels (using the screen recording permission) to drive LED intensity.

### Improvements

- Redesigned **reactivity system** using a single unified reactivity slider
- Improved responsiveness and smoother audio-driven animations

---

## Ambi Aurora

Combines Ambilight color sampling with Audio Reactive intensity for a hybrid effect.

### Enhancements

- Improved color calculation
- Supports **custom sampling**, **single color mode**, and **saturation boost**

---

## Animation Presets

- Save multiple animation presets with their own settings
- Automatically loads the **last selected preset** on app launch
- Easy organization and quick switching

---

## Performance Profiles

Bifrost offers multiple performance-level modes.

The **Ragnarok profile** updates the Thor LED controller **as fast as possible**, which may cause latency or even crashes.

---

# 🚀 New Features

Recent updates introduce several new capabilities.

## Auto Start

- **Auto-start on boot** so Bifrost resumes automatically after device reboot.
- If auto-start is skipped because MediaProjection permission is required, Bifrost shows a notification to reopen the app and request the permission.

## App-based Profiles

- Profiles can be **assigned to specific apps**
- Bifrost automatically switches profiles depending on the **foreground application**
- Optional **fallback preset** when no mapped app is in the foreground
- First-use popup to explain app mode behavior
- Immediate app-profile resolution when app mode is enabled

## Independent LED Control

- **Separate left/right LED control**
- Each stick can run **different colors or animations**

## Charging Indicator

Improved LED feedback while charging:

- Breathing lights while charging
- Charging speed indication
- Flash notification when charging completes

## CPU Temperature Animation

A new animation that changes LED colors based on **CPU temperature readings**.

## Preset Export / Import

- Export presets as a **versioned JSON bundle**
- Import presets in **Replace** or **Add** mode
- Preset metadata, artwork references, and app-profile flags are preserved

## Preset Artwork and Management

- Preset artwork editor supports:
  - Built-in icons
  - Custom emoji
  - Uploaded custom images
  - Assigned app icons
- Rename presets directly from the update flow
- Long-press delete to remove **all presets** with confirmation
- Horizontal preset presentation with smoother cover-flow and snap behavior

## Animation Color Customization

- Per-preset custom colors for **Battery Indicator**: low / mid / high
- Per-preset custom colors for **CPU Temperature**: cool / warm / hot
- Long-press color swatches to reset to defaults

## Service and UX Controls

- Toggle for persistent foreground notification
- Improved switch visuals and general UX polish
- Better app-mode fallback handling: if app mode is on and no fallback preset is selected, Bifrost keeps the service active but does not apply an animation until a mapped/fallback preset can be resolved

---

# 📦 Installation

Bifrost can be installed in two different ways:

## Method 1 — Manual APK install

1. Download the latest **APK** from the GitHub releases page.
2. Open your **Downloads** folder.
3. Tap the APK file to start the installation.
4. If Android asks to allow installation from **unknown sources**, accept the permission.
5. Complete the installation.

---

## Method 2 — Install & update via Obtainium (recommended)

If you use **Obtainium**, you can automatically receive updates:

1. Open the Obtainium app  
   https://github.com/ImranR98/Obtainium

2. Add a new app using this source:
   https://github.com/Pollux-MoonBench/Bifrost/releases/

3. Follow the Obtainium installation process.

---

# 🔒 Required Permissions

To enable Ambilight, Audio Reactive, and Ambi Aurora modes, Bifrost requires:

### Screen recording permission

Used exclusively to sample:

- Screen colors (Ambilight)
- Audio intensity (Audio Reactive)

Bifrost does **not save or transmit screen contents** — sampling happens locally and is reduced to minimal pixel data for efficiency.

---

# 🎮 Other Tested Devices

Bifrost has been tested and confirmed to work on the following devices:

### AYN
- Thor
- Odin 2 Portal Pro

### Retroid
- Pocket Mini V2
- Pocket 5

---

# ⚠️ In Dev Status

Bifrost is now **out of beta**, but still actively evolving.  
While overall stability has improved, unexpected behavior may still occur on some devices.

### Known issues

- Random crashes under certain conditions
- Granting notification permission at launch may cause the LED toggle switch to appear disabled even though animations continue running
- On the Retroid Pocket Mini, only the left stick turns on in Ambilight mode.  
  This issue can be solved using the **custom color sampler mode**.

Thanks to **r/hupo224** for helping investigate this issue.

---

# ❤️ Contributors

Huge thanks to **KuriGohan-Kamehameha** for the **massive work and new features added to the project**, including major functionality improvements and system integrations.

Project:  
https://github.com/KuriGohan-Kamehameha

---

# ☕ Support the Project

If you enjoy Bifrost and want to support development, you can **buy me a coffee** here:

👉 https://ko-fi.com/pollux_moonbench

Thank you! ❤️

---

# 📜 License

This project is licensed under **GPLv3**.

You are free to use, study, modify, and redistribute the app under the terms of the GPLv3 license.

This app is provided for free in this repository and **cannot be sold to you.**
