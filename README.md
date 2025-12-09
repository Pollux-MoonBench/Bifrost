# Bifrost -- LED Controller for the AYN Thor

Bifrost is a custom LED controller for the **AYN Thor** handheld (and might work for other handhelds).\
It provides a collection of LED animations that can run in the
background, including:

-   **Ambilight**
-   **Audio Reactive**
-   **Ambi Aurora** (a mix of Ambilight + Audio Reactive)
-   **Classic animations:** Breath, Rainbow, Pulse, and more

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

### **Audio Reactive**

Analyzes live audio levels (using the screen recording permission) to
drive LED intensity.

### **Ambi Aurora**

Combines Ambilight color sampling with Audio Reactive intensity for a
hybrid effect.

### **Animation Presets**

-   Save multiple animation presets with their own settings
-   Automatically loads the **last selected preset** on app launch
-   Easy organization and quick switching

### **Performance Profiles**

Bifrost offers multiple performance-level modes.\
The **Ragnarok profile** updates the Thor LED controller **as fast as
possible**, which may cause latency or even crashes.

------------------------------------------------------------------------

## 🔒 Required Permissions

To enable Ambilight, Audio Reactive, and Ambi Aurora modes, Bifrost
requires:

-   **Screen recording permission**\
    Used exclusively to sample colors (Ambilight) and volume intensity
    (Audio Reactive).

Bifrost does *not* save or transmit screen contents---sampling happens
locally and is reduced to minimal pixel data for efficiency.

------------------------------------------------------------------------

## 🎮 Other Tested Devices
Bifrost has been tested and confirmed to work on the following devices:

-    Retroid Pocket Mini V2 (known issues)
-    Retroid Pocket 5

------------------------------------------------------------------------

## ⚠️ Beta Status

Bifrost is currently in **beta**, and unexpected behavior may still
occur.

### **Known issues**

-   Random crashes under certain conditions
-   Granting notification permission at launch may cause the LED toggle
    switch to appear disabled even though animations continue running
-   Ambilight and AmbiAurora can ealily averate colors to black when using old aspect ratio like 4:3
-   Ambilight and AmbiAurora can ealily average colors to white or desaturated color
-   On the Retroid Pocket Mini, only the left stick turns on in Ambilight mode

If you encounter issues, please open a GitHub issue or check the
existing ones.

------------------------------------------------------------------------

## ☕ Support the Project

If you enjoy Bifrost and want to support development, you can **buy me a
coffee** here:

👉 https://ko-fi.com/pollux_moonbench

Thank you! ❤️

------------------------------------------------------------------------

## 📜 License

This project is licensed under **GPLv3**.

You are free to use, study, modify, and redistribute the app under the
terms of the GPLv3 license.\
This app is provided for free in this repository and **cannot be sold to
you.**
