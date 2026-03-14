package com.moonbench.bifrost.animations

import android.graphics.Color
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.moonbench.bifrost.tools.AudioAnalyzer
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import kotlin.math.roundToInt

class AudioReactiveAnimation(
    ledController: LedController,
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val baseColor: Int,
    private val baseRightColor: Int = baseColor,
    private val profile: PerformanceProfile
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.AUDIO_REACTIVE
    override val needsColorSelection: Boolean = true

    private var audioAnalyzer: AudioAnalyzer? = null

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 0
    private var response: Float = 0.5f
    private var sensitivity: Float = 0.5f
    private var smoothedIntensity: Float = 0f

    @Volatile
    private var isRunning = false

    private var updateThread: HandlerThread? = null
    private var updateHandler: Handler? = null

    private var hasAudioUpdate = false
    private var pendingIntensity: Float = 0f

    private val updateInterval: Long
        get() = if (profile.intervalMs == 0L) 16L else profile.intervalMs

    private val ledUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (hasAudioUpdate || currentBrightness > 0) {
                hasAudioUpdate = false

                val intensity = pendingIntensity.coerceIn(0f, 1f)
                val rising = intensity > smoothedIntensity
                val f = if (rising) riseLerpFactor() else fallLerpFactor()
                smoothedIntensity = lerpFloat(smoothedIntensity, intensity, f)
                val mapped = mapIntensity(smoothedIntensity)
                val target = (targetBrightness * mapped).roundToInt()
                currentBrightness = lerpBrightnessInt(currentBrightness, target, brightnessLerpFactor())

                applyLeds()
            }

            if (isRunning) {
                updateHandler?.postDelayed(this, adjustedAnimationDelay(updateInterval, targetBrightness))
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

    override fun start() {
        if (isRunning) return
        isRunning = true

        updateThread = HandlerThread("AudioReactiveUpdate").apply {
            start()
            priority = Thread.MAX_PRIORITY
        }
        updateHandler = Handler(updateThread!!.looper)
        updateHandler?.post(ledUpdateRunnable)

        audioAnalyzer = AudioAnalyzer(mediaProjection, profile) { intensity ->
            pendingIntensity = intensity
            hasAudioUpdate = true
        }
        audioAnalyzer?.start()
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        updateHandler?.removeCallbacks(ledUpdateRunnable)
        audioAnalyzer?.stop()
        audioAnalyzer = null

        updateThread?.quitSafely()
        updateThread = null
        updateHandler = null

        currentBrightness = 0
        applyLeds()
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

    private fun applyLeds() {
        val scale = (currentBrightness / 255f).let {
            if (it < 0.02f) 0f else it * it
        }
        val lr = (Color.red(baseColor) * scale).roundToInt().coerceIn(0, 255)
        val lg = (Color.green(baseColor) * scale).roundToInt().coerceIn(0, 255)
        val lb = (Color.blue(baseColor) * scale).roundToInt().coerceIn(0, 255)

        val rr = (Color.red(baseRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rg = (Color.green(baseRightColor) * scale).roundToInt().coerceIn(0, 255)
        val rb = (Color.blue(baseRightColor) * scale).roundToInt().coerceIn(0, 255)

        ledController.setLedColor(lr, lg, lb,
            leftTop = true, leftBottom = true,
            rightTop = false, rightBottom = false)
        ledController.setLedColor(rr, rg, rb,
            leftTop = false, leftBottom = false,
            rightTop = true, rightBottom = true)
    }
}