package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class StaticAnimation(
    ledController: LedController,
    initialColor: Int
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.STATIC
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255

    private var lerpStrength: Float = 0.5f

    override fun setTargetColor(color: Int) {
        targetColor = color
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setLerpStrength(strength: Float) {
        lerpStrength = strength.coerceIn(0f, 1f)
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            val factor = 0.15f + 0.7f * lerpStrength
            currentColor = lerpColor(currentColor, targetColor, factor)
            currentBrightness = lerpInt(currentBrightness, targetBrightness, factor)

            val scale = currentBrightness / 255f

            val r = (Color.red(currentColor) * scale).roundToInt().coerceIn(0, 255)
            val g = (Color.green(currentColor) * scale).roundToInt().coerceIn(0, 255)
            val b = (Color.blue(currentColor) * scale).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(
                r,
                g,
                b,
                leftTop = true,
                leftBottom = true,
                rightTop = true,
                rightBottom = true
            )

            handler.postDelayed(this, 30L)
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(runnable)
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        ledController.setLedColor(
            0,
            0,
            0,
            leftTop = true,
            leftBottom = true,
            rightTop = true,
            rightBottom = true
        )
    }
}
