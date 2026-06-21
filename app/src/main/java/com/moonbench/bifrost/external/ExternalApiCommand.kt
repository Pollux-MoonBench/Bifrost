package com.moonbench.bifrost.external

import com.moonbench.bifrost.animations.LedAnimationType

sealed class Terminator {
    data class Duration(val millis: Long) : Terminator()
    object UntilNextCommand : Terminator()
    object UntilExplicitClear : Terminator()
}

sealed class ExternalApiCommand {
    abstract val callerPackage: String
    abstract val requestId: String?

    data class Display(
        override val callerPackage: String,
        override val requestId: String?,
        val effect: LedAnimationType,
        val color: Int,
        val colorRight: Int,
        val intensity: Int,
        val speed: Float,
        val smoothness: Float,
        val sensitivity: Float,
        val terminator: Terminator,
        val priority: Int,
        val phaseSeconds: Float = 0f,
        val flickering: Boolean = false,
        val burstWallMs: Long = 0L
    ) : ExternalApiCommand()

    data class Clear(
        override val callerPackage: String,
        override val requestId: String?
    ) : ExternalApiCommand()

    data class InstallProfile(
        override val callerPackage: String,
        override val requestId: String?,
        val profileName: String,
        val effect: LedAnimationType,
        val color: Int,
        val colorRight: Int,
        val intensity: Int,
        val speed: Float,
        val smoothness: Float,
        val sensitivity: Float,
        val saturationBoost: Float,
        val useCustomSampling: Boolean,
        val useSingleColor: Boolean,
        val breatheWhenCharging: Boolean,
        val indicateChargingSpeed: Boolean,
        val flashWhenReady: Boolean,
        val batteryLowColor: Int?,
        val batteryMidColor: Int?,
        val batteryHighColor: Int?,
        val cpuCoolColor: Int?,
        val cpuWarmColor: Int?,
        val cpuHotColor: Int?,
        val replaceIfExists: Boolean
    ) : ExternalApiCommand()

    data class UninstallProfile(
        override val callerPackage: String,
        override val requestId: String?,
        val profileName: String
    ) : ExternalApiCommand()
}
