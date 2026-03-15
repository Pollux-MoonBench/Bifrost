package com.moonbench.bifrost.services

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.moonbench.bifrost.MainActivity
import com.moonbench.bifrost.R
import com.moonbench.bifrost.animations.AmbiAuroraAnimation
import com.moonbench.bifrost.animations.AmbilightAnimation
import com.moonbench.bifrost.animations.AudioReactiveAnimation
import com.moonbench.bifrost.animations.BatteryIndicatorAnimation
import com.moonbench.bifrost.animations.BreathAnimation
import com.moonbench.bifrost.animations.ChaseAnimation
import com.moonbench.bifrost.animations.CpuTemperatureAnimation
import com.moonbench.bifrost.animations.FadeTransitionAnimation
import com.moonbench.bifrost.animations.LedAnimation
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.animations.PulseAnimation
import com.moonbench.bifrost.animations.RainbowAnimation
import com.moonbench.bifrost.animations.RaveAnimation
import com.moonbench.bifrost.animations.SparkleAnimation
import com.moonbench.bifrost.animations.StaticAnimation
import com.moonbench.bifrost.animations.StrobeAnimation
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.LedPreset
import java.util.concurrent.atomic.AtomicBoolean

class LEDService : Service() {

    companion object {
        const val CHANNEL_ID = "LEDServiceChannel"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.moonbench.bifrost.STOP"
        const val ACTION_UPDATE_PARAMS = "com.moonbench.bifrost.UPDATE_PARAMS"
        const val EXTRA_ALLOW_BACKGROUND_RUN = "allowBackgroundRun"
        const val EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED = "batteryOverrideWhenPlugged"
        const val EXTRA_PERSISTENT_NOTIFICATION = "persistentNotification"
        var isRunning = false
    }

    private var mediaProjection: MediaProjection? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var ledController: LedController
    private var currentAnimation: LedAnimation? = null
    private val handler = Handler(Looper.getMainLooper())
    private val isTransitioning = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)

    private var currentColor: Int = Color.WHITE
    private var currentRightColor: Int = Color.WHITE
    private var currentBrightness: Int = 255
    private var currentSpeed: Float = 0.5f
    private var currentSmoothness: Float = 0.5f
    private var currentSensitivity: Float = 0.5f
    private var currentProfile: PerformanceProfile = PerformanceProfile.MEDIUM
    private var currentAnimationType: LedAnimationType = LedAnimationType.AMBILIGHT
    private var currentSaturationBoost: Float = 0f
    private var currentUseCustomSampling: Boolean = false
    private var currentUseSingleColor: Boolean = false
    private var currentBreatheWhenCharging: Boolean = false
    private var currentIndicateChargingSpeed: Boolean = false
    private var currentFlashWhenReady: Boolean = false
    private var currentBatteryOverrideWhenPlugged: Boolean = false
    private var currentPersistentNotification: Boolean = true
    private var allowBackgroundRun: Boolean = false
    private var currentAmbilightDisplayId: Int = Display.DEFAULT_DISPLAY
    private var activeAnimationType: LedAnimationType? = null
    private var lastProjectionResultCode: Int = Activity.RESULT_OK
    private var lastProjectionData: Intent? = null
    private var isDevicePluggedIn: Boolean = false
    private var batteryReceiverRegistered: Boolean = false

    private val batteryStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val batteryIntent = intent ?: return
            if (updatePluggedState(batteryIntent)) {
                restartAnimationForCurrentState()
            }
        }
    }

    private val appProfileManager by lazy {
        val prefs = getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
        AppProfileManager(prefs)
    }

    private val activityCheckRunnable = object : Runnable {
        override fun run() {
            if (!allowBackgroundRun && !isActivityRunning()) {
                cleanupAndStop()
            } else {
                checkAutoProfileSwitch()
                handler.postDelayed(this, 2000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        ledController = LedController()
        registerBatteryStateReceiver()
        handler.postDelayed(activityCheckRunnable, 2000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_STOP) {
            cleanupAndStop()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_UPDATE_PARAMS) {
            handleUpdateParams(intent)
            return START_NOT_STICKY
        }

        allowBackgroundRun = intent.getBooleanExtra(EXTRA_ALLOW_BACKGROUND_RUN, allowBackgroundRun)
        currentPersistentNotification = intent.getBooleanExtra(
            EXTRA_PERSISTENT_NOTIFICATION,
            currentPersistentNotification
        )

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isRunning = true

        val animationTypeName = intent.getStringExtra("animationType")
        val animationType = animationTypeName?.let {
            runCatching { LedAnimationType.valueOf(it) }.getOrNull()
        } ?: LedAnimationType.AMBILIGHT

        val profileName = intent.getStringExtra("performanceProfile")
        val profile = profileName?.let {
            runCatching { PerformanceProfile.valueOf(it) }.getOrNull()
        } ?: PerformanceProfile.HIGH

        val color = intent.getIntExtra("animationColor", Color.WHITE)
        val rightColor = intent.getIntExtra("animationRightColor", color)
        val brightness = intent.getIntExtra("brightness", 255).coerceIn(0, 255)
        val speed = intent.getFloatExtra("speed", 0.5f).coerceIn(0f, 1f)
        val smoothness = intent.getFloatExtra("smoothness", 0.5f).coerceIn(0f, 1f)
        val sensitivity = intent.getFloatExtra("sensitivity", 0.5f).coerceIn(0f, 1f)
        currentSaturationBoost = intent.getFloatExtra("saturationBoost", 0f).coerceIn(0f, 1f)
        currentUseCustomSampling = intent.getBooleanExtra("useCustomSampling", false)
        currentUseSingleColor = intent.getBooleanExtra("useSingleColor", false)
        currentBreatheWhenCharging = intent.getBooleanExtra("breatheWhenCharging", false)
        currentIndicateChargingSpeed = intent.getBooleanExtra("indicateChargingSpeed", false)
        currentFlashWhenReady = intent.getBooleanExtra("flashWhenReady", false)
        currentBatteryOverrideWhenPlugged = intent.getBooleanExtra(
            EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
            currentBatteryOverrideWhenPlugged
        )
        currentAmbilightDisplayId = intent.getIntExtra("ambilightDisplayId", Display.DEFAULT_DISPLAY)

        lastProjectionResultCode = intent.getIntExtra("resultCode", Activity.RESULT_OK)
        if (intent.hasExtra("data")) {
            lastProjectionData = intent.getParcelableExtra("data")
        }

        currentAnimationType = animationType
        currentProfile = profile
        currentColor = color
        currentRightColor = rightColor
        currentBrightness = brightness
        currentSpeed = speed
        currentSmoothness = smoothness
        currentSensitivity = sensitivity

        restartAnimationForCurrentState(force = true)

        return START_NOT_STICKY
    }

    private fun handleUpdateParams(intent: Intent) {
        if (!isRunning) return
        val animation = currentAnimation ?: return

        if (intent.hasExtra("animationColor") || intent.hasExtra("animationRightColor")) {
            val newColor = intent.getIntExtra("animationColor", currentColor)
            val newRightColor = intent.getIntExtra("animationRightColor", currentRightColor)
            if (newColor != currentColor || newRightColor != currentRightColor) {
                currentColor = newColor
                currentRightColor = newRightColor
                if (currentAnimationType.needsColorSelection) {
                    restartAnimationForCurrentState(force = true)
                    return
                }
            }
        }

        if (intent.hasExtra("brightness")) {
            val newBrightness = intent.getIntExtra("brightness", currentBrightness).coerceIn(0, 255)
            currentBrightness = newBrightness
            animation.setTargetBrightness(currentBrightness)
        }

        if (intent.hasExtra("speed")) {
            val newSpeed = intent.getFloatExtra("speed", currentSpeed).coerceIn(0f, 1f)
            currentSpeed = newSpeed
            animation.setSpeed(currentSpeed)
        }

        if (intent.hasExtra("smoothness")) {
            val newSmoothness = intent.getFloatExtra("smoothness", currentSmoothness).coerceIn(0f, 1f)
            currentSmoothness = newSmoothness
            animation.setLerpStrength(currentSmoothness)
        }

        if (intent.hasExtra("sensitivity")) {
            val newSensitivity = intent.getFloatExtra("sensitivity", currentSensitivity).coerceIn(0f, 1f)
            currentSensitivity = newSensitivity
            animation.setSensitivity(currentSensitivity)
        }

        if (intent.hasExtra("saturationBoost")) {
            val newSaturationBoost = intent.getFloatExtra("saturationBoost", currentSaturationBoost).coerceIn(0f, 1f)
            if (newSaturationBoost != currentSaturationBoost) {
                currentSaturationBoost = newSaturationBoost
                currentAnimation?.setSaturationBoost(currentSaturationBoost)
            }
        }

        if (intent.hasExtra("useCustomSampling")) {
            val newUseCustomSampling = intent.getBooleanExtra("useCustomSampling", currentUseCustomSampling)
            if (newUseCustomSampling != currentUseCustomSampling) {
                currentUseCustomSampling = newUseCustomSampling
                restartAnimationForCurrentState(force = true)
            }
        }

        if (intent.hasExtra("useSingleColor")) {
            val newUseSingleColor = intent.getBooleanExtra("useSingleColor", currentUseSingleColor)
            if (newUseSingleColor != currentUseSingleColor) {
                currentUseSingleColor = newUseSingleColor
                restartAnimationForCurrentState(force = true)
            }
        }

        if (intent.hasExtra("breatheWhenCharging")) {
            val newBreatheWhenCharging = intent.getBooleanExtra(
                "breatheWhenCharging",
                currentBreatheWhenCharging
            )
            if (newBreatheWhenCharging != currentBreatheWhenCharging) {
                currentBreatheWhenCharging = newBreatheWhenCharging
                animation.setBreatheWhenCharging(currentBreatheWhenCharging)
            }
        }

        if (intent.hasExtra("indicateChargingSpeed")) {
            val newIndicateChargingSpeed = intent.getBooleanExtra(
                "indicateChargingSpeed",
                currentIndicateChargingSpeed
            )
            if (newIndicateChargingSpeed != currentIndicateChargingSpeed) {
                currentIndicateChargingSpeed = newIndicateChargingSpeed
                animation.setIndicateChargingSpeed(currentIndicateChargingSpeed)
            }
        }

        if (intent.hasExtra("flashWhenReady")) {
            val newFlashWhenReady = intent.getBooleanExtra(
                "flashWhenReady",
                currentFlashWhenReady
            )
            if (newFlashWhenReady != currentFlashWhenReady) {
                currentFlashWhenReady = newFlashWhenReady
                animation.setFlashWhenReady(currentFlashWhenReady)
            }
        }

        if (intent.hasExtra(EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED)) {
            val newBatteryOverrideWhenPlugged = intent.getBooleanExtra(
                EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                currentBatteryOverrideWhenPlugged
            )
            if (newBatteryOverrideWhenPlugged != currentBatteryOverrideWhenPlugged) {
                currentBatteryOverrideWhenPlugged = newBatteryOverrideWhenPlugged
                restartAnimationForCurrentState()
            }
        }

        if (intent.hasExtra(EXTRA_PERSISTENT_NOTIFICATION)) {
            val newPersistentNotification = intent.getBooleanExtra(
                EXTRA_PERSISTENT_NOTIFICATION,
                currentPersistentNotification
            )
            if (newPersistentNotification != currentPersistentNotification) {
                currentPersistentNotification = newPersistentNotification
                updateForegroundNotification()
            }
        }
    }

    private fun restartAnimationForCurrentState(force: Boolean = false) {
        if (!isRunning || isStopping.get()) return

        val effectiveType = resolveEffectiveAnimationType()
        if (!force && effectiveType == activeAnimationType) return

        if (isTransitioning.getAndSet(true)) {
            handler.postDelayed({
                processAnimationChange(
                    effectiveType,
                    currentColor,
                    currentRightColor,
                    currentBrightness,
                    currentSpeed,
                    currentSmoothness,
                    currentSensitivity,
                    currentProfile,
                    lastProjectionResultCode,
                    lastProjectionData
                )
            }, 200)
        } else {
            processAnimationChange(
                effectiveType,
                currentColor,
                currentRightColor,
                currentBrightness,
                currentSpeed,
                currentSmoothness,
                currentSensitivity,
                currentProfile,
                lastProjectionResultCode,
                lastProjectionData
            )
        }
    }

    private fun resolveEffectiveAnimationType(): LedAnimationType {
        return if (
            currentBatteryOverrideWhenPlugged &&
            isDevicePluggedIn &&
            currentAnimationType != LedAnimationType.BATTERY_INDICATOR
        ) {
            LedAnimationType.BATTERY_INDICATOR
        } else {
            currentAnimationType
        }
    }

    private fun registerBatteryStateReceiver() {
        if (batteryReceiverRegistered) return
        val stickyIntent = registerReceiver(
            batteryStateReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        batteryReceiverRegistered = true
        stickyIntent?.let { updatePluggedState(it) }
    }

    private fun unregisterBatteryStateReceiver() {
        if (!batteryReceiverRegistered) return
        runCatching { unregisterReceiver(batteryStateReceiver) }
        batteryReceiverRegistered = false
    }

    private fun updatePluggedState(intent: Intent): Boolean {
        val plugged = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
        if (plugged == isDevicePluggedIn) return false
        isDevicePluggedIn = plugged
        return true
    }

    private fun processAnimationChange(
        animationType: LedAnimationType,
        color: Int,
        rightColor: Int,
        brightness: Int,
        speed: Float,
        smoothness: Float,
        sensitivity: Float,
        profile: PerformanceProfile,
        resultCode: Int,
        data: Intent?
    ) {
        stopCurrentAnimation()

        if (needsMediaProjection(animationType) && resultCode == Activity.RESULT_OK && data != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (isRunning && !isStopping.get()) {
                        mediaProjection?.stop()
                        mediaProjection = null

                        handler.postDelayed({
                            try {
                                if (isRunning && !isStopping.get()) {
                                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                                    startAnimation(animationType, color, rightColor, brightness, speed, smoothness, sensitivity, profile, currentSaturationBoost)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                cleanupAndStop()
                            } finally {
                                isTransitioning.set(false)
                            }
                        }, 150)
                    } else {
                        isTransitioning.set(false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isTransitioning.set(false)
                    cleanupAndStop()
                }
            }, 100)
        } else {
            handler.postDelayed({
                if (isRunning && !isStopping.get()) {
                    startAnimation(animationType, color, rightColor, brightness, speed, smoothness, sensitivity, profile, currentSaturationBoost)
                }
                isTransitioning.set(false)
            }, 100)
        }
    }

    private fun stopCurrentAnimation() {
        try {
            currentAnimation?.stop()
            Thread.sleep(100)
            currentAnimation = null
            activeAnimationType = null
        } catch (e: Exception) {
            e.printStackTrace()
            currentAnimation = null
            activeAnimationType = null
        }
    }

    private fun checkAutoProfileSwitch() {
        if (!isRunning || isTransitioning.get() || isStopping.get()) return

        val preset = appProfileManager.checkForSwitch(this) ?: return

        // While the plugged-in battery override is active, keep tracking foreground-app
        // changes but do not apply the preset switch until the override is lifted.
        if (currentBatteryOverrideWhenPlugged && isDevicePluggedIn) return

        val needsMP = needsMediaProjection(preset.animationType)
        if (needsMP && mediaProjection == null) return

        currentAnimationType = preset.animationType
        currentProfile = preset.performanceProfile
        currentColor = preset.color
        currentRightColor = preset.rightColor
        currentBrightness = preset.brightness
        currentSpeed = preset.speed
        currentSmoothness = preset.smoothness
        currentSensitivity = preset.sensitivity
        currentSaturationBoost = preset.saturationBoost
        currentUseCustomSampling = preset.useCustomSampling
        currentUseSingleColor = preset.useSingleColor
        currentBreatheWhenCharging = preset.breatheWhenCharging
        currentIndicateChargingSpeed = preset.indicateChargingSpeed
        currentFlashWhenReady = preset.flashWhenReady
        restartAnimationForCurrentState(force = true)
    }

    private fun isActivityRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = activityManager.appTasks
        for (task in tasks) {
            val componentName = task.taskInfo.baseActivity
            if (componentName?.packageName == packageName) {
                return true
            }
        }
        return false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!allowBackgroundRun) {
            cleanupAndStop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(activityCheckRunnable)
        unregisterBatteryStateReceiver()
        cleanupAndStop()
    }

    private fun cleanupAndStop() {
        if (isStopping.getAndSet(true)) return

        try {
            handler.removeCallbacks(activityCheckRunnable)
            isRunning = false
            allowBackgroundRun = false
            isTransitioning.set(false)
            activeAnimationType = null

            stopCurrentAnimation()

            handler.postDelayed({
                try {
                    mediaProjection?.stop()
                    mediaProjection = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    ledController.setLedColor(0, 0, 0, 0, true, true, true, true)
                    Thread.sleep(200)
                    ledController.shutdown()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }, 150)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                ledController.shutdown()
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "LED Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, LEDService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPendingIntent =
            PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LED Active")
            .setContentText("Bifrost is running")
            .setSubText("Tap this notification to modify settings")
            .setSmallIcon(R.mipmap.ic_notification_foreground)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(currentPersistentNotification)
            .build()
    }

    private fun updateForegroundNotification() {
        if (!isRunning) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching {
            manager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    @Suppress("DEPRECATION")
    private fun getDisplayMetrics(displayId: Int): DisplayMetrics {
        val metrics = DisplayMetrics()
        val dm = getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(displayId) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            display.getMetrics(metrics)
        } else {
            getSystemService(WindowManager::class.java).defaultDisplay.getMetrics(metrics)
        }
        return metrics
    }

    private fun startAnimation(
        type: LedAnimationType,
        color: Int,
        rightColor: Int = color,
        brightness: Int,
        speed: Float,
        smoothness: Float,
        sensitivity: Float,
        profile: PerformanceProfile,
        saturationBoost: Float
    ) {
        try {
            val animation = createAnimation(type, color, rightColor, profile, saturationBoost)
            currentAnimation = animation

            if (animation == null) {
                activeAnimationType = null
                return
            }

            animation.setTargetBrightness(brightness)
            animation.setSpeed(speed)
            animation.setLerpStrength(smoothness)
            animation.setSensitivity(sensitivity)
            animation.setBreatheWhenCharging(currentBreatheWhenCharging)
            animation.setIndicateChargingSpeed(currentIndicateChargingSpeed)
            animation.setFlashWhenReady(currentFlashWhenReady)
            animation.start()
            activeAnimationType = type
        } catch (e: Exception) {
            e.printStackTrace()
            activeAnimationType = null
            cleanupAndStop()
        }
    }

    private fun needsMediaProjection(type: LedAnimationType): Boolean {
        return type == LedAnimationType.AMBILIGHT ||
                type == LedAnimationType.AUDIO_REACTIVE ||
                type == LedAnimationType.AMBIAURORA
    }

    private fun createAnimation(
        type: LedAnimationType,
        color: Int,
        rightColor: Int = color,
        profile: PerformanceProfile,
        saturationBoost: Float
    ): LedAnimation? {
        return when (type) {
            LedAnimationType.AMBILIGHT -> {
                val projection = mediaProjection ?: return null
                val displayMetrics = getDisplayMetrics(currentAmbilightDisplayId)
                AmbilightAnimation(
                    ledController,
                    projection,
                    displayMetrics,
                    profile,
                    currentUseCustomSampling,
                    currentUseSingleColor,
                    saturationBoost
                )
            }
            LedAnimationType.AUDIO_REACTIVE -> {
                val projection = mediaProjection ?: return null
                val displayMetrics = getDisplayMetrics(currentAmbilightDisplayId)
                AudioReactiveAnimation(
                    ledController,
                    projection,
                    displayMetrics,
                    color,
                    rightColor,
                    profile
                )
            }
            LedAnimationType.AMBIAURORA -> {
                val projection = mediaProjection ?: return null
                val displayMetrics = getDisplayMetrics(currentAmbilightDisplayId)
                AmbiAuroraAnimation(
                    ledController,
                    projection,
                    displayMetrics,
                    profile,
                    currentUseCustomSampling,
                    currentUseSingleColor,
                    saturationBoost
                )
            }
            LedAnimationType.BATTERY_INDICATOR -> BatteryIndicatorAnimation(
                ledController,
                this,
                currentBreatheWhenCharging,
                currentIndicateChargingSpeed,
                currentFlashWhenReady
            )
            LedAnimationType.CPU_TEMPERATURE -> CpuTemperatureAnimation(ledController)
            LedAnimationType.STATIC -> StaticAnimation(ledController, color, rightColor)
            LedAnimationType.BREATH -> BreathAnimation(ledController, color, rightColor)
            LedAnimationType.RAINBOW -> RainbowAnimation(ledController)
            LedAnimationType.PULSE -> PulseAnimation(ledController, color, rightColor)
            LedAnimationType.STROBE -> StrobeAnimation(ledController, color, rightColor)
            LedAnimationType.SPARKLE -> SparkleAnimation(ledController, color, rightColor)
            LedAnimationType.FADE_TRANSITION -> FadeTransitionAnimation(ledController, color, rightColor)
            LedAnimationType.RAVE -> RaveAnimation(ledController)
            LedAnimationType.CHASE -> ChaseAnimation(ledController, color, rightColor)
        }
    }
}