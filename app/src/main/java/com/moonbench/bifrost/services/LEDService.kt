package com.moonbench.bifrost.services

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
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
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import java.util.concurrent.atomic.AtomicBoolean

class LEDService : Service() {

    companion object {
        private const val TAG = "BIBI"
        private const val PREF_KEY_LAST_PRESET = "last_preset_name"
        private const val ACTIVITY_CHECK_INTERVAL_MS = 2000L
        private const val ACTIVITY_CHECK_INTERVAL_APP_PROFILE_MS = 700L
        private const val TRANSITION_RETRY_DELAY_MS = 200L
        private const val TRANSITION_START_DELAY_MS = 100L
        private const val PROJECTION_RESTART_DELAY_MS = 150L
        private const val LED_OFF_SETTLE_DELAY_MS = 120L

        const val CHANNEL_ID = "LEDServiceChannel"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.moonbench.bifrost.STOP"
        const val ACTION_UPDATE_PARAMS = "com.moonbench.bifrost.UPDATE_PARAMS"
        const val ACTION_FORCE_APP_PROFILE_RESOLUTION = "com.moonbench.bifrost.FORCE_APP_PROFILE_RESOLUTION"
        const val ACTION_SUPPLY_PROJECTION = "com.moonbench.bifrost.SUPPLY_PROJECTION"
        const val EXTRA_ALLOW_BACKGROUND_RUN = "allowBackgroundRun"
        const val EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED = "batteryOverrideWhenPlugged"
        const val EXTRA_PERSISTENT_NOTIFICATION = "persistentNotification"
        private const val EXTRA_BATTERY_LOW_COLOR_OVERRIDE = "batteryLowColorOverride"
        private const val EXTRA_BATTERY_MID_COLOR_OVERRIDE = "batteryMidColorOverride"
        private const val EXTRA_BATTERY_HIGH_COLOR_OVERRIDE = "batteryHighColorOverride"
        private const val EXTRA_CPU_COOL_COLOR_OVERRIDE = "cpuCoolColorOverride"
        private const val EXTRA_CPU_WARM_COLOR_OVERRIDE = "cpuWarmColorOverride"
        private const val EXTRA_CPU_HOT_COLOR_OVERRIDE = "cpuHotColorOverride"
        private const val COLOR_OVERRIDE_UNSET = Int.MIN_VALUE
        private const val PROJECTION_PROMPT_CHANNEL_ID = "bifrost_projection_prompt_channel"
        private const val PROJECTION_PROMPT_NOTIFICATION_ID = 4244
        var isRunning = false
    }

    private var mediaProjection: MediaProjection? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var ledController: LedController
    private var currentAnimation: LedAnimation? = null
    private val handler = Handler(Looper.getMainLooper())
    private val isTransitioning = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)
    private val mediaProjectionLock = Any()

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
    private var currentBatteryLowColorOverride: Int? = null
    private var currentBatteryMidColorOverride: Int? = null
    private var currentBatteryHighColorOverride: Int? = null
    private var currentCpuCoolColorOverride: Int? = null
    private var currentCpuWarmColorOverride: Int? = null
    private var currentCpuHotColorOverride: Int? = null
    private var currentBatteryOverrideWhenPlugged: Boolean = false
    private var currentPersistentNotification: Boolean = true
    private var allowBackgroundRun: Boolean = false
    private var currentAmbilightDisplayId: Int = Display.DEFAULT_DISPLAY
    private var activeAnimationType: LedAnimationType? = null
    private var lastProjectionResultCode: Int = Activity.RESULT_OK
    private var lastProjectionData: Intent? = null
    private var isDevicePluggedIn: Boolean = false
    private var batteryReceiverRegistered: Boolean = false
    private var pendingTransitionRunnable: Runnable? = null
    private var pendingProjectionRunnable: Runnable? = null
    private var pendingShutdownRunnable: Runnable? = null
    private var isAppProfileSuppressed: Boolean = false

    private val batteryStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val batteryIntent = intent ?: return
            if (updatePluggedState(batteryIntent)) {
                restartAnimationForCurrentState()
            }
        }
    }

    private val prefs by lazy {
        getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
    }

    private val appProfileManager by lazy {
        AppProfileManager(prefs)
    }

    private val activityCheckRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || isStopping.get()) return

            if (!allowBackgroundRun && !isActivityRunning()) {
                Log.d(TAG, "activityCheckRunnable: activity not running & no background run → stopping")
                cleanupAndStop()
            } else {
                Log.d(TAG, "activityCheckRunnable: tick — appProfileEnabled=${appProfileManager.isEnabled}, currentAnimationType=$currentAnimationType, activeAnimationType=$activeAnimationType, currentAnimation=${currentAnimation != null}, isTransitioning=${isTransitioning.get()}")
                checkAutoProfileSwitch()
                val nextDelay = if (appProfileManager.isEnabled) {
                    ACTIVITY_CHECK_INTERVAL_APP_PROFILE_MS
                } else {
                    ACTIVITY_CHECK_INTERVAL_MS
                }
                handler.postDelayed(this, nextDelay)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        ledController = LedController()
        registerBatteryStateReceiver()
        refreshPluggedStateSnapshot()
        handler.post(activityCheckRunnable)
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

        if (intent.action == ACTION_FORCE_APP_PROFILE_RESOLUTION) {
            Log.d(TAG, "onStartCommand: ACTION_FORCE_APP_PROFILE_RESOLUTION, isRunning=$isRunning")
            if (isRunning) {
                checkAutoProfileSwitch()
            }
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_SUPPLY_PROJECTION) {
            handleSupplyProjection(intent)
            return START_NOT_STICKY
        }

        allowBackgroundRun = intent.getBooleanExtra(EXTRA_ALLOW_BACKGROUND_RUN, allowBackgroundRun)
        currentBatteryOverrideWhenPlugged = intent.getBooleanExtra(
            EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
            currentBatteryOverrideWhenPlugged
        )
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
        currentBatteryLowColorOverride = parseOptionalColor(intent, EXTRA_BATTERY_LOW_COLOR_OVERRIDE)
        currentBatteryMidColorOverride = parseOptionalColor(intent, EXTRA_BATTERY_MID_COLOR_OVERRIDE)
        currentBatteryHighColorOverride = parseOptionalColor(intent, EXTRA_BATTERY_HIGH_COLOR_OVERRIDE)
        currentCpuCoolColorOverride = parseOptionalColor(intent, EXTRA_CPU_COOL_COLOR_OVERRIDE)
        currentCpuWarmColorOverride = parseOptionalColor(intent, EXTRA_CPU_WARM_COLOR_OVERRIDE)
        currentCpuHotColorOverride = parseOptionalColor(intent, EXTRA_CPU_HOT_COLOR_OVERRIDE)
        currentAmbilightDisplayId = intent.getIntExtra("ambilightDisplayId", Display.DEFAULT_DISPLAY)

        // Protect existing projection data when app-profile mode is active
        // and the intent was built for a non-MP animation (won't have real MP extras).
        if (intent.hasExtra("resultCode")) {
            lastProjectionResultCode = intent.getIntExtra("resultCode", Activity.RESULT_OK)
        }
        if (intent.hasExtra("data")) {
            val intentData: Intent? = intent.getParcelableExtra("data")
            if (intentData != null || !appProfileManager.isEnabled) {
                lastProjectionData = intentData
            }
            Log.d(TAG, "onStartCommand: intentData=${intentData != null}, kept lastProjectionData=${lastProjectionData != null}")
        } else {
            Log.d(TAG, "onStartCommand: no 'data' extra in intent, lastProjectionData preserved=${lastProjectionData != null}")
        }
        Log.d(TAG, "onStartCommand: lastProjectionResultCode=$lastProjectionResultCode, lastProjectionData=${lastProjectionData != null}")

        currentAnimationType = animationType
        currentProfile = profile
        currentColor = color
        currentRightColor = rightColor
        currentBrightness = brightness
        currentSpeed = speed
        currentSmoothness = smoothness
        currentSensitivity = sensitivity
        isAppProfileSuppressed = false

        refreshPluggedStateSnapshot()

        if (appProfileManager.isEnabled) {
            Log.d(TAG, "onStartCommand: app profile enabled, calling checkAutoProfileSwitch()")
            checkAutoProfileSwitch()
            if (!isAppProfileSuppressed && currentAnimation == null) {
                Log.d(TAG, "onStartCommand: no animation running after profile check, starting fallback with force=true")
                restartAnimationForCurrentState(force = true)
            }
        } else {
            Log.d(TAG, "onStartCommand: app profile disabled, restarting animation with force=true")
            restartAnimationForCurrentState(force = true)
        }

        return START_NOT_STICKY
    }

    private fun handleUpdateParams(intent: Intent) {
        if (!isRunning) return
        Log.d(TAG, "handleUpdateParams: received update, appProfileEnabled=${appProfileManager.isEnabled}")
        isAppProfileSuppressed = false
        val animation = currentAnimation

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
            animation?.setTargetBrightness(currentBrightness)
        }

        if (intent.hasExtra("speed")) {
            val newSpeed = intent.getFloatExtra("speed", currentSpeed).coerceIn(0f, 1f)
            currentSpeed = newSpeed
            animation?.setSpeed(currentSpeed)
        }

        if (intent.hasExtra("smoothness")) {
            val newSmoothness = intent.getFloatExtra("smoothness", currentSmoothness).coerceIn(0f, 1f)
            currentSmoothness = newSmoothness
            animation?.setLerpStrength(currentSmoothness)
        }

        if (intent.hasExtra("sensitivity")) {
            val newSensitivity = intent.getFloatExtra("sensitivity", currentSensitivity).coerceIn(0f, 1f)
            currentSensitivity = newSensitivity
            animation?.setSensitivity(currentSensitivity)
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
                animation?.setBreatheWhenCharging(currentBreatheWhenCharging)
            }
        }

        if (intent.hasExtra("indicateChargingSpeed")) {
            val newIndicateChargingSpeed = intent.getBooleanExtra(
                "indicateChargingSpeed",
                currentIndicateChargingSpeed
            )
            if (newIndicateChargingSpeed != currentIndicateChargingSpeed) {
                currentIndicateChargingSpeed = newIndicateChargingSpeed
                animation?.setIndicateChargingSpeed(currentIndicateChargingSpeed)
            }
        }

        if (intent.hasExtra("flashWhenReady")) {
            val newFlashWhenReady = intent.getBooleanExtra(
                "flashWhenReady",
                currentFlashWhenReady
            )
            if (newFlashWhenReady != currentFlashWhenReady) {
                currentFlashWhenReady = newFlashWhenReady
                animation?.setFlashWhenReady(currentFlashWhenReady)
            }
        }

        if (
            intent.hasExtra(EXTRA_BATTERY_LOW_COLOR_OVERRIDE) ||
            intent.hasExtra(EXTRA_BATTERY_MID_COLOR_OVERRIDE) ||
            intent.hasExtra(EXTRA_BATTERY_HIGH_COLOR_OVERRIDE) ||
            intent.hasExtra(EXTRA_CPU_COOL_COLOR_OVERRIDE) ||
            intent.hasExtra(EXTRA_CPU_WARM_COLOR_OVERRIDE) ||
            intent.hasExtra(EXTRA_CPU_HOT_COLOR_OVERRIDE)
        ) {
            var paletteChanged = false

            val newBatteryLow = parseOptionalColor(intent, EXTRA_BATTERY_LOW_COLOR_OVERRIDE)
            val newBatteryMid = parseOptionalColor(intent, EXTRA_BATTERY_MID_COLOR_OVERRIDE)
            val newBatteryHigh = parseOptionalColor(intent, EXTRA_BATTERY_HIGH_COLOR_OVERRIDE)
            val newCpuCool = parseOptionalColor(intent, EXTRA_CPU_COOL_COLOR_OVERRIDE)
            val newCpuWarm = parseOptionalColor(intent, EXTRA_CPU_WARM_COLOR_OVERRIDE)
            val newCpuHot = parseOptionalColor(intent, EXTRA_CPU_HOT_COLOR_OVERRIDE)

            if (newBatteryLow != currentBatteryLowColorOverride) {
                currentBatteryLowColorOverride = newBatteryLow
                paletteChanged = true
            }
            if (newBatteryMid != currentBatteryMidColorOverride) {
                currentBatteryMidColorOverride = newBatteryMid
                paletteChanged = true
            }
            if (newBatteryHigh != currentBatteryHighColorOverride) {
                currentBatteryHighColorOverride = newBatteryHigh
                paletteChanged = true
            }
            if (newCpuCool != currentCpuCoolColorOverride) {
                currentCpuCoolColorOverride = newCpuCool
                paletteChanged = true
            }
            if (newCpuWarm != currentCpuWarmColorOverride) {
                currentCpuWarmColorOverride = newCpuWarm
                paletteChanged = true
            }
            if (newCpuHot != currentCpuHotColorOverride) {
                currentCpuHotColorOverride = newCpuHot
                paletteChanged = true
            }

            if (paletteChanged) {
                restartAnimationForCurrentState(force = true)
            }
        }

        if (intent.hasExtra(EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED)) {
            val newBatteryOverrideWhenPlugged = intent.getBooleanExtra(
                EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                currentBatteryOverrideWhenPlugged
            )
            if (newBatteryOverrideWhenPlugged != currentBatteryOverrideWhenPlugged) {
                currentBatteryOverrideWhenPlugged = newBatteryOverrideWhenPlugged
                refreshPluggedStateSnapshot()
                restartAnimationForCurrentState()
                updateForegroundNotification()
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
        if (!isRunning || isStopping.get()) {
            Log.d(TAG, "restartAnimationForCurrentState: skipping — isRunning=$isRunning, isStopping=${isStopping.get()}")
            return
        }

        if (isAppProfileSuppressed && !(currentBatteryOverrideWhenPlugged && isDevicePluggedIn)) {
            Log.d(TAG, "restartAnimationForCurrentState: app profile suppressed → stopping animation")
            stopCurrentAnimation()
            return
        }

        val effectiveType = resolveEffectiveAnimationType()
        Log.d(TAG, "restartAnimationForCurrentState: force=$force, effectiveType=$effectiveType, activeAnimationType=$activeAnimationType")
        if (!force && effectiveType == activeAnimationType) {
            Log.d(TAG, "restartAnimationForCurrentState: same type & not forced, skipping")
            return
        }

        if (isTransitioning.getAndSet(true)) {
            pendingTransitionRunnable?.let(handler::removeCallbacks)
            pendingTransitionRunnable = Runnable {
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
            handler.postDelayed(pendingTransitionRunnable!!, TRANSITION_RETRY_DELAY_MS)
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
        var stickyIntent: Intent? = null
        val registered = runCatching {
            registerReceiver(
                batteryStateReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ).also { stickyIntent = it }
        }.isSuccess
        batteryReceiverRegistered = registered
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
        updateForegroundNotification()
        return true
    }

    private fun refreshPluggedStateSnapshot() {
        val stickyIntent = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        if (stickyIntent != null) {
            updatePluggedState(stickyIntent)
        }
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
        Log.d(TAG, "processAnimationChange: animationType=$animationType, needsMP=${needsMediaProjection(animationType)}, resultCode=$resultCode, data=${data != null}")
        pendingTransitionRunnable?.let(handler::removeCallbacks)
        pendingTransitionRunnable = null
        pendingProjectionRunnable?.let(handler::removeCallbacks)
        pendingProjectionRunnable = null

        stopCurrentAnimation()

        if (needsMediaProjection(animationType) && resultCode == Activity.RESULT_OK && data != null) {
            pendingTransitionRunnable = Runnable {
                try {
                    if (isRunning && !isStopping.get()) {
                        clearMediaProjection()

                        pendingProjectionRunnable = Runnable {
                            try {
                                if (isRunning && !isStopping.get()) {
                                    replaceMediaProjection(resultCode, data)
                                    startAnimation(animationType, color, rightColor, brightness, speed, smoothness, sensitivity, profile, currentSaturationBoost)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                cleanupAndStop()
                            } finally {
                                pendingProjectionRunnable = null
                                isTransitioning.set(false)
                            }
                        }
                        handler.postDelayed(pendingProjectionRunnable!!, PROJECTION_RESTART_DELAY_MS)
                    } else {
                        isTransitioning.set(false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isTransitioning.set(false)
                    cleanupAndStop()
                } finally {
                    pendingTransitionRunnable = null
                }
            }
            handler.postDelayed(pendingTransitionRunnable!!, TRANSITION_START_DELAY_MS)
        } else {
            pendingTransitionRunnable = Runnable {
                if (isRunning && !isStopping.get()) {
                    startAnimation(animationType, color, rightColor, brightness, speed, smoothness, sensitivity, profile, currentSaturationBoost)
                }
                isTransitioning.set(false)
                pendingTransitionRunnable = null
            }
            handler.postDelayed(pendingTransitionRunnable!!, TRANSITION_START_DELAY_MS)
        }
    }

    private fun stopCurrentAnimation() {
        Log.d(TAG, "stopCurrentAnimation: currentAnimation=${currentAnimation != null}, activeAnimationType=$activeAnimationType")
        try {
            currentAnimation?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentAnimation = null
            activeAnimationType = null
        }
    }

    private fun clearMediaProjection() {
        synchronized(mediaProjectionLock) {
            runCatching { mediaProjection?.stop() }
            mediaProjection = null
        }
    }

    private fun replaceMediaProjection(resultCode: Int, data: Intent) {
        synchronized(mediaProjectionLock) {
            runCatching { mediaProjection?.stop() }
            try {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                Log.d(TAG, "replaceMediaProjection: created new projection, isNull=${mediaProjection == null}")
            } catch (e: Exception) {
                Log.e(TAG, "replaceMediaProjection: FAILED to create projection", e)
                mediaProjection = null
            }
        }
    }

    private fun checkAutoProfileSwitch() {
        if (!isRunning || isTransitioning.get() || isStopping.get()) {
            Log.d(TAG, "checkAutoProfileSwitch: skipping — isRunning=$isRunning, isTransitioning=${isTransitioning.get()}, isStopping=${isStopping.get()}")
            return
        }

        val switchResult = appProfileManager.checkForSwitch(this)
        if (switchResult == null) {
            Log.d(TAG, "checkAutoProfileSwitch: no switch needed (null result)")
            return
        }

        Log.d(TAG, "checkAutoProfileSwitch: switchResult presetName='${switchResult.presetName}', preset animType=${switchResult.preset?.animationType}")

        // Keep the UI in sync by tracking which preset is active when auto-switch is enabled.
        prefs.edit().putString(PREF_KEY_LAST_PRESET, switchResult.presetName.orEmpty()).apply()

        // While the plugged-in battery override is active, keep tracking foreground-app
        // changes but do not apply the preset switch until the override is lifted.
        if (currentBatteryOverrideWhenPlugged && isDevicePluggedIn) {
            Log.d(TAG, "checkAutoProfileSwitch: battery override active, NOT applying switch")
            return
        }

        val preset = switchResult.preset
        if (preset == null) {
            Log.d(TAG, "checkAutoProfileSwitch: preset is null → suppressing animation")
            isAppProfileSuppressed = true
            stopCurrentAnimation()
            return
        }

        val needsMP = needsMediaProjection(preset.animationType)
        val hasProjectionData = lastProjectionResultCode == Activity.RESULT_OK && lastProjectionData != null
        Log.d(TAG, "checkAutoProfileSwitch: needsMP=$needsMP, hasProjectionData=$hasProjectionData, lastProjectionResultCode=$lastProjectionResultCode, lastProjectionData=${lastProjectionData != null}")

        if (needsMP && !hasProjectionData) {
            Log.d(TAG, "checkAutoProfileSwitch: needs MP but no projection data → showing prompt")
            val triggerPackage = appProfileManager.getForegroundPackage(this)
            if (!triggerPackage.isNullOrBlank() && switchResult.presetName != null) {
                appProfileManager.setPendingProjectionToken(triggerPackage, switchResult.presetName)
                if (!appProfileManager.isPendingProjectionNotified()) {
                    showProjectionPromptNotification()
                    appProfileManager.markPendingProjectionNotified()
                }
            }
            return
        }

        // If we successfully apply a preset that needed MP, clear any pending token.
        if (needsMP) {
            Log.d(TAG, "checkAutoProfileSwitch: clearing pending projection token (MP preset being applied)")
            appProfileManager.clearPendingProjectionToken()
            dismissProjectionPromptNotification()
        }

        isAppProfileSuppressed = false

        Log.d(TAG, "checkAutoProfileSwitch: APPLYING preset '${switchResult.presetName}' — animationType=${preset.animationType}, color=${preset.color}")
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
        currentBatteryLowColorOverride = preset.batteryLowColorOverride
        currentBatteryMidColorOverride = preset.batteryMidColorOverride
        currentBatteryHighColorOverride = preset.batteryHighColorOverride
        currentCpuCoolColorOverride = preset.cpuCoolColorOverride
        currentCpuWarmColorOverride = preset.cpuWarmColorOverride
        currentCpuHotColorOverride = preset.cpuHotColorOverride
        restartAnimationForCurrentState(force = true)
    }

    private fun parseOptionalColor(intent: Intent, key: String): Int? {
        if (!intent.hasExtra(key)) return null
        val value = intent.getIntExtra(key, COLOR_OVERRIDE_UNSET)
        return value.takeUnless { it == COLOR_OVERRIDE_UNSET }
    }

    private fun isActivityRunning(): Boolean {
        return runCatching {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.appTasks
            for (task in tasks) {
                val componentName = task.taskInfo.baseActivity
                if (componentName?.packageName == packageName) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
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
        clearPendingCallbacks()
        unregisterBatteryStateReceiver()
        cleanupAndStop()
    }

    private fun clearPendingCallbacks() {
        pendingTransitionRunnable?.let(handler::removeCallbacks)
        pendingTransitionRunnable = null
        pendingProjectionRunnable?.let(handler::removeCallbacks)
        pendingProjectionRunnable = null
        pendingShutdownRunnable?.let(handler::removeCallbacks)
        pendingShutdownRunnable = null
    }

    private fun cleanupAndStop() {
        if (isStopping.getAndSet(true)) return
        Log.d(TAG, "cleanupAndStop: STOPPING SERVICE")

        try {
            handler.removeCallbacks(activityCheckRunnable)
            clearPendingCallbacks()
            isRunning = false
            allowBackgroundRun = false
            isTransitioning.set(false)
            activeAnimationType = null
            isAppProfileSuppressed = false

            stopCurrentAnimation()

            pendingShutdownRunnable = Runnable {
                try {
                    clearMediaProjection()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    ledController.setLedColor(0, 0, 0, 0, true, true, true, true)
                    handler.postDelayed({
                        runCatching { ledController.shutdown() }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }, LED_OFF_SETTLE_DELAY_MS)
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } finally {
                    pendingShutdownRunnable = null
                }
            }
            handler.post(pendingShutdownRunnable!!)
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
            .setContentTitle("BIFROST is open")
            .setContentText("Tap to tune")
            .setSubText(resolveNotificationSubText())
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground))
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(currentPersistentNotification)
            .build()
    }

    private fun resolveNotificationSubText(): String {
        return if (isDevicePluggedIn && currentBatteryOverrideWhenPlugged) {
            "Profiles are overridden when charging"
        } else {
            "Following profile presets"
        }
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
            Log.d(TAG, "startAnimation: type=$type, mediaProjection=${synchronized(mediaProjectionLock) { mediaProjection != null }}")
            val animation = createAnimation(type, color, rightColor, profile, saturationBoost)
            currentAnimation = animation

            if (animation == null) {
                Log.w(TAG, "startAnimation: createAnimation returned null for type=$type")
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
            Log.d(TAG, "startAnimation: STARTED type=$type, activeAnimationType=$activeAnimationType")
        } catch (e: Exception) {
            Log.e(TAG, "startAnimation: EXCEPTION for type=$type", e)
            e.printStackTrace()
            activeAnimationType = null
            cleanupAndStop()
        }
    }

    private fun handleSupplyProjection(intent: Intent) {
        Log.d(TAG, "handleSupplyProjection: isRunning=$isRunning, isStopping=${isStopping.get()}")
        if (!isRunning || isStopping.get()) return

        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data: Intent? = intent.getParcelableExtra("data")
        Log.d(TAG, "handleSupplyProjection: resultCode=$resultCode, data=${data != null}")
        if (resultCode != Activity.RESULT_OK || data == null) return

        // Only store the projection token; do NOT create the MediaProjection now.
        // processAnimationChange will create it on-demand the first time an
        // MP-requiring animation actually starts, avoiding exhausting the
        // single-use consent token before it is needed.
        lastProjectionResultCode = resultCode
        lastProjectionData = data
        Log.d(TAG, "handleSupplyProjection: stored projection token")

        appProfileManager.clearPendingProjectionToken()
        dismissProjectionPromptNotification()

        // Force the next periodic check to re-evaluate, so that when the
        // user navigates back to the mapped app the MP-requiring preset
        // is actually applied (the dedup cache previously returned null
        // because the preset name matched even though MP was missing).
        appProfileManager.forceNextResolution()
        Log.d(TAG, "handleSupplyProjection: forced next resolution, waiting for user to navigate back to mapped app")
    }

    private fun showProjectionPromptNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createProjectionPromptNotificationChannel()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_GRANT_PROJECTION_FOR_APP_PROFILE, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            PROJECTION_PROMPT_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PROJECTION_PROMPT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_foreground))
            .setContentTitle("Screen capture permission needed")
            .setContentText("Tap to grant permission so Bifrost can run the assigned animation.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(PROJECTION_PROMPT_NOTIFICATION_ID, notification)
    }

    private fun dismissProjectionPromptNotification() {
        NotificationManagerCompat.from(this).cancel(PROJECTION_PROMPT_NOTIFICATION_ID)
    }

    private fun createProjectionPromptNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PROJECTION_PROMPT_CHANNEL_ID,
            "App profile permission prompt",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
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
                val projection = synchronized(mediaProjectionLock) { mediaProjection } ?: return null
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
                val projection = synchronized(mediaProjectionLock) { mediaProjection } ?: return null
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
                val projection = synchronized(mediaProjectionLock) { mediaProjection } ?: return null
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
                currentFlashWhenReady,
                currentBatteryLowColorOverride,
                currentBatteryMidColorOverride,
                currentBatteryHighColorOverride
            )
            LedAnimationType.CPU_TEMPERATURE -> CpuTemperatureAnimation(
                ledController,
                coolColorOverride = currentCpuCoolColorOverride,
                warmColorOverride = currentCpuWarmColorOverride,
                hotColorOverride = currentCpuHotColorOverride
            )
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