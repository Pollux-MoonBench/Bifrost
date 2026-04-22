package com.moonbench.bifrost.animations

enum class LedAnimationType(
    val needsMediaProjection: Boolean,
    val needsColorSelection: Boolean,
    val supportsSpeed: Boolean,
    val supportsSmoothness: Boolean,
    val supportsAudioSensitivity: Boolean = false
) {
    AMBILIGHT(true, false, true, true, false),
    AUDIO_REACTIVE(true, true, true, true, true),
    AMBIAURORA(true, false, true, true, true),
    BATTERY_INDICATOR(false, false, false, false, false),
    CPU_TEMPERATURE(false, false, false, false, false),
    STATIC(false, true, false, false, false),
    BREATH(false, true, true, false, false),
    RAINBOW(false, false, true, false, false),
    PULSE(false, true, true, false, false),
    STROBE(false, true, true, false, false),
    SPARKLE(false, true, true, false, false),
    FADE_TRANSITION(false, true, true, false, false),
    RAVE(false, false, true, false, false),
    CHASE(false, true, true, false, false),
}