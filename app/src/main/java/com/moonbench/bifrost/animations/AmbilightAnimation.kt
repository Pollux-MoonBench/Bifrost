package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.tools.ScreenAnalyzer
import kotlin.math.roundToInt

class AmbilightAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection? = null,
    private val displayId: Int,
    private val displayMetrics: DisplayMetrics,
    private val profile: PerformanceProfile,
    private val useCustomSampling: Boolean,
    private val useSingleColor: Boolean,
    initialSaturationBoost: Float = 0.0f
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AMBILIGHT
    override val needsColorSelection: Boolean = false

    private var screenAnalyzer: ScreenAnalyzer? = null
    private var updateThread: HandlerThread? = null
    private var updateHandler: Handler? = null

    @Volatile private var isRunning = false
    @Volatile private var targetLeftColor = Color.BLACK
    @Volatile private var targetRightColor = Color.BLACK

    private var currentLeftColor = Color.BLACK
    private var currentRightColor = Color.BLACK
    private var lastAppliedLeft = Color.TRANSPARENT
    private var lastAppliedRight = Color.TRANSPARENT

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255
    private var response: Float = 0.5f
    private var saturationBoost: Float = initialSaturationBoost

    private val ledUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            currentLeftColor = lerpColor(currentLeftColor, targetLeftColor, colorLerpFactor())
            currentRightColor = lerpColor(currentRightColor, targetRightColor, colorLerpFactor())
            currentBrightness = lerpBrightnessInt(currentBrightness, targetBrightness, brightnessLerpFactor())

            applyLeds()

            updateHandler?.postDelayed(this, 16L)
        }
    }

    override fun setTargetBrightness(brightness: Int) { targetBrightness = brightness.coerceIn(0, 255) }
    override fun setLerpStrength(strength: Float) { response = strength.coerceIn(0f, 1f) }
    override fun setSpeed(speed: Float) { response = speed.coerceIn(0f, 1f) }

    override fun setSaturationBoost(boost: Float) {
        saturationBoost = boost.coerceIn(0f, 1f)
        screenAnalyzer?.saturationBoost = saturationBoost
    }

    override fun start() {
        isRunning = true

        updateThread = HandlerThread("AmbilightUpdate").apply { start() }
        updateHandler = Handler(updateThread!!.looper)
        updateHandler?.post(ledUpdateRunnable)

        screenAnalyzer = ScreenAnalyzer(
            displayId, displayMetrics, profile, useCustomSampling, useSingleColor, saturationBoost,
            mediaProjection = mediaProjection
        ) { colors ->
            targetLeftColor = if (isColorBlack(colors.leftColor)) Color.BLACK else colors.leftColor
            targetRightColor = if (isColorBlack(colors.rightColor)) Color.BLACK else colors.rightColor
        }
        screenAnalyzer?.start()
    }

    override fun stop() {
        isRunning = false
        updateHandler?.removeCallbacks(ledUpdateRunnable)
        screenAnalyzer?.stop()
        screenAnalyzer = null
        updateThread?.quitSafely()
        updateThread = null
        updateHandler = null
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

    private fun applyLeds() {
        val scale = applyGamma(currentBrightness) / 255f

        val lr = (Color.red(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val lg = (Color.green(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val lb = (Color.blue(currentLeftColor) * scale).roundToInt().coerceIn(0, 255)
        val rr = (Color.red(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rg = (Color.green(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rb = (Color.blue(currentRightColor) * scale).roundToInt().coerceIn(0, 255)

        val newLeft = Color.rgb(lr, lg, lb)
        val newRight = Color.rgb(rr, rg, rb)
        if (newLeft == lastAppliedLeft && newRight == lastAppliedRight) return
        lastAppliedLeft = newLeft
        lastAppliedRight = newRight

        ledController.setLedColor(lr, lg, lb, leftTop = true, leftBottom = true, rightTop = false, rightBottom = false)
        ledController.setLedColor(rr, rg, rb, leftTop = false, leftBottom = false, rightTop = true, rightBottom = true)
    }
}