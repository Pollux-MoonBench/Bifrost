package com.moonbench.bifrost.external

import com.moonbench.bifrost.animations.LedAnimationType

data class ExternalOverrideState(
    val callerPackage: String,
    val effect: LedAnimationType,
    val color: Int,
    val colorRight: Int,
    val intensity: Int,
    val speed: Float,
    val smoothness: Float,
    val sensitivity: Float,
    val terminator: Terminator,
    val priority: Int,
    val startedAtMs: Long,
)
