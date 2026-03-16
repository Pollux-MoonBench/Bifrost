package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt
import kotlin.random.Random

class RaveAnimation(
    ledController: LedController
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.RAVE
    override val needsColorSelection: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetBrightness: Int = 255
    private var speed: Float = 0.5f

    override fun setTargetColor(color: Int) {
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

            val randomColor = Color.rgb(
                Random.nextInt(256),
                Random.nextInt(256),
                Random.nextInt(256)
            )

            val baseR = Color.red(randomColor)
            val baseG = Color.green(randomColor)
            val baseB = Color.blue(randomColor)

            val globalScale = targetBrightness / 255f

            val r = (baseR * globalScale).roundToInt().coerceIn(0, 255)
            val g = (baseG * globalScale).roundToInt().coerceIn(0, 255)
            val b = (baseB * globalScale).roundToInt().coerceIn(0, 255)

            ledController.setLedColor(
                r,
                g,
                b,
                leftTop = true,
                leftBottom = true,
                rightTop = true,
                rightBottom = true
            )

            val delay = (150 - 100 * speed).toLong()
            handler.postDelayed(this, adjustedAnimationDelay(delay, targetBrightness))
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