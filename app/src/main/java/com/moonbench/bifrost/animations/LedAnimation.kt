package com.moonbench.bifrost.animations

import android.graphics.Color
import com.moonbench.bifrost.tools.DeviceInfo
import com.moonbench.bifrost.tools.LedController
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

abstract class LedAnimation(protected val ledController: LedController) {

    companion object {
        private const val THOR_LOW_BRIGHTNESS_THRESHOLD = 51
        private const val THOR_LOW_BRIGHTNESS_SPEED_MULTIPLIER = 1.35f
    }

    abstract val type: LedAnimationType
    abstract val needsColorSelection: Boolean

    abstract fun start()
    abstract fun stop()

    open fun setTargetColor(color: Int) {}
    open fun setTargetRightColor(color: Int) {}
    open fun setTargetBrightness(brightness: Int) {}
    open fun setLerpStrength(strength: Float) {}
    open fun setSpeed(speed: Float) {}
    open fun setSensitivity(sensitivity: Float) {}
    open fun setSaturationBoost(boost: Float) {}
    open fun setBreatheWhenCharging(enabled: Boolean) {}
    open fun setIndicateChargingSpeed(enabled: Boolean) {}
    open fun setFlashWhenReady(enabled: Boolean) {}

    protected fun applyGamma(value: Int): Int {
        val normalized = value / 255f
        val corrected = normalized.pow(2.0f)
        return (corrected * 255f).roundToInt().coerceIn(0, 255)
    }

    protected fun lerpInt(from: Int, to: Int, factor: Float): Int {
        if (from == to) return from.coerceIn(0, 255)
        val f = factor.coerceIn(0f, 1f)
        val raw = from + (to - from) * f
        val result = if (abs(to - raw) < 1f) to else raw.roundToInt()
        return result.coerceIn(0, 255)
    }

    protected fun lerpBrightnessInt(from: Int, to: Int, factor: Float): Int {
        val isThorLowBrightnessRange =
            DeviceInfo.isAynThor &&
                (from <= THOR_LOW_BRIGHTNESS_THRESHOLD || to <= THOR_LOW_BRIGHTNESS_THRESHOLD)

        val adjustedFactor = if (isThorLowBrightnessRange) {
            (factor * THOR_LOW_BRIGHTNESS_SPEED_MULTIPLIER).coerceAtMost(0.92f)
        } else {
            factor
        }

        val interpolated = lerpInt(from, to, adjustedFactor)
        if (!isThorLowBrightnessRange || from == to) return interpolated

        if (interpolated == from) {
            val distance = abs(to - from)
            val minStep = when {
                distance >= 18 -> 3
                distance >= 8 -> 2
                else -> 1
            }
            return if (to > from) {
                (from + minStep).coerceAtMost(to).coerceIn(0, 255)
            } else {
                (from - minStep).coerceAtLeast(to).coerceIn(0, 255)
            }
        }

        return interpolated
    }

    protected fun adjustedAnimationDelay(baseDelayMs: Long, brightness: Int): Long {
        return baseDelayMs
    }

    protected fun lerpFloat(from: Float, to: Float, factor: Float): Float {
        val f = factor.coerceIn(0f, 1f)
        return from + (to - from) * f
    }

    protected fun lerpColor(from: Int, to: Int, factor: Float): Int {
        val r = lerpInt(Color.red(from), Color.red(to), factor)
        val g = lerpInt(Color.green(from), Color.green(to), factor)
        val b = lerpInt(Color.blue(from), Color.blue(to), factor)
        return Color.rgb(r, g, b)
    }

    protected fun isColorBlack(color: Int): Boolean {
        return Color.red(color) == 0 && Color.green(color) == 0 && Color.blue(color) == 0
    }

    protected fun boostSaturation(color: Int, boost: Float): Int {
        if (boost <= 0f) return color

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val avg = (r + g + b) / 3f
        val boostFactor = 1f + (boost * 2f)

        val newR = ((avg + (r - avg) * boostFactor).coerceIn(0f, 255f)).toInt()
        val newG = ((avg + (g - avg) * boostFactor).coerceIn(0f, 255f)).toInt()
        val newB = ((avg + (b - avg) * boostFactor).coerceIn(0f, 255f)).toInt()

        return Color.rgb(newR, newG, newB)
    }

}