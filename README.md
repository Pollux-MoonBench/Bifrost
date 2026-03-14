# Bifrost -- LED Controller for the AYN Thor

Bifrost is a custom LED controller for the **AYN Thor** handheld (and might work for other handhelds).\
It provides a collection of LED animations that can run in the
background, including:

- **Ambilight**
- **Audio Reactive**
- **Ambi Aurora** (a mix of Ambilight + Audio Reactive)
- **Classic animations:** Breath, Rainbow, Pulse, and more

Bifrost aims to bring a vibrant, customizable lighting experience to the
AYN Thor while keeping performance and battery consumption in mind.

> ⚠️ **Important:** For animations to work, **Bifrost must stay alive in
> the background**.\
> Closing the app or restricting notification (and screen recording for Ambilight) activity will stop LED
> updates.

------------------------------------------------------------------------

## ✨ Features

### **Ambilight**

Uses Android's screen recording API to sample the screen's left and
right average colors.\
For performance, Bifrost captures the screen in **2×1 pixels** and reads
the RGB values directly from the buffer.


**New options:**
- **Custom color sampler** to eliminate pillarboxing/letterboxing and favor more vivid colors (useful for older content)
- **Single color mode** to calculate one shared color for both sticks
- **Saturation boost slider** for more intense colors
- **Hex color input** for precise color selection

When choosing the custom colors sampler, Bifrost captures the screen using a
**low-resolution 32-pixel**, which allows more advanced color
analysis while remaining lightweight, it:
- **Favors saturated colors** to improve vibrancy
- Helps eliminate pillarboxing and letterboxing

### **Audio Reactive**

Analyzes live audio levels (using the screen recording permission) to
drive LED intensity.

**Improvements:**
- Redesigned **reactivity system** using a single, unified reactivity slider
- Improved responsiveness and smoother audio-driven animations

### **Ambi Aurora**

Combines Ambilight color sampling with Audio Reactive intensity for a
hybrid effect.

**Enhancements:**
- Improved color calculation
- Supports **custom sampling**, **single color mode**, and **saturation boost**

### **Animation Presets**

- Save multiple animation presets with their own settings
- Automatically loads the **last selected preset** on app launch
- Easy organization and quick switching

### **Performance Profiles**

Bifrost offers multiple performance-level modes.\
The **Ragnarok profile** updates the Thor LED controller **as fast as
possible**, which may cause latency or even crashes.

------------------------------------------------------------------------

## 📦 Installation

Bifrost can be installed in two different ways:

### **Method 1 — Manual APK install**

1. Download the latest **APK** from the GitHub releases page.
2. Open your **Downloads** folder.
3. Tap the APK file to start the installation.
4. If Android asks to allow installation from **unknown sources**, accept the permission.
5. Complete the installation.

### **Method 2 — Install & update via Obtainium (recommended)**

If you use **Obtainium**, you can automatically receive updates:

1. Open the Obtainium app. It can be found here : https://github.com/ImranR98/Obtainium
2. Add a new app using this source: https://github.com/Pollux-MoonBench/Bifrost/releases/
3. Follow the Obtainium classic installation process

------------------------------------------------------------------------

## 🔒 Required Permissions

To enable Ambilight, Audio Reactive, and Ambi Aurora modes, Bifrost
requires:

- **Screen recording permission**\
  Used exclusively to sample colors (Ambilight) and volume intensity
  (Audio Reactive).

Bifrost does *not* save or transmit screen contents — sampling happens
locally and is reduced to minimal pixel data for efficiency.

------------------------------------------------------------------------

## 🎮 Other Tested Devices

Bifrost has been tested and confirmed to work on the following devices:

- Retroid Pocket Mini V2
- Retroid Pocket 5
- Odin 2 Portal Pro

------------------------------------------------------------------------

## ⚠️ In Dev Status

Bifrost is now **out of beta**, but still actively evolving.  
While overall stability has improved, unexpected behavior may still
occur on some devices.

### **Known issues**

- Random crashes under certain conditions
- Granting notification permission at launch may cause the LED toggle
  switch to appear disabled even though animations continue running
- On the Retroid Pocket Mini, only the left stick turns on in Ambilight mode. This issue can be solved using the custom color sampler mode. Thank's to r/hupo224 for helping me work on this issue.

If you encounter issues, please open a GitHub issue or check the
existing ones.

## 📜 License

This project is licensed under **GPLv3**.

You are free to use, study, modify, and redistribute the app under the
terms of the GPLv3 license.\
This app is provided for free in this repository and **cannot be sold to
you.**
