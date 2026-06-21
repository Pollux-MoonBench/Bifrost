package com.moonbench.bifrost.tools

/**
 * Pure timing math for the dip-to-black crossfade used when an LED override
 * reverts to the underlying animation.
 *
 *   phase 1 (0 .. outMs)            scale ramps 1 → 0   (outgoing dims out)
 *   midpoint (== outMs)            scale 0, animation swap happens here
 *   phase 2 (outMs .. outMs+inMs)  scale ramps 0 → 1   (incoming fades up)
 *   after                          scale 1             (fade complete)
 *
 * Extracted from LEDService so the curve is unit-testable without the Android
 * handler / LED binder.
 */
object Crossfade {

    /** Master brightness scale (0f..1f) at [elapsedMs] into the fade. */
    fun scaleAt(elapsedMs: Long, outMs: Long, inMs: Long): Float = when {
        elapsedMs < 0L -> 1f
        elapsedMs < outMs -> (1f - elapsedMs.toFloat() / outMs).coerceIn(0f, 1f)
        elapsedMs < outMs + inMs -> ((elapsedMs - outMs).toFloat() / inMs).coerceIn(0f, 1f)
        else -> 1f
    }

    /** True once the fade is finished and full scale has been restored. */
    fun isComplete(elapsedMs: Long, outMs: Long, inMs: Long): Boolean =
        elapsedMs >= outMs + inMs
}
