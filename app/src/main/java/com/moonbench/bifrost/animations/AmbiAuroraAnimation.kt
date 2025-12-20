package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.AudioAnalyzer
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.tools.ScreenAnalyzer
import com.moonbench.bifrost.tools.ScreenColors
import kotlin.math.roundToInt

class AmbiAuroraAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val profile: PerformanceProfile,
    private val useCustomSampling: Boolean
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AMBIAURORA
    override val needsColorSelection: Boolean = false

    private var screenAnalyzer: ScreenAnalyzer? = null
    private var audioAnalyzer: AudioAnalyzer? = null

    private var currentLeftColor = Color.BLACK
    private var currentRightColor = Color.BLACK

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 0
    private var response: Float = 0.5f
    private var sensitivity: Float = 0.5f
    private var saturationBoost: Float = 0.0f
    private var smoothedIntensity: Float = 0f

    @Volatile
    private var isRunning = false

    private var updateThread: HandlerThread? = null
    private var updateHandler: Handler? = null

    private var hasColorUpdate = false
    private var hasAudioUpdate = false
    private var pendingColors: ScreenColors? = null
    private var pendingIntensity: Float = 0f

    private val updateInterval: Long
        get() = if (profile.intervalMs == 0L) 16L else profile.intervalMs

    private val combinedUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            var needsLedUpdate = false

            if (hasColorUpdate) {
                hasColorUpdate = false
                pendingColors?.let { colors ->
                    updateColors(colors)
                    needsLedUpdate = true
                }
            }

            if (hasAudioUpdate) {
                hasAudioUpdate = false
                val intensity = pendingIntensity.coerceIn(0f, 1f)
                val rising = intensity > smoothedIntensity
                val f = if (rising) riseLerpFactor() else fallLerpFactor()
                smoothedIntensity = lerpFloat(smoothedIntensity, intensity, f)
                val mapped = mapIntensity(smoothedIntensity)
                val target = (targetBrightness * mapped).roundToInt()
                currentBrightness = lerpInt(currentBrightness, target, brightnessLerpFactor())
                needsLedUpdate = true
            }

            if (needsLedUpdate) {
                applyLeds()
            }

            if (isRunning) {
                updateHandler?.postDelayed(this, updateInterval)
            }
        }
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        response = strength.coerceIn(0f, 1f)
    }

    override fun setSpeed(speed: Float) {
        response = speed.coerceIn(0f, 1f)
    }

    override fun setSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity.coerceIn(0f, 1f)
    }

    override fun setSaturationBoost(boost: Float) {
        saturationBoost = boost.coerceIn(0f, 1f)
    }

    override fun start() {
        if (isRunning) return
        isRunning = true

        updateThread = HandlerThread("AmbiAuroraUpdate").apply {
            start()
            priority = Thread.MAX_PRIORITY
        }
        updateHandler = Handler(updateThread!!.looper)

        updateHandler?.post(combinedUpdateRunnable)

        screenAnalyzer = ScreenAnalyzer(
            mediaProjection,
            displayMetrics,
            profile,
            useCustomSampling
        ) { colors ->
            pendingColors = colors
            hasColorUpdate = true
        }
        screenAnalyzer?.start()

        audioAnalyzer = AudioAnalyzer(mediaProjection, profile) { intensity ->
            pendingIntensity = intensity
            hasAudioUpdate = true
        }
        audioAnalyzer?.start()
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        updateHandler?.removeCallbacks(combinedUpdateRunnable)
        screenAnalyzer?.stop()
        screenAnalyzer = null
        audioAnalyzer?.stop()
        audioAnalyzer = null

        updateThread?.quitSafely()
        updateThread = null
        updateHandler = null

        currentBrightness = 0
        applyLeds()
    }

    private fun colorLerpFactor(): Float {
        val min = 0.1f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun riseLerpFactor(): Float {
        val min = 0.2f
        val max = 0.9f
        return min + (max - min) * response
    }

    private fun fallLerpFactor(): Float {
        val min = 0.07f
        val max = 0.7f
        return min + (max - min) * response
    }

    private fun brightnessLerpFactor(): Float {
        val min = 0.25f
        val max = 1f
        return min + (max - min) * response
    }

    private fun mapIntensity(raw: Float): Float {
        val noiseFloor = 0.05f + (0.25f * (1f - sensitivity))
        if (raw <= noiseFloor) return 0f
        val norm = ((raw - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
        val amp = 0.5f + 1.5f * sensitivity
        val boosted = norm * amp
        return boosted.coerceIn(0f, 1f)
    }

    private fun updateColors(colors: ScreenColors) {
        val leftTarget = if (isColorBlack(colors.leftColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.leftColor, saturationBoost)
        }

        val rightTarget = if (isColorBlack(colors.rightColor)) {
            Color.BLACK
        } else {
            boostSaturation(colors.rightColor, saturationBoost)
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
    }

    private fun applyLeds() {
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