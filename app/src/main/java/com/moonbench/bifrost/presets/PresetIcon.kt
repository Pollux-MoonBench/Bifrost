package com.moonbench.bifrost

import androidx.annotation.DrawableRes
import com.moonbench.bifrost.animations.LedAnimationType

enum class PresetIcon(
    val label: String,
    @DrawableRes val drawableRes: Int? = null,
    val emoji: String? = null
) {
    LIGHT("Light", drawableRes = R.drawable.baseline_lightbulb_circle_24),
    STAR("Star", drawableRes = android.R.drawable.btn_star_big_on),
    PLAY("Media", drawableRes = android.R.drawable.ic_media_play),
    CAMERA("Camera", drawableRes = android.R.drawable.ic_menu_camera),
    COMPASS("Compass", drawableRes = android.R.drawable.ic_menu_compass),
    DISPLAY("Display", drawableRes = android.R.drawable.ic_menu_gallery),
    SYSTEM("System", drawableRes = android.R.drawable.ic_menu_manage),
    TIMER("Timer", drawableRes = android.R.drawable.ic_lock_idle_alarm),
    FIRE("Fire emoji", emoji = "\uD83D\uDD25"),
    SPARKLES("Sparkles emoji", emoji = "\u2728"),
    WAVE("Wave emoji", emoji = "\uD83C\uDF0A"),
    BOLT("Lightning emoji", emoji = "\u26A1"),
    MUSIC("Music emoji", emoji = "\uD83C\uDFB5"),
    MOON("Moon emoji", emoji = "\uD83C\uDF19");

    companion object {
        fun fromStoredName(value: String?): PresetIcon {
            return values().firstOrNull { it.name == value } ?: LIGHT
        }

        fun defaultFor(animationType: LedAnimationType): PresetIcon {
            return when (animationType) {
                LedAnimationType.STATIC,
                LedAnimationType.BREATH,
                LedAnimationType.PULSE,
                LedAnimationType.STROBE,
                LedAnimationType.CHASE,
                LedAnimationType.SPARKLE,
                LedAnimationType.RAINBOW,
                LedAnimationType.RAVE,
                LedAnimationType.FADE_TRANSITION -> LIGHT

                LedAnimationType.AUDIO_REACTIVE -> PLAY
                LedAnimationType.AMBILIGHT,
                LedAnimationType.AMBIAURORA -> DISPLAY

                LedAnimationType.BATTERY_INDICATOR,
                LedAnimationType.CPU_TEMPERATURE -> TIMER
            }
        }
    }
}