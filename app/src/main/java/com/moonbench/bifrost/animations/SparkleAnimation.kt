package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt
import kotlin.random.Random

class SparkleAnimation(
    ledController: LedController,
    initialColor: Int,
    initialRightColor: Int = initialColor
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.SPARKLE
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetColor: Int = initialColor
    private var currentColor: Int = initialColor
    private var targetRightColor: Int = initialRightColor
    private var currentRightColor: Int = initialRightColor
    private var targetBrightness: Int = 255
    private var lerpStrength: Float = 0.5f
    private val ledBrightness = mutableListOf(0f, 0f, 0f, 0f)

    override fun setTargetColor(color: Int) {
        targetColor = color
    }

    override fun setTargetRightColor(color: Int) {
        targetRightColor = color
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

            val colorFactor = 0.05f + 0.45f * lerpStrength
            currentColor = lerpColor(currentColor, targetColor, colorFactor)
            currentRightColor = lerpColor(currentRightColor, targetRightColor, colorFactor)

            val leftR = Color.red(currentColor)
            val leftG = Color.green(currentColor)
            val leftB = Color.blue(currentColor)

            val rightR = Color.red(currentRightColor)
            val rightG = Color.green(currentRightColor)
            val rightB = Color.blue(currentRightColor)

            val globalScale = targetBrightness / 255f

            for (i in 0 until 4) {
                val sparkleChance = 0.02f + 0.08f * lerpStrength
                if (Random.nextFloat() < sparkleChance) {
                    ledBrightness[i] = Random.nextFloat()
                } else {
                    ledBrightness[i] *= 0.9f
                }
            }

            val r0 = (leftR * ledBrightness[0] * globalScale).roundToInt().coerceIn(0, 255)
            val g0 = (leftG * ledBrightness[0] * globalScale).roundToInt().coerceIn(0, 255)
            val b0 = (leftB * ledBrightness[0] * globalScale).roundToInt().coerceIn(0, 255)

            val r1 = (leftR * ledBrightness[1] * globalScale).roundToInt().coerceIn(0, 255)
            val g1 = (leftG * ledBrightness[1] * globalScale).roundToInt().coerceIn(0, 255)
            val b1 = (leftB * ledBrightness[1] * globalScale).roundToInt().coerceIn(0, 255)

            val r2 = (rightR * ledBrightness[2] * globalScale).roundToInt().coerceIn(0, 255)
            val g2 = (rightG * ledBrightness[2] * globalScale).roundToInt().coerceIn(0, 255)
            val b2 = (rightB * ledBrightness[2] * globalScale).roundToInt().coerceIn(0, 255)

            val r3 = (rightR * ledBrightness[3] * globalScale).roundToInt().coerceIn(0, 255)
            val g3 = (rightG * ledBrightness[3] * globalScale).roundToInt().coerceIn(0, 255)
            val b3 = (rightB * ledBrightness[3] * globalScale).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(r0, g0, b0, leftTop = true, leftBottom = false, rightTop = false, rightBottom = false)
            ledController.setLedColor(r1, g1, b1, leftTop = false, leftBottom = true, rightTop = false, rightBottom = false)
            ledController.setLedColor(r2, g2, b2, leftTop = false, leftBottom = false, rightTop = true, rightBottom = false)
            ledController.setLedColor(r3, g3, b3, leftTop = false, leftBottom = false, rightTop = false, rightBottom = true)

            handler.postDelayed(this, 50L)
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