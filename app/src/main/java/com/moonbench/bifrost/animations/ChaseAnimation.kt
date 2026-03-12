package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class ChaseAnimation(
    ledController: LedController,
    initialColor: Int,
    initialRightColor: Int = initialColor
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.CHASE
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor
    private var targetRightColor: Int = initialRightColor
    private var currentRightColor: Int = initialRightColor
    private var targetBrightness: Int = 255
    private var speed: Float = 0.5f
    private var currentLed = 0
    private val trailLength = 2
    private val ledTrail = mutableListOf<Int>()

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

            ledTrail.add(0, currentLed)
            if (ledTrail.size > trailLength) {
                ledTrail.removeAt(ledTrail.size - 1)
            }

            for (i in 0 until 4) {
                val isRight = i >= 2
                val baseR = if (isRight) Color.red(currentRightColor) else Color.red(currentColor)
                val baseG = if (isRight) Color.green(currentRightColor) else Color.green(currentColor)
                val baseB = if (isRight) Color.blue(currentRightColor) else Color.blue(currentColor)

                val trailIndex = ledTrail.indexOf(i)
                val brightness = if (trailIndex >= 0) {
                    (1f - trailIndex.toFloat() / trailLength) * globalScale
                } else {
                    0f
                }

                val r = (baseR * brightness).roundToInt().coerceIn(0, 255)
                val g = (baseG * brightness).roundToInt().coerceIn(0, 255)
                val b = (baseB * brightness).roundToInt().coerceIn(0, 255)

                ledController.setLedColor(
                    r, g, b,
                    leftTop = i == 0,
                    leftBottom = i == 1,
                    rightTop = i == 2,
                    rightBottom = i == 3
                )
            }

            currentLed = (currentLed + 1) % 4

            val delay = (150 - 100 * speed).toLong()
            handler.postDelayed(this, delay)
        }
    }

    override fun start() {
        if (running) return
        running = true
        ledTrail.clear()
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