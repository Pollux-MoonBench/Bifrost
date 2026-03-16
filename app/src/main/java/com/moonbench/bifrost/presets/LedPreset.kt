package com.moonbench.bifrost

import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.PerformanceProfile

data class LedPreset(
    val name: String,
    val animationType: LedAnimationType,
    val performanceProfile: PerformanceProfile,
    val color: Int,
    val rightColor: Int = color,
    val brightness: Int,
    val speed: Float,
    val smoothness: Float,
    val sensitivity: Float = 0.5f,
    val saturationBoost: Float = 0.0f,
    val useCustomSampling: Boolean = false,
    val useSingleColor: Boolean = false,
    val breatheWhenCharging: Boolean = false,
    val indicateChargingSpeed: Boolean = false,
    val flashWhenReady: Boolean = false,
    val ragnarokAccepted: Boolean = false,
    val icon: PresetIcon = PresetIcon.LIGHT,
    val customEmoji: String? = null,
    val customImageFileName: String? = null
)