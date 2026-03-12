package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class StrobeAnimation(
    ledController: LedController,
    initialColor: Int,
    initialRightColor: Int = initialColor
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.STROBE
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor
    private var targetRightColor: Int = initialRightColor
    private var currentRightColor: Int = initialRightColor
    private var targetBrightness: Int = 255
    private var speed: Float = 0.5f
    private var isOn = false

    override fun setTargetColor(color: Int) {
        targetColor = color
    }

    override fun setTargetRightColor(color: Int) {
        targetRightColor = color
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0f, 1f)
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            val colorFactor = 0.05f + 0.45f * speed
            currentColor = lerpColor(currentColor, targetColor, colorFactor)
            currentRightColor = lerpColor(currentRightColor, targetRightColor, colorFactor)

            val globalScale = targetBrightness / 255f

            isOn = !isOn
            val factor = if (isOn) globalScale else 0f

            val lr = (Color.red(currentColor) * factor).roundToInt().coerceIn(0, 255)
            val lg = (Color.green(currentColor) * factor).roundToInt().coerceIn(0, 255)
            val lb = (Color.blue(currentColor) * factor).roundToInt().coerceIn(0, 255)

            val rr = (Color.red(currentRightColor) * factor).roundToInt().coerceIn(0, 255)
            val rg = (Color.green(currentRightColor) * factor).roundToInt().coerceIn(0, 255)
            val rb = (Color.blue(currentRightColor) * factor).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(lr, lg, lb,
                leftTop = true, leftBottom = true,
                rightTop = false, rightBottom = false)
            ledController.setLedColor(rr, rg, rb,
                leftTop = false, leftBottom = false,
                rightTop = true, rightBottom = true)

            val delay = (100 - 70 * speed).toLong()
            handler.postDelayed(this, delay)
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