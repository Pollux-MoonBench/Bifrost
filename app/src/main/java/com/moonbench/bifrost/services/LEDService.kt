package com.moonbench.bifrost.services

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.moonbench.bifrost.MainActivity
import com.moonbench.bifrost.R
import com.moonbench.bifrost.animations.AmbiAuroraAnimation
import com.moonbench.bifrost.animations.AmbilightAnimation
import com.moonbench.bifrost.animations.AudioReactiveAnimation
import com.moonbench.bifrost.animations.BreathAnimation
import com.moonbench.bifrost.animations.ChaseAnimation
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
import java.util.concurrent.atomic.AtomicBoolean

class LEDService : Service() {

    companion object {
        const val CHANNEL_ID = "LEDServiceChannel"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.moonbench.bifrost.STOP"
        const val ACTION_UPDATE_PARAMS = "com.moonbench.bifrost.UPDATE_PARAMS"
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
    private var currentBrightness: Int = 255
    private var currentSpeed: Float = 0.5f
    private var currentSmoothness: Float = 0.5f
    private var currentSensitivity: Float = 0.5f
    private var currentProfile: PerformanceProfile = PerformanceProfile.MEDIUM
    private var currentAnimationType: LedAnimationType = LedAnimationType.AMBILIGHT
    private var currentSaturationBoost: Float = 0f
    private var currentUseCustomSampling: Boolean = false

    private val activityCheckRunnable = object : Runnable {
        override fun run() {
            if (!isActivityRunning()) {
                cleanupAndStop()
            } else {
                handler.postDelayed(this, 2000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        ledController = LedController()
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
        val brightness = intent.getIntExtra("brightness", 255).coerceIn(0, 255)
        val speed = intent.getFloatExtra("speed", 0.5f).coerceIn(0f, 1f)
        val smoothness = intent.getFloatExtra("smoothness", 0.5f).coerceIn(0f, 1f)
        val sensitivity = intent.getFloatExtra("sensitivity", 0.5f).coerceIn(0f, 1f)
        currentSaturationBoost = intent.getFloatExtra("saturationBoost", 0f).coerceIn(0f, 1f)
        currentUseCustomSampling = intent.getBooleanExtra("useCustomSampling", false)

        currentAnimationType = animationType
        currentProfile = profile
        currentColor = color
        currentBrightness = brightness
        currentSpeed = speed
        currentSmoothness = smoothness
        currentSensitivity = sensitivity

        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_OK)
        val data = intent.getParcelableExtra<Intent>("data")

        if (isTransitioning.getAndSet(true)) {
            handler.postDelayed({
                processAnimationChange(animationType, color, brightness, speed, smoothness, sensitivity, profile, resultCode, data)
            }, 200)
        } else {
            processAnimationChange(animationType, color, brightness, speed, smoothness, sensitivity, profile, resultCode, data)
        }

        return START_NOT_STICKY
    }

    private fun handleUpdateParams(intent: Intent) {
        if (!isRunning) return
        val animation = currentAnimation ?: return

        if (intent.hasExtra("animationColor")) {
            val newColor = intent.getIntExtra("animationColor", currentColor)
            if (newColor != currentColor) {
                currentColor = newColor
                if (currentAnimationType.needsColorSelection) {
                    val type = currentAnimationType
                    val brightness = currentBrightness
                    val speed = currentSpeed
                    val smoothness = currentSmoothness
                    val sensitivity = currentSensitivity
                    val profile = currentProfile
                    stopCurrentAnimation()
                    startAnimation(type, currentColor, brightness, speed, smoothness, sensitivity, profile)
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
                val type = currentAnimationType
                val brightness = currentBrightness
                val speed = currentSpeed
                val smoothness = currentSmoothness
                val sensitivity = currentSensitivity
                val profile = currentProfile
                stopCurrentAnimation()
                startAnimation(type, currentColor, brightness, speed, smoothness, sensitivity, profile)
            }
        }
    }

    private fun processAnimationChange(
        animationType: LedAnimationType,
        color: Int,
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
                                    startAnimation(animationType, color, brightness, speed, smoothness, sensitivity, profile)
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
                    startAnimation(animationType, color, brightness, speed, smoothness, sensitivity, profile)
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
        } catch (e: Exception) {
            e.printStackTrace()
            currentAnimation = null
        }
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
        cleanupAndStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(activityCheckRunnable)
        cleanupAndStop()
    }

    private fun cleanupAndStop() {
        if (isStopping.getAndSet(true)) return

        try {
            handler.removeCallbacks(activityCheckRunnable)
            isRunning = false
            isTransitioning.set(false)

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

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent =
            PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LED Active")
            .setContentText("Tap to configure")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startAnimation(
        type: LedAnimationType,
        color: Int,
        brightness: Int,
        speed: Float,
        smoothness: Float,
        sensitivity: Float,
        profile: PerformanceProfile
    ) {
        try {
            currentAnimation = createAnimation(type, color, profile)
            currentAnimation?.setTargetBrightness(brightness)
            currentAnimation?.setSpeed(speed)
            currentAnimation?.setLerpStrength(smoothness)
            currentAnimation?.setSensitivity(sensitivity)
            currentAnimation?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            cleanupAndStop()
        }
    }

    private fun needsMediaProjection(type: LedAnimationType): Boolean {
        return type == LedAnimationType.AMBILIGHT ||
                type == LedAnimationType.AUDIO_REACTIVE ||
                type == LedAnimationType.AMBIAURORA
    }

    private fun createAnimation(type: LedAnimationType, color: Int, profile: PerformanceProfile): LedAnimation? {
        return when (type) {
            LedAnimationType.AMBILIGHT -> {
                val projection = mediaProjection ?: return null
                val windowManager = getSystemService(WindowManager::class.java)
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                AmbilightAnimation(
                    ledController,
                    projection,
                    displayMetrics,
                    profile,
                    currentUseCustomSampling
                )
            }
            LedAnimationType.AUDIO_REACTIVE -> {
                val projection = mediaProjection ?: return null
                val windowManager = getSystemService(WindowManager::class.java)
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                AudioReactiveAnimation(
                    ledController,
                    projection,
                    displayMetrics,
                    color,
                    profile
                )
            }
            LedAnimationType.AMBIAURORA -> {
                val projection = mediaProjection ?: return null
                val windowManager = getSystemService(WindowManager::class.java)
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                AmbiAuroraAnimation(
                    ledController,
                    projection,
                    displayMetrics,
                    profile,
                    currentUseCustomSampling
                )
            }
            LedAnimationType.STATIC -> StaticAnimation(ledController, color)
            LedAnimationType.BREATH -> BreathAnimation(ledController, color)
            LedAnimationType.RAINBOW -> RainbowAnimation(ledController)
            LedAnimationType.PULSE -> PulseAnimation(ledController, color)
            LedAnimationType.STROBE -> StrobeAnimation(ledController, color)
            LedAnimationType.SPARKLE -> SparkleAnimation(ledController, color)
            LedAnimationType.FADE_TRANSITION -> FadeTransitionAnimation(ledController, color)
            LedAnimationType.RAVE -> RaveAnimation(ledController)
            LedAnimationType.CHASE -> ChaseAnimation(ledController, color)
        }
    }
}