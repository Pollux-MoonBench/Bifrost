package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.tools.ScreenAnalyzer
import com.moonbench.bifrost.tools.ScreenColors
import kotlin.math.roundToInt

class AmbilightAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val profile: PerformanceProfile,
    private val useCustomSampling: Boolean,
    private val useSingleColor: Boolean,
    initialSaturationBoost: Float = 0.0f
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AMBILIGHT
    override val needsColorSelection: Boolean = false

    private var screenAnalyzer: ScreenAnalyzer? = null

    private var currentLeftColor = Color.BLACK
    private var currentRightColor = Color.BLACK

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255
    private var response: Float = 0.5f
    private var saturationBoost: Float = initialSaturationBoost

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        response = strength.coerceIn(0f, 1f)
    }

    override fun setSpeed(speed: Float) {
        response = speed.coerceIn(0f, 1f)
    }

    override fun setSaturationBoost(boost: Float) {
        saturationBoost = boost.coerceIn(0f, 1f)
        screenAnalyzer?.saturationBoost = saturationBoost
    }

    override fun start() {
        screenAnalyzer = ScreenAnalyzer(
            mediaProjection,
            displayMetrics,
            profile,
            useCustomSampling,
            useSingleColor,
            saturationBoost
        ) { colors ->
            updateColors(colors)
        }
        screenAnalyzer?.start()
    }

    override fun stop() {
        screenAnalyzer?.stop()
        screenAnalyzer = null
    }

    private fun colorLerpFactor(): Float {
        val min = 0.1f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun brightnessLerpFactor(): Float {
        val min = 0.1f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun updateColors(colors: ScreenColors) {
        val leftTarget = if (isColorBlack(colors.leftColor)) {
            Color.BLACK
        } else {
            colors.leftColor
        }

        val rightTarget = if (isColorBlack(colors.rightColor)) {
            Color.BLACK
        } else {
            colors.rightColor
        }

        currentLeftColor = if (isColorBlack(leftTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentLeftColor, leftTarget, colorLerpFactor())
        }

        currentRightColor = if (isColorBlack(rightTarget)) {
            Color.BLACK
        } else {
            lerpColor(currentRightColor, rightTarget, colorLerpFactor())
        }

        currentBrightness = lerpInt(currentBrightness, targetBrightness, brightnessLerpFactor())

        val scale = currentBrightness / 255f

        val leftRed = (Color.red(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val leftGreen = (Color.green(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val leftBlue = (Color.blue(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)

        val rightRed = (Color.red(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rightGreen = (Color.green(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rightBlue = (Color.blue(currentRightColor) * scale).roundToInt().coerceIn(0, 255)

        ledController.setLedColor(
            leftRed,
            leftGreen,
            leftBlue,
            leftTop = true,
            leftBottom = true,
            rightTop = false,
            rightBottom = false
        )

        ledController.setLedColor(
            rightRed,
            rightGreen,
            rightBlue,
            leftTop = false,
            leftBottom = false,
            rightTop = true,
            rightBottom = true
        )
    }
}