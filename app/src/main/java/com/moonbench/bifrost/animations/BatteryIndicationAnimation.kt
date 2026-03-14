package com.moonbench.bifrost.animations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.cos
import kotlin.math.roundToInt

class BatteryIndicatorAnimation(
    ledController: LedController,
    private val context: Context,
    initialBreatheWhenCharging: Boolean = false,
    initialIndicateChargingSpeed: Boolean = false,
    initialFlashWhenReady: Boolean = false
) : LedAnimation(ledController) {

    companion object {
        private const val CHARGING_PULSE_OFFSET = 0.25f
        private const val CHARGING_PULSE_BRIGHTNESS_THRESHOLD = 0.75f
        private const val BASE_CHARGING_PHASE_STEP = 0.08
        private const val READY_FLASH_ON_MS = 180L
        private const val READY_FLASH_OFF_MS = 1200L
        private const val EXTRA_MAX_CHARGING_CURRENT = "max_charging_current"
    }

    override val type: LedAnimationType = LedAnimationType.BATTERY_INDICATOR
    override val needsColorSelection: Boolean = false

    private var currentColor = Color.BLACK
    private var targetColor = Color.BLACK
    private var currentBrightness: Int = 255
    private var targetBrightness: Int = 255
    private var isBlinking = false
    private var blinkState = false
    private var isPluggedIn = false
    private var batteryStatus: Int = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var batteryPercentage: Int = 100
    private var breatheWhenCharging = initialBreatheWhenCharging
    private var indicateChargingSpeed = initialIndicateChargingSpeed
    private var flashWhenReady = initialFlashWhenReady
    private var breathPhase = 0.0
    private var chargingPhaseStep = BASE_CHARGING_PHASE_STEP
    private var readyFlashState = false
    private var isBatteryReceiverRegistered = false
    private val batteryManager by lazy { context.getSystemService(BatteryManager::class.java) }

    @Volatile
    private var isRunning = false

    private val handler = Handler(Looper.getMainLooper())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isRunning) return
            updateBatteryState(intent)
        }
    }

    private val colorLerpRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (isBlinking) {
                blinkState = !blinkState
                val brightness = if (blinkState) targetBrightness else 0
                applyLeds(targetColor, brightness)
                handler.postDelayed(this, 500)
            } else if (shouldFlashWhenReady()) {
                readyFlashState = !readyFlashState
                val brightness = if (readyFlashState) calculateReadyFlashBrightness() else 0
                applyLeds(targetColor, brightness)
                handler.postDelayed(this, if (readyFlashState) READY_FLASH_ON_MS else READY_FLASH_OFF_MS)
            } else if (shouldBreathe()) {
                val lerpFactor = 0.15f
                currentColor = lerpColor(currentColor, targetColor, lerpFactor)
                currentBrightness = lerpInt(currentBrightness, targetBrightness, lerpFactor)

                val breathBrightness = calculateChargingBreathBrightness(currentBrightness)

                applyLeds(currentColor, breathBrightness)

                breathPhase += chargingPhaseStep
                handler.postDelayed(this, 30)
            } else {
                val lerpFactor = 0.15f
                currentColor = lerpColor(currentColor, targetColor, lerpFactor)
                currentBrightness = lerpInt(currentBrightness, targetBrightness, lerpFactor)

                applyLeds(currentColor, currentBrightness)

                val colorDiff = colorDistance(currentColor, targetColor)
                val brightnessDiff = kotlin.math.abs(currentBrightness - targetBrightness)

                if (colorDiff > 2 || brightnessDiff > 2) {
                    handler.postDelayed(this, 16)
                } else {
                    currentColor = targetColor
                    currentBrightness = targetBrightness
                    applyLeds(currentColor, currentBrightness)
                }
            }
        }
    }

    private fun restartLerpAnimation() {
        handler.removeCallbacks(colorLerpRunnable)
        handler.post(colorLerpRunnable)
    }

    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
        if (isRunning) {
            restartLerpAnimation()
        }
    }

    override fun setBreatheWhenCharging(enabled: Boolean) {
        if (breatheWhenCharging == enabled) return
        breatheWhenCharging = enabled
        if (isRunning) {
            restartLerpAnimation()
        }
    }

    override fun setIndicateChargingSpeed(enabled: Boolean) {
        if (indicateChargingSpeed == enabled) return
        indicateChargingSpeed = enabled
        chargingPhaseStep = resolveChargingPhaseStep()
        if (isRunning) {
            restartLerpAnimation()
        }
    }

    override fun setFlashWhenReady(enabled: Boolean) {
        if (flashWhenReady == enabled) return
        flashWhenReady = enabled
        if (isRunning) {
            restartLerpAnimation()
        }
    }

    override fun start() {
        if (isRunning) return
        isRunning = true

        val initialBatteryState = context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        isBatteryReceiverRegistered = true
        updateBatteryState(initialBatteryState)
        restartLerpAnimation()
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(colorLerpRunnable)
        if (isBatteryReceiverRegistered) {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            isBatteryReceiverRegistered = false
        }
        currentBrightness = 0
        applyLeds(Color.BLACK, 0)
    }

    private fun shouldBreathe(): Boolean {
        return breatheWhenCharging && isPluggedIn && !isBlinking
    }

    private fun shouldFlashWhenReady(): Boolean {
        return flashWhenReady && isPluggedIn && (
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL ||
                (batteryPercentage >= 100 && batteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING)
            )
    }

    private fun updateBatteryState(batteryStatus: Intent?) {
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val previousTargetColor = targetColor
        val wasBlinking = isBlinking
        val wasBreathing = shouldBreathe()
        val wasReadyFlashing = shouldFlashWhenReady()

        batteryPercentage = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).roundToInt()
        } else {
            100
        }

        isPluggedIn = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        this.batteryStatus = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        isBlinking = batteryPercentage <= 5 && !isPluggedIn
        chargingPhaseStep = resolveChargingPhaseStep(batteryStatus)

        targetColor = calculateColorForBattery(batteryPercentage)
        val isBehaviorChanged =
            wasBlinking != isBlinking ||
                wasBreathing != shouldBreathe() ||
                wasReadyFlashing != shouldFlashWhenReady()
        val colorChanged = previousTargetColor != targetColor

        if (isRunning && (isBehaviorChanged || colorChanged)) {
            if (isBehaviorChanged) {
                blinkState = false
                readyFlashState = false
                breathPhase = 0.0
            }
            restartLerpAnimation()
        }
    }

    private fun calculateChargingBreathBrightness(baseBrightness: Int): Int {
        val base = baseBrightness.coerceIn(0, 255)
        val pulseWave = ((1.0 - cos(breathPhase)) / 2.0).toFloat().coerceIn(0f, 1f)
        val threshold = (255 * CHARGING_PULSE_BRIGHTNESS_THRESHOLD).roundToInt()
        val offset = (255 * CHARGING_PULSE_OFFSET).roundToInt()

        return if (base >= threshold) {
            val dimTarget = (base - offset).coerceIn(0, 255)
            lerpInt(base, dimTarget, pulseWave)
        } else {
            val boostTarget = (base + offset).coerceIn(0, 255)
            lerpInt(base, boostTarget, pulseWave)
        }
    }

    private fun calculateReadyFlashBrightness(): Int {
        val readyBrightness = if (targetBrightness >= (255 * CHARGING_PULSE_BRIGHTNESS_THRESHOLD).roundToInt()) {
            targetBrightness
        } else {
            targetBrightness + (255 * CHARGING_PULSE_OFFSET).roundToInt()
        }
        return readyBrightness.coerceIn(0, 255)
    }

    private fun resolveChargingPhaseStep(intent: Intent? = null): Double {
        if (!indicateChargingSpeed || !isPluggedIn) return BASE_CHARGING_PHASE_STEP

        val chargingCurrentUa = readChargingCurrentMicroAmps(intent)
        if (chargingCurrentUa != null) {
            val chargingCurrentMa = (chargingCurrentUa / 1000f).coerceAtLeast(0f)
            val normalized = ((chargingCurrentMa - 500f) / 2500f).coerceIn(0f, 1f)
            val multiplier = 0.75f + (normalized * 1.0f)
            return BASE_CHARGING_PHASE_STEP * multiplier
        }

        val pluggedState = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val multiplier = when {
            pluggedState and BatteryManager.BATTERY_PLUGGED_AC != 0 -> 1.2f
            pluggedState and BatteryManager.BATTERY_PLUGGED_USB != 0 -> 0.85f
            pluggedState and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> 0.9f
            else -> 1f
        }

        return BASE_CHARGING_PHASE_STEP * multiplier
    }

    private fun readChargingCurrentMicroAmps(intent: Intent?): Long? {
        val currentNow = runCatching {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()
        if (currentNow != null && currentNow != Long.MIN_VALUE && currentNow != 0L) {
            return kotlin.math.abs(currentNow)
        }

        val maxChargingCurrent = intent?.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1) ?: -1
        return maxChargingCurrent.takeIf { it > 0 }?.toLong()
    }

    private fun calculateColorForBattery(percentage: Int): Int {
        return when {
            percentage > 50 -> {
                val factor = (percentage - 50) / 50f
                lerpColor(Color.rgb(255, 255, 0), Color.rgb(0, 255, 0), factor)
            }
            percentage > 20 -> {
                val factor = (percentage - 20) / 30f
                lerpColor(Color.rgb(255, 0, 0), Color.rgb(255, 255, 0), factor)
            }
            else -> {
                Color.rgb(255, 0, 0)
            }
        }
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val rDiff = kotlin.math.abs(Color.red(c1) - Color.red(c2))
        val gDiff = kotlin.math.abs(Color.green(c1) - Color.green(c2))
        val bDiff = kotlin.math.abs(Color.blue(c1) - Color.blue(c2))
        return rDiff + gDiff + bDiff
    }

    private fun applyLeds(color: Int, brightness: Int) {
        val gammaCorrectedBrightness = applyGamma(brightness)
        val scale = gammaCorrectedBrightness / 255f

        val red = (Color.red(color) * scale).roundToInt().coerceIn(0, 255)
        val green = (Color.green(color) * scale).roundToInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * scale).roundToInt().coerceIn(0, 255)

        ledController.setLedColor(
            red,
            green,
            blue,
            leftTop = true,
            leftBottom = true,
            rightTop = true,
            rightBottom = true
        )
    }
}