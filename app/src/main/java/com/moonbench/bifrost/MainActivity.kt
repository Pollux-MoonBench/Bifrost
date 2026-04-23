package com.moonbench.bifrost

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.Manifest
import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Display
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.external.ExternalApiGate
import com.moonbench.bifrost.services.AppProfileManager
import com.moonbench.bifrost.services.HeimdallStartupManager
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.LiveWallpaperSettingsManager
import com.moonbench.bifrost.services.ServiceController
import com.moonbench.bifrost.services.VideoLiveWallpaperService
import com.moonbench.bifrost.tools.DeviceInfo
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.ui.AnimatedRainbowDrawable
import com.moonbench.bifrost.ui.BifrostAlertDialog
import com.moonbench.bifrost.ui.ColorPickerDialog
import com.moonbench.bifrost.ui.LockableHorizontalScrollView
import com.moonbench.bifrost.ui.RagnarokWarningDialog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var serviceToggle: SwitchMaterial
    private lateinit var autoStartupSwitch: SwitchMaterial
    private lateinit var pluggedBatteryOverrideSwitch: SwitchMaterial
    private lateinit var persistentNotificationSwitch: SwitchMaterial
    private lateinit var adaptiveBrightnessSwitch: SwitchMaterial
    private lateinit var externalApiSwitch: SwitchMaterial
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var animationSpinner: Spinner
    private lateinit var profileSpinner: Spinner
    private lateinit var presetSpinner: Spinner
    private lateinit var themeSpinner: Spinner
    private lateinit var exportThemeButton: MaterialButton
    private lateinit var importThemeButton: MaterialButton
    private lateinit var savePresetButton: MaterialButton
    private lateinit var modifyPresetButton: MaterialButton
    private lateinit var deletePresetButton: MaterialButton
    private lateinit var customizePresetArtworkButton: MaterialButton
    private lateinit var exportPresetsButton: MaterialButton
    private lateinit var importPresetsButton: MaterialButton
    private lateinit var colorButton: MaterialButton
    private lateinit var rightColorButton: MaterialButton
    private lateinit var batteryLowColorButton: MaterialButton
    private lateinit var batteryMidColorButton: MaterialButton
    private lateinit var batteryHighColorButton: MaterialButton
    private lateinit var cpuCoolColorButton: MaterialButton
    private lateinit var cpuWarmColorButton: MaterialButton
    private lateinit var cpuHotColorButton: MaterialButton
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var speedSeekBar: SeekBar
    private lateinit var smoothnessSeekBar: SeekBar
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var saturationBoostSeekBar: SeekBar
    private lateinit var customSamplingSwitch: SwitchMaterial
    private lateinit var singleColorSwitch: SwitchMaterial
    private lateinit var breatheWhenChargingSwitch: SwitchMaterial
    private lateinit var chargingSpeedIndicatorSwitch: SwitchMaterial
    private lateinit var flashWhenReadySwitch: SwitchMaterial
    private lateinit var appProfileSwitch: SwitchMaterial
    private lateinit var homeAppProfileSwitch: SwitchMaterial
    private lateinit var appProfileDefaultSwitch: SwitchMaterial
    private lateinit var coloredLogoSwitch: SwitchMaterial
    private lateinit var assignAppButton: MaterialButton
    private lateinit var manageAppsButton: MaterialButton
    private lateinit var settingsOverlay: View
    private lateinit var homeContainer: View
    private lateinit var homeSettingsButton: MaterialButton
    private lateinit var closeSettingsButton: MaterialButton
    private lateinit var tabUiSettings: MaterialButton
    private lateinit var tabBehaviorSettings: MaterialButton
    private lateinit var tabThemesSettings: MaterialButton
    private lateinit var liveWallpaperTabButton: MaterialButton
    private lateinit var mainSettingsScroll: ScrollView
    private lateinit var liveWallpaperSettingsScroll: ScrollView
    private lateinit var liveWallpaperVideoPathText: TextView
    private lateinit var liveWallpaperPickVideoButton: MaterialButton
    private lateinit var liveWallpaperApplyButton: MaterialButton
    private lateinit var liveWallpaperRemoveButton: MaterialButton
    private lateinit var liveWallpaperPerformanceSpinner: Spinner
    private lateinit var liveWallpaperFpsSeekBar: SeekBar
    private lateinit var liveWallpaperFpsValueText: TextView
    private lateinit var presetCoverFlowScroll: LockableHorizontalScrollView
    private lateinit var presetCoverFlowContainer: LinearLayout
    private lateinit var activePresetInfoCard: MaterialCardView
    private lateinit var activePresetNameText: TextView
    private lateinit var activePresetStatusBadge: TextView
    private lateinit var activePresetAnimationText: TextView
    private lateinit var activePresetProfileText: TextView
    private lateinit var modeCard: MaterialCardView
    private lateinit var appProfileCard: MaterialCardView
    private lateinit var colorCard: MaterialCardView
    private lateinit var animationCard: MaterialCardView
    private lateinit var performanceCard: MaterialCardView
    private lateinit var themesCard: MaterialCardView
    private lateinit var settingsSystemStatusCard: MaterialCardView
    private lateinit var systemStatusContainer: View
    private lateinit var bifrostLogoView: ImageView
    private lateinit var bifrostTitleText: TextView
    private var thorLaunchBottomSwitch: SwitchMaterial? = null
    private var thorAmbientBottomSwitch: SwitchMaterial? = null

    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }

    private enum class SettingsTab {
        UI,
        BEHAVIOR,
        THEMES,
        LIVE_WALLPAPER
    }

    private data class HeaderThemePalette(
        val introHueStart: Float,
        val introHueSpan: Float,
        val introSaturation: Float,
        val introValue: Float,
        val settleHueStart: Float,
        val settleHueSpan: Float,
        val settleHueWobbleAmplitude: Float,
        val settleSaturationBase: Float,
        val settleSaturationWave: Float,
        val settleValueBase: Float,
        val settleValueWave: Float,
        val settleAlphaBase: Int,
        val settleAlphaWave: Int,
        val settlePhaseDegrees: Float
    )

    private data class UiTheme(
        val id: String,
        val label: String,
        val backgroundColor: Int,
        val cardColor: Int,
        val surfaceColor: Int,
        val textColor: Int,
        val accentColor: Int,
        val accentLightColor: Int,
        val headerPalette: HeaderThemePalette
    )

    companion object {
        var mediaProjectionResultCode: Int? = null
        var mediaProjectionData: Intent? = null
        private const val DEBOUNCE_DELAY = 500L
        private const val SERVICE_RESTART_DELAY = 400L
        private const val SETTINGS_OPEN_DURATION_MS = 300L
        private const val SETTINGS_CLOSE_DURATION_MS = 210L
        private const val SETTINGS_HOME_DIM_ALPHA = 0.84f
        private const val COVER_FLOW_TILE_SIZE_DP = 176
        private const val COVER_FLOW_TILE_GAP_DP = 10
        private const val COVER_FLOW_CREATE_TAG = -1
        private const val COVER_FLOW_SNAP_SETTLE_DELAY_MS = 100L
        private const val APP_PROFILE_SYNC_INTERVAL_MS = 1200L
        private const val PREF_KEY_LAST_PRESET = "last_preset_name"
        private const val PREF_APP_PROFILE_INFO_SHOWN = "app_profile_info_shown"

        // Note: this key is shared with PresetController for backward compatibility.
        // Do not change unless updating persistence logic across components.

        private const val TITLE_INTRO_ANIMATION_MS = 3200L
        private const val PREF_FIRST_LAUNCH_ALERT_SHOWN = "first_launch_alert_shown"
        private const val PREF_THOR_BOTTOM_SCREEN = "thor_bottom_screen"
        private const val PREF_THOR_AMBIENT_BOTTOM_SCREEN = "thor_ambient_bottom_screen"
        private const val PREF_BATTERY_OVERRIDE_WHEN_PLUGGED = "battery_override_when_plugged"
        private const val PREF_PERSISTENT_NOTIFICATION = "persistent_notification_enabled"
        private const val PREF_ADAPTIVE_BRIGHTNESS = "adaptive_brightness_enabled"
        private const val PREF_SELECTED_UI_THEME = "selected_ui_theme"
        private const val PREF_COLORED_LOGO_ENABLED = "colored_logo_enabled"
        private const val PREF_STARTUP_GUIDE_DONE = "startup_guide_done"
        private const val PREF_LIVE_WALLPAPER_RESTORE_SETTINGS = "live_wallpaper_restore_settings"
        private const val PREF_LIVE_WALLPAPER_APPLY_IN_PROGRESS = "live_wallpaper_apply_in_progress"
        private const val LIVE_WALLPAPER_STATE_SYNC_MAX_RETRIES = 12
        private const val LIVE_WALLPAPER_STATE_SYNC_DELAY_MS = 250L
        private const val AUTOSTART_PERMISSION_CHANNEL_ID = "bifrost_autostart_permission"
        private const val AUTOSTART_PERMISSION_NOTIFICATION_ID = 4245
        private const val EXTRA_DISPLAY_RELAUNCH_ATTEMPT = "display_relaunch_attempt"
        private const val MAX_DISPLAY_RELAUNCH_ATTEMPTS = 3
        private const val COLOR_OVERRIDE_UNSET = Int.MIN_VALUE
        private const val EXTRA_BATTERY_LOW_COLOR_OVERRIDE = "batteryLowColorOverride"
        private const val EXTRA_BATTERY_MID_COLOR_OVERRIDE = "batteryMidColorOverride"
        private const val EXTRA_BATTERY_HIGH_COLOR_OVERRIDE = "batteryHighColorOverride"
        private const val EXTRA_CPU_COOL_COLOR_OVERRIDE = "cpuCoolColorOverride"
        private const val EXTRA_CPU_WARM_COLOR_OVERRIDE = "cpuWarmColorOverride"
        private const val EXTRA_CPU_HOT_COLOR_OVERRIDE = "cpuHotColorOverride"

        private val DEFAULT_BATTERY_LOW_COLOR = Color.rgb(255, 0, 0)
        private val DEFAULT_BATTERY_MID_COLOR = Color.rgb(255, 255, 0)
        private val DEFAULT_BATTERY_HIGH_COLOR = Color.rgb(0, 255, 0)
        private val DEFAULT_CPU_COOL_COLOR = Color.rgb(0, 120, 255)
        private val DEFAULT_CPU_WARM_COLOR = Color.rgb(255, 215, 0)
        private val DEFAULT_CPU_HOT_COLOR = Color.rgb(255, 0, 0)

        const val EXTRA_GRANT_PROJECTION_FOR_APP_PROFILE = "grant_projection_for_app_profile"
    }

    private var selectedAnimationType: LedAnimationType = LedAnimationType.AMBIENT
    private var selectedProfile: PerformanceProfile = PerformanceProfile.HIGH
    private var selectedColor: Int = Color.WHITE
    private var selectedRightColor: Int = Color.WHITE
    private var selectedBatteryLowColorOverride: Int? = null
    private var selectedBatteryMidColorOverride: Int? = null
    private var selectedBatteryHighColorOverride: Int? = null
    private var selectedCpuCoolColorOverride: Int? = null
    private var selectedCpuWarmColorOverride: Int? = null
    private var selectedCpuHotColorOverride: Int? = null
    private var selectedBrightness: Int = 255
    private var selectedSpeed: Float = 0.5f
    private var selectedSmoothness: Float = 0.5f
    private var selectedSensitivity: Float = 0.5f
    private var selectedSaturationBoost: Float = 0.0f
    private var selectedUseCustomSampling: Boolean = false
    private var selectedUseSingleColor: Boolean = false
    private var selectedBreatheWhenCharging: Boolean = false
    private var selectedIndicateChargingSpeed: Boolean = false
    private var selectedFlashWhenReady: Boolean = false
    private var selectedBatteryOverrideWhenPlugged: Boolean = false
    private var selectedPersistentNotification: Boolean = true
    private var selectedAdaptiveBrightness: Boolean = false
    private var isAwaitingPermissionResult = false
    private var isUpdatingFromPreset = false
    private var isGrantingProjectionForAppProfile = false
    private var rainbowDrawable: AnimatedRainbowDrawable? = null
    private var titleIntroAnimator: ValueAnimator? = null
    private var headerSettleAnimator: ValueAnimator? = null
    private var isAppInitialized = false
    private var bifrostTitleLabel: String = ""
    private var selectedCoverFlowIndex: Int = 0
    private var coverFlowSnapRunnable: Runnable? = null
    private var suppressNextCoverFlowSnap: Boolean = false
    private var isCoverFlowDragging: Boolean = false
    private var isCoverFlowTouching: Boolean = false
    private var lastCoverFlowScrollXForSnap: Int = 0
    private var isSettingsOverlayAnimating: Boolean = false
    private var currentSettingsTab: SettingsTab = SettingsTab.UI
    private var isColoredLogoEnabled: Boolean = false
    private var isSyncingAppProfileSwitches: Boolean = false
    private var isSyncingAppProfileDefaultSwitch: Boolean = false
    private var pendingPresetArtworkIndex: Int? = null
    private var presetArtworkSheetDialog: BottomSheetDialog? = null
    private var appProfileSyncRunnable: Runnable? = null
    private var resumeStateSyncRunnable: Runnable? = null
    private var liveWallpaperApplyStateSyncRunnable: Runnable? = null
    private var pendingBackupExportOptions: BackupArchiveTransfer.CategoryOptions? = null
    private var pendingBackupImportOptions: BackupArchiveTransfer.CategoryOptions? = null
    private val liveWallpaperPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "live_wallpaper_is_applied") return@OnSharedPreferenceChangeListener
        if (!isAppInitialized) return@OnSharedPreferenceChangeListener
        runOnUiThread {
            refreshLiveWallpaperActionButtons()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var presetController: PresetController
    private lateinit var serviceController: ServiceController
    private val colorPickerDialog = ColorPickerDialog()
    private val ragnarokWarningDialog = RagnarokWarningDialog()
    private lateinit var appProfileManager: AppProfileManager
    private val builtInThemes = listOf(
        UiTheme(
            id = "classic",
            label = "Classic",
            backgroundColor = Color.parseColor("#0A0E1A"),
            cardColor = Color.parseColor("#12182B"),
            surfaceColor = Color.parseColor("#1A2236"),
            textColor = Color.parseColor("#E8EEFF"),
            accentColor = Color.parseColor("#6366F1"),
            accentLightColor = Color.parseColor("#818CF8"),
            headerPalette = HeaderThemePalette(
                introHueStart = 0f,
                introHueSpan = 360f,
                introSaturation = 0.82f,
                introValue = 1f,
                settleHueStart = 18f,
                settleHueSpan = 300f,
                settleHueWobbleAmplitude = 10f,
                settleSaturationBase = 0.28f,
                settleSaturationWave = 0.14f,
                settleValueBase = 0.92f,
                settleValueWave = 0.08f,
                settleAlphaBase = 224,
                settleAlphaWave = 31,
                settlePhaseDegrees = 18f
            )
        ),
        UiTheme(
            id = "oled_purple",
            label = "OLED Purple",
            backgroundColor = Color.parseColor("#000000"),
            cardColor = Color.parseColor("#000000"),
            surfaceColor = Color.parseColor("#000000"),
            textColor = Color.parseColor("#C084FC"),
            accentColor = Color.parseColor("#A855F7"),
            accentLightColor = Color.parseColor("#C084FC"),
            headerPalette = HeaderThemePalette(
                introHueStart = 264f,
                introHueSpan = 64f,
                introSaturation = 0.9f,
                introValue = 0.98f,
                settleHueStart = 272f,
                settleHueSpan = 48f,
                settleHueWobbleAmplitude = 5f,
                settleSaturationBase = 0.5f,
                settleSaturationWave = 0.16f,
                settleValueBase = 0.76f,
                settleValueWave = 0.2f,
                settleAlphaBase = 236,
                settleAlphaWave = 19,
                settlePhaseDegrees = 12f
            )
        )
    )
    private var selectedUiTheme: UiTheme = builtInThemes.first()

    private val launchNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Notification permission is required for this app to function",
                    Toast.LENGTH_LONG
                ).show()
            }
            initializeApp()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (selectedAnimationType.needsMediaProjection) {
                    if (mediaProjectionResultCode != null && mediaProjectionData != null) {
                        serviceController.startDebounced { createLedServiceIntent() }
                    } else {
                        requestScreenCapturePermission()
                    }
                } else {
                    serviceController.startDebounced { createLedServiceIntent() }
                }
            } else {
                isAwaitingPermissionResult = false
                serviceToggle.isChecked = false
                Toast.makeText(
                    this,
                    "Notification permission required for Foreground Service",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                mediaProjectionResultCode = result.resultCode
                mediaProjectionData = result.data

                if (isGrantingProjectionForAppProfile) {
                    isGrantingProjectionForAppProfile = false
                    // Supply projection to the running service without restarting it.
                    // The correct preset will be applied when the user returns to the
                    // mapped app (the periodic check will resolve it automatically).
                    if (LEDService.isRunning) {
                        val supplyIntent = Intent(this, LEDService::class.java).apply {
                            action = LEDService.ACTION_SUPPLY_PROJECTION
                            putExtra("resultCode", mediaProjectionResultCode)
                            putExtra("data", mediaProjectionData)
                        }
                        startService(supplyIntent)
                    }
                } else {
                    serviceController.startDebounced { createLedServiceIntent() }
                }
            } else {
                val wasAppProfileGrant = isGrantingProjectionForAppProfile
                isGrantingProjectionForAppProfile = false
                if (!wasAppProfileGrant) {
                    isAwaitingPermissionResult = false
                    serviceToggle.isChecked = false
                }
                Toast.makeText(
                    this,
                    "Screen capture permission required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val presetImagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            val targetIndex = pendingPresetArtworkIndex
            pendingPresetArtworkIndex = null

            if (uri == null || targetIndex == null) return@registerForActivityResult

            val storedFileName = runCatching {
                PresetImageStorage.copyPickedImage(this, uri)
            }.getOrNull()

            if (storedFileName == null) {
                Toast.makeText(this, "Couldn't import that image", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val updatedPreset = presetController.updatePresetVisual(targetIndex) { preset ->
                preset.copy(
                    customEmoji = null,
                    customImageFileName = storedFileName,
                    appIconPackageName = null
                )
            }

            if (updatedPreset == null) {
                PresetImageStorage.deleteIfExists(this, storedFileName)
                Toast.makeText(this, "That preset is no longer available", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            refreshCoverFlowFromPresets()
            Toast.makeText(this, "${updatedPreset.name} image updated", Toast.LENGTH_SHORT).show()
        }

    private val exportPresetsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val options = pendingBackupExportOptions ?: BackupArchiveTransfer.CategoryOptions()
            pendingBackupExportOptions = null
            exportPresetBundle(uri, options)
        }

    private val exportThemeLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            exportThemeBundle(uri)
        }

    private val importPresetsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val options = pendingBackupImportOptions ?: BackupArchiveTransfer.CategoryOptions()
            pendingBackupImportOptions = null
            importPresetBundle(uri, options)
        }

    private val importThemeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importThemeBundle(uri)
        }

    private val liveWallpaperVideoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            LiveWallpaperSettingsManager.setVideoUri(prefs, uri)
            refreshLiveWallpaperVideoSummary()
            Toast.makeText(this, "Live wallpaper video updated", Toast.LENGTH_SHORT).show()
        }

    private val liveWallpaperApplyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            scheduleLiveWallpaperApplyStateSync(initialDelayMs = 180L)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra("finish", false)) {
            finishAffinity()
            return
        }

        if (shouldLaunchStartupGuide()) {
            startActivity(Intent(this, StartupGuideActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        setupStatusBar()

        if (maybeRelaunchOnCorrectDisplay()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launchNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        initializeApp()
    }

    private fun shouldLaunchStartupGuide(): Boolean {
        return !prefs.getBoolean(PREF_STARTUP_GUIDE_DONE, false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAppProfileProjectionIntent(intent)
    }

    private fun handleAppProfileProjectionIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_GRANT_PROJECTION_FOR_APP_PROFILE, false) != true) return
        // Consume the flag so it doesn't re-trigger on configuration change.
        intent.removeExtra(EXTRA_GRANT_PROJECTION_FOR_APP_PROFILE)

        if (!LEDService.isRunning) return
        if (!::mediaProjectionManager.isInitialized) return

        isGrantingProjectionForAppProfile = true
        requestScreenCapturePermission()
    }

    private fun initializeApp() {
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        prefs.registerOnSharedPreferenceChangeListener(liveWallpaperPrefsListener)

        settingsOverlay = findViewById(R.id.settingsOverlay)
        homeContainer = findViewById(R.id.homeContainer)
        homeSettingsButton = findViewById(R.id.homeSettingsButton)
        closeSettingsButton = findViewById(R.id.closeSettingsButton)
        tabUiSettings = findViewById(R.id.tabUiSettings)
        tabBehaviorSettings = findViewById(R.id.tabBehaviorSettings)
        tabThemesSettings = findViewById(R.id.tabThemesSettings)
        liveWallpaperTabButton = findViewById(R.id.liveWallpaperTabButton)
        mainSettingsScroll = findViewById(R.id.mainSettingsScroll)
        liveWallpaperSettingsScroll = findViewById(R.id.liveWallpaperSettingsScroll)
        liveWallpaperVideoPathText = findViewById(R.id.liveWallpaperVideoPathText)
        liveWallpaperPickVideoButton = findViewById(R.id.liveWallpaperPickVideoButton)
        liveWallpaperApplyButton = findViewById(R.id.liveWallpaperApplyButton)
        liveWallpaperRemoveButton = findViewById(R.id.liveWallpaperRemoveButton)
        liveWallpaperPerformanceSpinner = findViewById(R.id.liveWallpaperPerformanceSpinner)
        liveWallpaperFpsSeekBar = findViewById(R.id.liveWallpaperFpsSeekBar)
        liveWallpaperFpsValueText = findViewById(R.id.liveWallpaperFpsValueText)
        presetCoverFlowScroll = findViewById(R.id.presetCoverFlowScroll)
        presetCoverFlowContainer = findViewById(R.id.presetCoverFlowContainer)
        activePresetInfoCard = findViewById(R.id.activePresetInfoCard)
        activePresetNameText = findViewById(R.id.activePresetNameText)
        activePresetStatusBadge = findViewById(R.id.activePresetStatusBadge)
        activePresetAnimationText = findViewById(R.id.activePresetAnimationText)
        activePresetProfileText = findViewById(R.id.activePresetProfileText)

        serviceToggle = findViewById(R.id.serviceToggle)
        autoStartupSwitch = findViewById(R.id.autoStartupSwitch)
        pluggedBatteryOverrideSwitch = findViewById(R.id.pluggedBatteryOverrideSwitch)
        persistentNotificationSwitch = findViewById(R.id.persistentNotificationSwitch)
        adaptiveBrightnessSwitch = findViewById(R.id.adaptiveBrightnessSwitch)
        externalApiSwitch = findViewById(R.id.externalApiSwitch)
        animationSpinner = findViewById(R.id.animationSpinner)
        profileSpinner = findViewById(R.id.profileSpinner)
        presetSpinner = findViewById(R.id.presetSpinner)
        themeSpinner = findViewById(R.id.themeSpinner)
        exportThemeButton = findViewById(R.id.exportThemeButton)
        importThemeButton = findViewById(R.id.importThemeButton)
        savePresetButton = findViewById(R.id.savePresetButton)
        modifyPresetButton = findViewById(R.id.modifyPresetButton)
        deletePresetButton = findViewById(R.id.deletePresetButton)
        customizePresetArtworkButton = findViewById(R.id.customizePresetArtworkButton)
        exportPresetsButton = findViewById(R.id.exportPresetsButton)
        importPresetsButton = findViewById(R.id.importPresetsButton)
        colorButton = findViewById(R.id.colorButton)
        rightColorButton = findViewById(R.id.rightColorButton)
        batteryLowColorButton = findViewById(R.id.batteryLowColorButton)
        batteryMidColorButton = findViewById(R.id.batteryMidColorButton)
        batteryHighColorButton = findViewById(R.id.batteryHighColorButton)
        cpuCoolColorButton = findViewById(R.id.cpuCoolColorButton)
        cpuWarmColorButton = findViewById(R.id.cpuWarmColorButton)
        cpuHotColorButton = findViewById(R.id.cpuHotColorButton)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        smoothnessSeekBar = findViewById(R.id.smoothnessSeekBar)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        saturationBoostSeekBar = findViewById(R.id.saturationBoostSeekBar)
        customSamplingSwitch = findViewById(R.id.customSamplingSwitch)
        singleColorSwitch = findViewById(R.id.singleColorSwitch)
        breatheWhenChargingSwitch = findViewById(R.id.breatheWhenChargingSwitch)
        chargingSpeedIndicatorSwitch = findViewById(R.id.chargingSpeedIndicatorSwitch)
        flashWhenReadySwitch = findViewById(R.id.flashWhenReadySwitch)
        appProfileSwitch = findViewById(R.id.appProfileSwitch)
        coloredLogoSwitch = findViewById(R.id.coloredLogoSwitch)
        homeAppProfileSwitch = findViewById(R.id.homeAppProfileSwitch)
        val appProfileDefaultSwitchId = resources.getIdentifier(
            "appProfileDefaultSwitch",
            "id",
            packageName
        )
        check(appProfileDefaultSwitchId != 0) { "Missing appProfileDefaultSwitch id" }
        appProfileDefaultSwitch = findViewById(appProfileDefaultSwitchId)
        assignAppButton = findViewById(R.id.assignAppButton)
        manageAppsButton = findViewById(R.id.manageAppsButton)
        modeCard = findViewById(R.id.modeCard)
        appProfileCard = findViewById(R.id.appProfileCard)
        colorCard = findViewById(R.id.colorCard)
        animationCard = findViewById(R.id.animationCard)
        performanceCard = findViewById(R.id.performanceCard)
        themesCard = findViewById(R.id.themesCard)
        settingsSystemStatusCard = findViewById(R.id.settingsSystemStatusCard)
        systemStatusContainer = findViewById(R.id.systemStatusContainer)
        bifrostLogoView = findViewById(R.id.homeBifrostLogoView)
        bifrostTitleText = findViewById(R.id.homeBifrostTitleText)
        bifrostTitleLabel = bifrostTitleText.text.toString()

        serviceController = ServiceController(
            activity = this,
            handler = mainHandler,
            debounceDelay = DEBOUNCE_DELAY,
            restartDelay = SERVICE_RESTART_DELAY
        )

        serviceController.onNeedsMediaProjectionCheck = {
            handleMediaProjectionRequirement()
        }

        setupHomeSurface()
        setupBackNavigationHandler()
        setupAnimationSpinner()
        setupProfileSpinner()
        setupColorButton()
        setupBrightnessSeekBar()
        setupSpeedSeekBar()
        setupSmoothnessSeekBar()
        setupSensitivitySeekBar()
        setupSaturationBoostSeekBar()
        setupCustomSamplingSwitch()
        setupSingleColorSwitch()
        setupBreatheWhenChargingSwitch()
        setupChargingSpeedIndicatorSwitch()
        setupFlashWhenReadySwitch()
        appProfileManager = AppProfileManager(prefs)
        setupAppProfileFeature()
        setupAutoStartupSwitch()
        setupPluggedBatteryOverrideSwitch()
        setupPersistentNotificationSwitch()
        setupAdaptiveBrightnessSwitch()
        setupExternalApiSwitch()
        setupThorScreenPreference()
        setupSettingsTabs()
        setupThemeFeature()
        setupLiveWallpaperFeature()
        setupColoredLogoSwitch()
        setupRainbowTitleText()
        setupPresetFeature()
        updateParameterVisibility()
        enableRainbowBackground(LEDService.isRunning)
        showFirstLaunchAlertIfNeeded()

        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            if (serviceController.isServiceTransitioning) return@setOnCheckedChangeListener

            serviceController.cancelPendingOperations()
            isAwaitingPermissionResult = isChecked
            enableRainbowBackground(isChecked)

            if (isChecked) {
                if (!LEDService.isRunning) {
                    playBifrostHeaderAnimation()
                }
                handleStartWithCurrentSelection()
            } else {
                serviceController.stopDebounced()
            }
        }

        maybeAutoStartHeimdallOnLaunch()

        isAppInitialized = true

        // Handle the case where the activity was freshly created from a projection-prompt
        // notification tap (service is running but activity wasn't alive).
        handleAppProfileProjectionIntent(intent)
    }

    private fun setupBackNavigationHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::settingsOverlay.isInitialized && settingsOverlay.visibility == View.VISIBLE) {
                        requestCloseSettingsOverlay()
                        return
                    }

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    private fun setupHomeSurface() {
        homeSettingsButton.setOnClickListener { openSettingsOverlay() }
        closeSettingsButton.setOnClickListener { requestCloseSettingsOverlay() }
        customizePresetArtworkButton.setOnClickListener {
            openSelectedPresetArtworkEditor(it)
        }

        presetCoverFlowScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isCoverFlowTouching = true
                    cancelPendingCoverFlowSnap()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isCoverFlowTouching = false
                    scheduleCoverFlowSnap()
                }
            }
            false
        }

        presetCoverFlowScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            updateCoverFlowCardTransforms()
            if (!::appProfileManager.isInitialized || !appProfileManager.isEnabled) {
                if (!isCoverFlowTouching) {
                    scheduleCoverFlowSnap()
                }
            }
        }
    }

    private fun setupSettingsTabs() {
        tabUiSettings.setOnClickListener { setSettingsTab(SettingsTab.UI) }
        tabBehaviorSettings.setOnClickListener { setSettingsTab(SettingsTab.BEHAVIOR) }
        tabThemesSettings.setOnClickListener { setSettingsTab(SettingsTab.THEMES) }
        liveWallpaperTabButton.setOnClickListener { setSettingsTab(SettingsTab.LIVE_WALLPAPER) }
        setSettingsTab(currentSettingsTab)
    }

    private fun setSettingsTab(tab: SettingsTab) {
        currentSettingsTab = tab
        val showingUi = tab == SettingsTab.UI
        val showingBehavior = tab == SettingsTab.BEHAVIOR
        val showingThemes = tab == SettingsTab.THEMES
        val showingLiveWallpaper = tab == SettingsTab.LIVE_WALLPAPER

        modeCard.visibility = if (showingUi) View.VISIBLE else View.GONE
        colorCard.visibility = if (showingUi) View.VISIBLE else View.GONE
        animationCard.visibility = if (showingUi) View.VISIBLE else View.GONE
        performanceCard.visibility = if (showingUi) View.VISIBLE else View.GONE

        settingsSystemStatusCard.visibility = if (showingBehavior) View.VISIBLE else View.GONE
        appProfileCard.visibility = if (showingBehavior) View.VISIBLE else View.GONE

        val thorCard = findViewById<View>(R.id.thorSettingsCard)
        thorCard?.visibility = if (showingBehavior && DeviceInfo.isAynThor) View.VISIBLE else View.GONE

        themesCard.visibility = if (showingThemes) View.VISIBLE else View.GONE

        mainSettingsScroll.visibility = if (showingLiveWallpaper) View.GONE else View.VISIBLE
        liveWallpaperSettingsScroll.visibility = if (showingLiveWallpaper) View.VISIBLE else View.GONE
        if (showingLiveWallpaper) {
            refreshLiveWallpaperActionButtons()
        }

        updateSettingsTabButtonStyles()
    }

    private fun updateSettingsTabButtonStyles() {
        updateSettingsTabButtonState(tabUiSettings, currentSettingsTab == SettingsTab.UI)
        updateSettingsTabButtonState(tabBehaviorSettings, currentSettingsTab == SettingsTab.BEHAVIOR)
        updateSettingsTabButtonState(tabThemesSettings, currentSettingsTab == SettingsTab.THEMES)
        updateSettingsTabButtonState(liveWallpaperTabButton, currentSettingsTab == SettingsTab.LIVE_WALLPAPER)
    }

    private fun updateSettingsTabButtonState(button: MaterialButton, selected: Boolean) {
        val selectedTint = ColorStateList.valueOf(selectedUiTheme.accentColor)
        val unselectedTint = ColorStateList.valueOf(selectedUiTheme.surfaceColor)
        button.backgroundTintList = if (selected) selectedTint else unselectedTint
        button.setTextColor(if (selected) Color.WHITE else selectedUiTheme.textColor)
        button.strokeColor = ColorStateList.valueOf(selectedUiTheme.accentLightColor)
    }

    private fun setupThemeFeature() {
        val adapter = ArrayAdapter(this, R.layout.item_spinner_bifrost, builtInThemes.map { it.label })
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_bifrost)
        themeSpinner.adapter = adapter

        val selectedThemeId = prefs.getString(PREF_SELECTED_UI_THEME, builtInThemes.first().id)
        val selectedIndex = builtInThemes.indexOfFirst { it.id == selectedThemeId }.takeIf { it >= 0 } ?: 0
        selectedUiTheme = builtInThemes[selectedIndex]
        themeSpinner.setSelection(selectedIndex, false)
        applySelectedTheme(selectedUiTheme, persistSelection = false)

        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val theme = builtInThemes.getOrNull(position) ?: return
                applySelectedTheme(theme, persistSelection = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applySelectedTheme(theme: UiTheme, persistSelection: Boolean) {
        selectedUiTheme = theme
        if (persistSelection) {
            prefs.edit().putString(PREF_SELECTED_UI_THEME, theme.id).apply()
        }
        applyUiTheme(theme)
    }

    private fun applyUiTheme(theme: UiTheme) {
        setupStatusBar()
        findViewById<View>(android.R.id.content)?.setBackgroundColor(theme.backgroundColor)
        homeContainer.setBackgroundColor(theme.backgroundColor)
        settingsOverlay.setBackgroundColor(theme.backgroundColor)
        applyThemeToViewTree(homeContainer, theme)
        applyThemeToViewTree(settingsOverlay, theme)
        applyHeaderLogoPreference()
        updateSettingsTabButtonStyles()
    }

    private fun applyThemeToViewTree(view: View, theme: UiTheme) {
        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(theme.cardColor)
                view.strokeColor = theme.accentColor
            }

            is MaterialButton -> {
                view.backgroundTintList = ColorStateList.valueOf(theme.surfaceColor)
                view.setTextColor(theme.textColor)
                view.iconTint = ColorStateList.valueOf(theme.textColor)
                view.strokeColor = ColorStateList.valueOf(theme.accentColor)
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyThemeToViewTree(view.getChildAt(index), theme)
            }
        }
    }

    private fun setupLiveWallpaperFeature() {
        liveWallpaperPickVideoButton.setOnClickListener {
            liveWallpaperVideoPickerLauncher.launch(arrayOf("video/*"))
        }

        liveWallpaperApplyButton.setOnClickListener {
            openLiveWallpaperChooser(requireVideo = true)
        }

        liveWallpaperRemoveButton.setOnClickListener {
            removeLiveWallpaper()
        }

        val modes = LiveWallpaperSettingsManager.PerformanceMode.values().toList()
        val modeLabels = modes.map { it.name.replace("_", " ") }
        val adapter = ArrayAdapter(this, R.layout.item_spinner_bifrost, modeLabels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_bifrost)
        liveWallpaperPerformanceSpinner.adapter = adapter

        val selectedMode = LiveWallpaperSettingsManager.getPerformanceMode(prefs)
        liveWallpaperPerformanceSpinner.setSelection(modes.indexOf(selectedMode).coerceAtLeast(0), false)
        liveWallpaperPerformanceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = modes.getOrNull(position) ?: return
                LiveWallpaperSettingsManager.setPerformanceMode(prefs, mode)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        liveWallpaperFpsSeekBar.max = 105
        val currentFps = LiveWallpaperSettingsManager.getTargetFps(prefs)
        liveWallpaperFpsSeekBar.progress = currentFps - 15
        liveWallpaperFpsValueText.text = currentFps.toString()
        liveWallpaperFpsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fps = progress + 15
                liveWallpaperFpsValueText.text = fps.toString()
                if (fromUser) {
                    LiveWallpaperSettingsManager.setTargetFps(prefs, fps)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        refreshLiveWallpaperVideoSummary()
        refreshLiveWallpaperActionButtons()
    }

    private fun syncLiveWallpaperAppliedState(): Boolean {
        val fromSystem = isBifrostLiveWallpaperActive()
        if (fromSystem) {
            LiveWallpaperSettingsManager.setWallpaperApplied(prefs, true)
            return true
        }
        return LiveWallpaperSettingsManager.isWallpaperApplied(prefs)
    }

    private fun scheduleLiveWallpaperApplyStateSync(initialDelayMs: Long = 120L) {
        liveWallpaperApplyStateSyncRunnable?.let(mainHandler::removeCallbacks)
        var attempts = 0
        val runnable = object : Runnable {
            override fun run() {
                val isApplied = syncLiveWallpaperAppliedState()
                refreshLiveWallpaperActionButtons()

                if (isApplied || attempts >= LIVE_WALLPAPER_STATE_SYNC_MAX_RETRIES) {
                    prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_APPLY_IN_PROGRESS, false).apply()
                    return
                }

                attempts += 1
                mainHandler.postDelayed(this, LIVE_WALLPAPER_STATE_SYNC_DELAY_MS)
            }
        }
        liveWallpaperApplyStateSyncRunnable = runnable
        mainHandler.postDelayed(runnable, initialDelayMs)
    }

    private fun refreshLiveWallpaperActionButtons() {
        val isWallpaperApplied = LiveWallpaperSettingsManager.isWallpaperApplied(prefs) || syncLiveWallpaperAppliedState()
        liveWallpaperPickVideoButton.visibility = if (isWallpaperApplied) View.GONE else View.VISIBLE
        liveWallpaperApplyButton.visibility = if (isWallpaperApplied) View.GONE else View.VISIBLE
        liveWallpaperRemoveButton.visibility = if (isWallpaperApplied) View.VISIBLE else View.GONE
    }

    private fun refreshLiveWallpaperVideoSummary() {
        val uri = LiveWallpaperSettingsManager.getVideoUri(prefs)
        liveWallpaperVideoPathText.text = if (uri == null) {
            "No video selected"
        } else {
            uri.lastPathSegment ?: uri.toString()
        }
    }

    private fun removeLiveWallpaper() {
        val wallpaperManager = WallpaperManager.getInstance(this)
        val removed = runCatching {
            wallpaperManager.clear(WallpaperManager.FLAG_SYSTEM)
        }.isSuccess || runCatching {
            wallpaperManager.clear()
        }.isSuccess

        LiveWallpaperSettingsManager.clearVideoUri(prefs)
        LiveWallpaperSettingsManager.setWallpaperApplied(prefs, false)
        prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_APPLY_IN_PROGRESS, false).apply()
        prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_RESTORE_SETTINGS, false).apply()
        refreshLiveWallpaperVideoSummary()
        refreshLiveWallpaperActionButtons()

        if (!removed) {
            runCatching {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
            }
        }
    }

    private fun openLiveWallpaperChooser(requireVideo: Boolean) {
        val uri = LiveWallpaperSettingsManager.getVideoUri(prefs)
        if (requireVideo && uri == null) {
            Toast.makeText(this, "Pick a video first", Toast.LENGTH_SHORT).show()
            setSettingsTab(SettingsTab.LIVE_WALLPAPER)
            return
        }
        prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_APPLY_IN_PROGRESS, true).apply()
        prefs.edit()
            .putBoolean(
                PREF_LIVE_WALLPAPER_RESTORE_SETTINGS,
                ::settingsOverlay.isInitialized && settingsOverlay.visibility == View.VISIBLE
            )
            .apply()
        val component = ComponentName(this, VideoLiveWallpaperService::class.java)
        val targetedIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        try {
            liveWallpaperApplyLauncher.launch(targetedIntent)
        } catch (e: Exception) {
            try {
                liveWallpaperApplyLauncher.launch(fallbackIntent)
            } catch (e2: Exception) {
                prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_APPLY_IN_PROGRESS, false).apply()
                prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_RESTORE_SETTINGS, false).apply()
                Toast.makeText(this, "Unable to open wallpaper chooser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isBifrostLiveWallpaperActive(): Boolean {
        val info = WallpaperManager.getInstance(this).wallpaperInfo ?: return false
        val expectedComponent = ComponentName(this, VideoLiveWallpaperService::class.java)
        val activeComponent = info.component
        if (activeComponent == expectedComponent) return true

        val serviceName = info.serviceName
        val matches = info.packageName == packageName && (
            serviceName == VideoLiveWallpaperService::class.java.name ||
                serviceName.endsWith(".${VideoLiveWallpaperService::class.java.simpleName}")
            )
        Log.d(
            "LiveWallpaperState",
            "expected=${expectedComponent.flattenToShortString()} active=${activeComponent?.flattenToShortString()} package=${info.packageName} service=$serviceName matches=$matches"
        )
        return matches
    }

    private fun setupColoredLogoSwitch() {
        isColoredLogoEnabled = prefs.getBoolean(PREF_COLORED_LOGO_ENABLED, false)
        coloredLogoSwitch.isChecked = isColoredLogoEnabled
        applyHeaderLogoPreference()

        coloredLogoSwitch.setOnCheckedChangeListener { _, isChecked ->
            isColoredLogoEnabled = isChecked
            prefs.edit().putBoolean(PREF_COLORED_LOGO_ENABLED, isChecked).apply()
            applyHeaderLogoPreference()
            if (isChecked) {
                playBifrostHeaderAnimation()
            }
        }
    }

    private fun setupRainbowTitleText() {
        if (bifrostTitleLabel.isBlank()) return

        bifrostLogoView.setOnClickListener {
            if (isColoredLogoEnabled) {
                playBifrostHeaderAnimation()
            }
        }
        bifrostTitleText.setOnClickListener {
            if (isColoredLogoEnabled) {
                playBifrostHeaderAnimation()
            }
        }

        if (isColoredLogoEnabled) {
            playBifrostHeaderAnimation()
        } else {
            applyHeaderLogoPreference()
        }
    }

    private fun applyHeaderLogoPreference() {
        if (bifrostTitleLabel.isBlank()) return

        titleIntroAnimator?.cancel()
        headerSettleAnimator?.cancel()
        titleIntroAnimator = null
        headerSettleAnimator = null

        bifrostLogoView.rotation = 0f
        bifrostLogoView.scaleX = 1f
        bifrostLogoView.scaleY = 1f
        bifrostLogoView.translationY = 0f
        bifrostLogoView.alpha = 1f

        if (isColoredLogoEnabled) {
            applyWatercolorTitlePhase(
                bifrostTitleLabel,
                selectedUiTheme.headerPalette.settlePhaseDegrees
            )
            bifrostLogoView.clearColorFilter()
        } else {
            bifrostTitleText.text = bifrostTitleLabel
            bifrostTitleText.setTextColor(selectedUiTheme.textColor)
            bifrostLogoView.clearColorFilter()
        }
    }

    private fun cancelPendingCoverFlowSnap() {
        coverFlowSnapRunnable?.let(mainHandler::removeCallbacks)
        coverFlowSnapRunnable = null
    }

    private fun scheduleCoverFlowSnap() {
        if (!::presetController.isInitialized) return
        if (::appProfileManager.isInitialized && appProfileManager.isEnabled) return

        cancelPendingCoverFlowSnap()
        lastCoverFlowScrollXForSnap = presetCoverFlowScroll.scrollX

        coverFlowSnapRunnable = object : Runnable {
            override fun run() {
                if (isCoverFlowTouching) {
                    cancelPendingCoverFlowSnap()
                    return
                }

                val currentX = presetCoverFlowScroll.scrollX
                if (currentX != lastCoverFlowScrollXForSnap) {
                    lastCoverFlowScrollXForSnap = currentX
                    mainHandler.postDelayed(this, COVER_FLOW_SNAP_SETTLE_DELAY_MS)
                    return
                }

                if (suppressNextCoverFlowSnap) {
                    suppressNextCoverFlowSnap = false
                }

                snapCoverFlowToNearestPreset()
                cancelPendingCoverFlowSnap()
            }
        }

        mainHandler.postDelayed(coverFlowSnapRunnable!!, COVER_FLOW_SNAP_SETTLE_DELAY_MS)
    }

    private fun openSettingsOverlay() {
        if (settingsOverlay.visibility == View.VISIBLE || isSettingsOverlayAnimating) return

        isSettingsOverlayAnimating = true
        val startOffset = getSettingsSlideDistancePx()

        settingsOverlay.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationX = startOffset
            isClickable = true
        }

        homeContainer.animate().cancel()
        homeContainer.animate()
            .alpha(SETTINGS_HOME_DIM_ALPHA)
            .setDuration(SETTINGS_OPEN_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        settingsOverlay.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(SETTINGS_OPEN_DURATION_MS)
            .setInterpolator(OvershootInterpolator(0.72f))
            .withEndAction {
                isSettingsOverlayAnimating = false
            }
            .start()
    }

    private fun closeSettingsOverlay() {
        if (settingsOverlay.visibility != View.VISIBLE || isSettingsOverlayAnimating) return

        isSettingsOverlayAnimating = true
        val targetOffset = getSettingsSlideDistancePx()

        homeContainer.animate().cancel()
        homeContainer.animate()
            .alpha(1f)
            .setDuration(SETTINGS_CLOSE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        settingsOverlay.animate()
            .alpha(0f)
            .translationX(targetOffset)
            .setDuration(SETTINGS_CLOSE_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.15f))
            .withEndAction {
                settingsOverlay.visibility = View.GONE
                settingsOverlay.alpha = 1f
                settingsOverlay.translationX = 0f
                isSettingsOverlayAnimating = false
                refreshCoverFlowFromPresets()
            }
            .start()
    }

    private fun promptCloseSettingsWithSaveChoice() {
        val dialog = BifrostAlertDialog()
        dialog.show(
            activity = this,
            title = getString(R.string.settings_close_confirm_title),
            subtitle = getString(R.string.settings_close_confirm_subtitle),
            body = null,
            positiveLabelResId = R.string.action_save,
            negativeLabelResId = R.string.action_dont_save,
            cancelable = true,
            onConfirm = {
                presetController.saveCurrentConfigToSelectedPreset()
                closeSettingsOverlay()
            },
            onCancel = {
                closeSettingsOverlay()
            }
        )
    }

    private fun requestCloseSettingsOverlay() {
        if (!::presetController.isInitialized || !presetController.hasUnsavedChangesForSelectedPreset()) {
            closeSettingsOverlay()
            return
        }
        promptCloseSettingsWithSaveChoice()
    }

    private fun openSelectedPresetArtworkEditor(anchor: View) {
        if (!::presetController.isInitialized) return
        val presets = presetController.getPresets()
        if (presets.isEmpty()) return

        val selectedIndex = presetSpinner.selectedItemPosition
        if (selectedIndex !in presets.indices) {
            Toast.makeText(this, "Select a preset first", Toast.LENGTH_SHORT).show()
            return
        }

        showPresetArtworkMenu(anchor, selectedIndex)
    }

    private fun startAppProfileSync() {
        if (!::presetController.isInitialized) return
        stopAppProfileSync()
        appProfileSyncRunnable = object : Runnable {
            override fun run() {
                if (!::appProfileManager.isInitialized || !appProfileManager.isEnabled) {
                    stopAppProfileSync()
                    return
                }

                val lastPresetName = prefs.getString(PREF_KEY_LAST_PRESET, null)
                if (!lastPresetName.isNullOrBlank()) {
                    val presets = presetController.getPresets()
                    val index = presets.indexOfFirst { it.name == lastPresetName }
                    if (index in presets.indices) {
                        selectedCoverFlowIndex = index
                        val selectedPreset = presets[index]
                        activePresetNameText.text = selectedPreset.name
                        activePresetAnimationText.text =
                            formatCardAnimationLabel(selectedPreset.animationType.name)
                        activePresetProfileText.text =
                            formatCardProfileLabel(selectedPreset.performanceProfile.name)
                    }
                }

                mainHandler.postDelayed(this, APP_PROFILE_SYNC_INTERVAL_MS)
            }
        }
        appProfileSyncRunnable?.let(mainHandler::post)
    }

    private fun stopAppProfileSync() {
        appProfileSyncRunnable?.let(mainHandler::removeCallbacks)
        appProfileSyncRunnable = null
    }

    private fun getSettingsSlideDistancePx(): Float {
        val width = settingsOverlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (width * 0.24f).coerceAtLeast(dpToPx(72).toFloat())
    }

    private fun refreshCoverFlowFromPresets() {
        if (!::presetController.isInitialized) return

        val autoSwitchEnabled = ::appProfileManager.isInitialized && appProfileManager.isEnabled
        val presets = presetController.getPresets()
        val tileSizePx = dpToPx(COVER_FLOW_TILE_SIZE_DP)
        val tileGapPx = dpToPx(COVER_FLOW_TILE_GAP_DP)
        if (presets.isEmpty()) {
            presetCoverFlowContainer.removeAllViews()
            presetCoverFlowContainer.addView(createCreatePresetActionCard(tileSizePx, tileGapPx))
            activePresetNameText.text = "No presets"
            activePresetAnimationText.text = "Animation: -"
            activePresetProfileText.text = "Profile: -"
            selectedCoverFlowIndex = 0

            presetCoverFlowScroll.post {
                val sidePadding = ((presetCoverFlowScroll.width - tileSizePx) / 2).coerceAtLeast(dpToPx(12))
                presetCoverFlowContainer.setPadding(sidePadding, 0, sidePadding, 0)
                centerPresetCard(0, animate = false)
                updateCoverFlowCardTransforms()
            }

            updateManualPresetSwitchingUi(appProfileManager.isEnabled)
            return
        }

        presetCoverFlowContainer.removeAllViews()
        presets.forEachIndexed { index, preset ->
            var longPressTriggered = false
            var longPressRunnable: Runnable? = null
            var movedTooMuchForTap = false
            val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
            var downX = 0f
            var downY = 0f

            val triggerCardLongPress: () -> Unit = {
                if (!isCoverFlowDragging) {
                    suppressNextCoverFlowSnap = true
                    selectPresetFromCoverFlow(index, animate = true, applyPreset = false)
                    presetController.selectPresetForEditing(index)
                    openSettingsOverlay()
                }
            }

            val card = MaterialCardView(this).apply {
                tag = index
                val layoutParams = LinearLayout.LayoutParams(tileSizePx, tileSizePx).apply {
                    marginEnd = tileGapPx
                }
                this.layoutParams = layoutParams
                radius = dpToPx(16).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_card))
                setStrokeColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_accent))
                strokeWidth = dpToPx(1)
                setOnClickListener {
                    if (autoSwitchEnabled) return@setOnClickListener
                    if (isCoverFlowDragging) return@setOnClickListener
                    suppressNextCoverFlowSnap = true
                    selectPresetFromCoverFlow(index, animate = true)
                }
                setOnLongClickListener {
                    triggerCardLongPress()
                    true
                }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            longPressTriggered = false
                            movedTooMuchForTap = false
                            downX = event.x
                            downY = event.y
                            parent?.requestDisallowInterceptTouchEvent(true)
                            longPressRunnable?.let(mainHandler::removeCallbacks)
                            longPressRunnable = Runnable {
                                if (!longPressTriggered) {
                                    triggerCardLongPress()
                                    longPressTriggered = true
                                }
                            }
                            mainHandler.postDelayed(
                                longPressRunnable!!,
                                ViewConfiguration.getLongPressTimeout().toLong()
                            )
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val movedTooMuch = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                            if (movedTooMuch) {
                                movedTooMuchForTap = true
                                parent?.requestDisallowInterceptTouchEvent(false)
                                longPressRunnable?.let(mainHandler::removeCallbacks)
                                longPressRunnable = null
                            }
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            longPressRunnable?.let(mainHandler::removeCallbacks)
                            longPressRunnable = null
                            if (event.actionMasked == MotionEvent.ACTION_UP && longPressTriggered) {
                                // Consume ACTION_UP after long press to avoid triggering the tap handler.
                                return@setOnTouchListener true
                            }

                            if (
                                event.actionMasked == MotionEvent.ACTION_UP &&
                                !movedTooMuchForTap &&
                                !autoSwitchEnabled &&
                                !isCoverFlowDragging
                            ) {
                                view.performClick()
                                return@setOnTouchListener true
                            }
                        }
                    }
                    false
                }
                setOnDragListener { dragTarget, event ->
                    if (autoSwitchEnabled) return@setOnDragListener true
                    val fromIndex = event.localState as? Int ?: return@setOnDragListener false
                    val toIndex = dragTarget.tag as? Int ?: return@setOnDragListener false

                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> {
                            isCoverFlowDragging = true
                            true
                        }

                        DragEvent.ACTION_DRAG_ENTERED -> {
                            if (toIndex != fromIndex) {
                                strokeWidth = dpToPx(3)
                            }
                            true
                        }

                        DragEvent.ACTION_DRAG_EXITED -> {
                            updateCoverFlowCardTransforms()
                            true
                        }

                        DragEvent.ACTION_DROP -> {
                            if (toIndex != fromIndex) {
                                val moved = presetController.movePreset(fromIndex, toIndex)
                                if (moved) {
                                    suppressNextCoverFlowSnap = true
                                    refreshCoverFlowFromPresets()
                                }
                            }
                            true
                        }

                        DragEvent.ACTION_DRAG_ENDED -> {
                            isCoverFlowDragging = false
                            updateCoverFlowCardTransforms()
                            true
                        }

                        else -> true
                    }
                }
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.card_glow_bg)
                isClickable = true
                isLongClickable = true
                setOnClickListener {
                    card.performClick()
                }
                setOnLongClickListener {
                    card.performLongClick()
                }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val name = TextView(this).apply {
                text = preset.name
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_text))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
            }

            val centerIconContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = android.view.Gravity.CENTER
            }

            val emojiView = TextView(this).apply {
                textSize = 72f
                gravity = android.view.Gravity.CENTER
                visibility = View.GONE
            }

            val drawableIconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(74), dpToPx(74))
                visibility = View.VISIBLE
            }

            PresetVisuals.bind(
                context = this,
                spec = PresetVisuals.fromPreset(preset),
                iconView = drawableIconView,
                emojiView = emojiView,
                targetSizePx = dpToPx(74)
            )

            centerIconContainer.addView(emojiView)
            centerIconContainer.addView(drawableIconView)
            content.addView(name)
            content.addView(centerIconContainer)
            card.addView(content)
            presetCoverFlowContainer.addView(card)
        }

        // Keep a persistent trailing action tile to create a new preset from the home flow.
        presetCoverFlowContainer.addView(createCreatePresetActionCard(tileSizePx, tileGapPx))

        selectedCoverFlowIndex = if (::appProfileManager.isInitialized && appProfileManager.isEnabled) {
            val lastPresetName = prefs.getString(PREF_KEY_LAST_PRESET, null)
            lastPresetName?.let { name ->
                presets.indexOfFirst { it.name == name }.takeIf { it >= 0 }
            } ?: presetSpinner.selectedItemPosition
        } else {
            presetSpinner.selectedItemPosition
        }.coerceIn(0, presets.lastIndex)

        val selectedPreset = presets[selectedCoverFlowIndex]
        activePresetNameText.text = selectedPreset.name
        activePresetAnimationText.text = formatCardAnimationLabel(selectedPreset.animationType.name)
        activePresetProfileText.text = formatCardProfileLabel(selectedPreset.performanceProfile.name)

        presetCoverFlowScroll.post {
            val sidePadding = ((presetCoverFlowScroll.width - tileSizePx) / 2).coerceAtLeast(dpToPx(12))
            presetCoverFlowContainer.setPadding(sidePadding, 0, sidePadding, 0)
            centerPresetCard(selectedCoverFlowIndex, animate = false)
            updateCoverFlowCardTransforms()
        }

        syncAppProfileDefaultSwitch()

        updateManualPresetSwitchingUi(appProfileManager.isEnabled)
    }

    private fun createCreatePresetActionCard(tileSizePx: Int, tileGapPx: Int): MaterialCardView {
        val card = MaterialCardView(this).apply {
            tag = COVER_FLOW_CREATE_TAG
            layoutParams = LinearLayout.LayoutParams(tileSizePx, tileSizePx).apply {
                marginEnd = tileGapPx
            }
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_card))
            setStrokeColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_accent))
            strokeWidth = dpToPx(1)
            setOnClickListener {
                if (::appProfileManager.isInitialized && appProfileManager.isEnabled) return@setOnClickListener
                launchCreatePresetFromCoverFlow()
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.card_glow_bg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        val plusBubble = TextView(this).apply {
            text = "+"
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_text))
            textSize = 34f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_surface))
                setStroke(dpToPx(2), ContextCompat.getColor(this@MainActivity, R.color.bifrost_accent))
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(88), dpToPx(88))
            contentDescription = "Create new preset"
        }

        val label = TextView(this).apply {
            text = "NEW PRESET"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_text_secondary))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
        }

        content.addView(plusBubble)
        content.addView(label)
        card.addView(content)
        return card
    }

    private fun launchCreatePresetFromCoverFlow() {
        if (!::presetController.isInitialized) return

        if (settingsOverlay.visibility != View.VISIBLE) {
            openSettingsOverlay()
        }

        val triggerCreatePreset: () -> Unit = {
            if (settingsOverlay.visibility == View.VISIBLE) {
                presetController.showSaveAsNewPresetDialogFromHomePlus()
            }
        }

        if (isSettingsOverlayAnimating) {
            mainHandler.postDelayed(triggerCreatePreset, SETTINGS_OPEN_DURATION_MS + 40L)
        } else {
            mainHandler.post(triggerCreatePreset)
        }
    }

    private fun selectPresetFromCoverFlow(index: Int, animate: Boolean, applyPreset: Boolean = true) {
        if (!::presetController.isInitialized) return
        if (applyPreset && ::appProfileManager.isInitialized && appProfileManager.isEnabled) return
         val presets = presetController.getPresets()
         if (presets.isEmpty()) return
 
         val selectedIndex = index.coerceIn(0, presets.lastIndex)
         selectedCoverFlowIndex = selectedIndex
         val selectedPreset = presets[selectedIndex]
 
         activePresetNameText.text = selectedPreset.name
         activePresetAnimationText.text = formatCardAnimationLabel(selectedPreset.animationType.name)
         activePresetProfileText.text = formatCardProfileLabel(selectedPreset.performanceProfile.name)
 
         centerPresetCard(selectedIndex, animate)
         updateCoverFlowCardTransforms()
 
        if (applyPreset) {
            presetController.applyPresetAt(selectedIndex)
        }
     }

    private fun snapCoverFlowToNearestPreset() {
        if (presetCoverFlowContainer.childCount == 0) return
        val presetCount = presetController.getPresets().size
        if (presetCount <= 0) return

        val centerX = presetCoverFlowScroll.scrollX + (presetCoverFlowScroll.width / 2f)
        var nearestIndex = 0
        var nearestDistance = Float.MAX_VALUE

        for (index in 0 until presetCoverFlowContainer.childCount) {
            val card = presetCoverFlowContainer.getChildAt(index) ?: continue
            if (!isPresetCardIndex(index, presetCount)) continue
            val cardCenterX = card.left + (card.width / 2f)
            val distance = abs(centerX - cardCenterX)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }

        val shouldApplyPreset = nearestIndex != selectedCoverFlowIndex
        selectPresetFromCoverFlow(nearestIndex, animate = true, applyPreset = shouldApplyPreset)
    }

    private fun centerPresetCard(index: Int, animate: Boolean) {
        val card = presetCoverFlowContainer.getChildAt(index) ?: return
        val targetScrollX = (card.left + (card.width / 2f) - (presetCoverFlowScroll.width / 2f))
            .roundToInt()
            .coerceAtLeast(0)

        if (animate) {
            presetCoverFlowScroll.smoothScrollTo(targetScrollX, 0)
        } else {
            presetCoverFlowScroll.scrollTo(targetScrollX, 0)
        }
    }

    private fun updateCoverFlowCardTransforms() {
        val scrollWidth = presetCoverFlowScroll.width
        val centerX = presetCoverFlowScroll.scrollX + (presetCoverFlowScroll.width / 2f)
        val accentColor = ContextCompat.getColor(this, R.color.bifrost_accent)
        val secondaryColor = ContextCompat.getColor(this, R.color.bifrost_text_secondary)
        val autoSwitchEnabled = ::appProfileManager.isInitialized && appProfileManager.isEnabled

        if (scrollWidth <= 0) {
            for (index in 0 until presetCoverFlowContainer.childCount) {
                val card = presetCoverFlowContainer.getChildAt(index) as? MaterialCardView ?: continue
                if (isCreatePresetCard(card)) {
                    card.scaleX = 1f
                    card.scaleY = 1f
                    card.alpha = if (autoSwitchEnabled) 0.5f else 0.88f
                    card.strokeWidth = dpToPx(1)
                    card.setStrokeColor(accentColor)
                    continue
                }
                val isSelected = index == selectedCoverFlowIndex
                card.scaleX = 1f
                card.scaleY = 1f
                card.alpha = if (autoSwitchEnabled) {
                    0.3f
                } else {
                    if (isSelected) 1f else 0.5f
                }

                if (autoSwitchEnabled) {
                    card.strokeWidth = dpToPx(1)
                    card.setStrokeColor(secondaryColor)
                } else {
                    card.strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
                    card.setStrokeColor(if (isSelected) accentColor else secondaryColor)
                }
            }
            return
        }

        for (index in 0 until presetCoverFlowContainer.childCount) {
            val card = presetCoverFlowContainer.getChildAt(index) as? MaterialCardView ?: continue
            if (isCreatePresetCard(card)) {
                val cardCenterX = card.left + (card.width / 2f)
                val distance = abs(centerX - cardCenterX)
                val normalizedDistance = (distance / (scrollWidth * 0.9f)).coerceIn(0f, 1f)
                val scale = 1f - (0.12f * normalizedDistance)
                card.scaleX = scale
                card.scaleY = scale
                card.alpha = if (autoSwitchEnabled) 0.46f else 0.78f + (0.2f * (1f - normalizedDistance))
                card.strokeWidth = dpToPx(1)
                card.setStrokeColor(accentColor)
                continue
            }
            val cardCenterX = card.left + (card.width / 2f)
            val distance = abs(centerX - cardCenterX)
            val normalizedDistance = (distance / (scrollWidth * 0.9f)).coerceIn(0f, 1f)
            val scale = 1f - (0.2f * normalizedDistance)
            val isSelected = index == selectedCoverFlowIndex

            card.scaleX = scale
            card.scaleY = scale
            card.alpha = if (autoSwitchEnabled) {
                0.22f + (0.12f * (1f - normalizedDistance))
            } else {
                0.5f + (0.5f * (1f - normalizedDistance))
            }

            if (autoSwitchEnabled) {
                card.strokeWidth = dpToPx(1)
                card.setStrokeColor(secondaryColor)
            } else {
                card.strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
                card.setStrokeColor(if (isSelected) accentColor else secondaryColor)
            }
        }
    }

    private fun isPresetCardIndex(index: Int, presetCount: Int): Boolean {
        return index in 0 until presetCount
    }

    private fun isCreatePresetCard(view: View): Boolean {
        return (view.tag as? Int) == COVER_FLOW_CREATE_TAG
    }

    private fun syncAppProfileSwitches(isChecked: Boolean) {
        isSyncingAppProfileSwitches = true
        appProfileSwitch.isChecked = isChecked
        homeAppProfileSwitch.isChecked = isChecked
        isSyncingAppProfileSwitches = false
    }

    private fun syncAppProfileDefaultSwitch() {
        if (!::presetController.isInitialized) return
        val selectedIndex = presetSpinner.selectedItemPosition
        val preset = presetController.getPresets().getOrNull(selectedIndex)
        isSyncingAppProfileDefaultSwitch = true
        appProfileDefaultSwitch.isChecked = preset?.isAppProfileDefault == true
        isSyncingAppProfileDefaultSwitch = false
    }

    private fun requestImmediateAppProfileResolution() {
        if (!LEDService.isRunning) return
        startService(Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_FORCE_APP_PROFILE_RESOLUTION
        })
    }

    private fun updateManualPresetSwitchingUi(autoSwitchEnabled: Boolean) {
        presetCoverFlowScroll.isEnabled = true
        presetCoverFlowScroll.scrollLocked = false
        activePresetInfoCard.alpha = 1f
        activePresetStatusBadge.visibility = if (autoSwitchEnabled) View.VISIBLE else View.GONE
        presetCoverFlowScroll.alpha = if (autoSwitchEnabled) 0.95f else 1f
        presetSpinner.isEnabled = true
        presetSpinner.alpha = 1f

        if (autoSwitchEnabled) {
            startAppProfileSync()
        } else {
            stopAppProfileSync()
        }

        updateCoverFlowCardTransforms()
    }

    private fun showPresetArtworkMenu(anchor: View, index: Int): Boolean {
        val initialPreset = presetController.getPresets().getOrNull(index) ?: return false
        presetArtworkSheetDialog?.dismiss()

        val sheetView = LayoutInflater.from(this).inflate(R.layout.sheet_preset_artwork, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetView)
        presetArtworkSheetDialog = dialog

        val titleView = sheetView.findViewById<TextView>(R.id.presetArtworkTitle)
        val previewNameView = sheetView.findViewById<TextView>(R.id.presetArtworkPreviewName)
        val previewImageView = sheetView.findViewById<ImageView>(R.id.presetArtworkPreviewImage)
        val previewEmojiView = sheetView.findViewById<TextView>(R.id.presetArtworkPreviewEmoji)
        val iconOptionsContainer = sheetView.findViewById<LinearLayout>(R.id.presetArtworkIconOptions)
        val emojiInputLayout = sheetView.findViewById<TextInputLayout>(R.id.presetArtworkEmojiInputLayout)
        val emojiInput = sheetView.findViewById<TextInputEditText>(R.id.presetArtworkEmojiInput)
        val applyEmojiButton = sheetView.findViewById<MaterialButton>(R.id.presetArtworkApplyEmojiButton)
        val uploadButton = sheetView.findViewById<MaterialButton>(R.id.presetArtworkUploadButton)
        val resetButton = sheetView.findViewById<MaterialButton>(R.id.presetArtworkResetButton)
        val closeButton = sheetView.findViewById<MaterialButton>(R.id.presetArtworkCloseButton)
        val builtInIcons = PresetIcon.values().toList()

        titleView.text = "CUSTOMIZE ${initialPreset.name.uppercase()}"
        emojiInput.setText(initialPreset.customEmoji.orEmpty())
        emojiInput.setSelection(emojiInput.text?.length ?: 0)

        fun updatePresetVisualInSheet(
            successMessage: (LedPreset) -> String,
            transform: (LedPreset) -> LedPreset
        ): LedPreset? {
            val updatedPreset = presetController.updatePresetVisual(index, transform)
            if (updatedPreset == null) {
                Toast.makeText(this, "That preset is no longer available", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return null
            }

            refreshCoverFlowFromPresets()
            Toast.makeText(this, successMessage(updatedPreset), Toast.LENGTH_SHORT).show()
            return updatedPreset
        }

        fun renderSheet(preset: LedPreset) {
            previewNameView.text = preset.name
            resetButton.visibility = if (
                !preset.customEmoji.isNullOrBlank() ||
                !preset.customImageFileName.isNullOrBlank() ||
                !preset.appIconPackageName.isNullOrBlank()
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

            PresetVisuals.bind(
                context = this,
                spec = PresetVisuals.fromPreset(preset),
                iconView = previewImageView,
                emojiView = previewEmojiView,
                targetSizePx = dpToPx(88)
            )

            iconOptionsContainer.removeAllViews()
            builtInIcons.forEach { icon ->
                val isSelected = preset.customEmoji.isNullOrBlank() &&
                    preset.customImageFileName.isNullOrBlank() &&
                    preset.appIconPackageName.isNullOrBlank() &&
                    preset.icon == icon
                iconOptionsContainer.addView(
                    createPresetArtworkIconOption(
                        icon = icon,
                        isSelected = isSelected,
                        onClick = {
                            emojiInputLayout.error = null
                            emojiInput.setText("")
                            val updatedPreset = updatePresetVisualInSheet(
                                successMessage = { "${it.name} icon updated" }
                            ) { current ->
                                current.copy(
                                    icon = icon,
                                    customEmoji = null,
                                    customImageFileName = null,
                                    appIconPackageName = null
                                )
                            }
                            if (updatedPreset != null) {
                                renderSheet(updatedPreset)
                            }
                        }
                    )
                )
            }

            val assignedApps = resolveAssignedAppVisuals(preset.name)
            assignedApps.forEach { appVisual ->
                val isSelected =
                    preset.customEmoji.isNullOrBlank() &&
                    preset.customImageFileName.isNullOrBlank() &&
                    preset.appIconPackageName == appVisual.packageName

                iconOptionsContainer.addView(
                    createPresetArtworkAppIconOption(
                        iconDrawable = appVisual.icon,
                        label = appVisual.appName,
                        isSelected = isSelected,
                        onClick = {
                            emojiInputLayout.error = null
                            emojiInput.setText("")
                            val updatedPreset = updatePresetVisualInSheet(
                                successMessage = { "${it.name} app icon updated" }
                            ) { current ->
                                current.copy(
                                    customEmoji = null,
                                    customImageFileName = null,
                                    appIconPackageName = appVisual.packageName
                                )
                            }
                            if (updatedPreset != null) {
                                renderSheet(updatedPreset)
                            }
                        }
                    )
                )
            }
        }

        applyEmojiButton.setOnClickListener {
            val value = emojiInput.text?.toString()?.trim().orEmpty()
            if (value.isBlank()) {
                emojiInputLayout.error = "Enter an emoji or short symbol"
                return@setOnClickListener
            }

            emojiInputLayout.error = null
            val updatedPreset = updatePresetVisualInSheet(
                successMessage = { "${it.name} emoji updated" }
            ) { current ->
                current.copy(
                    customEmoji = value,
                    customImageFileName = null,
                    appIconPackageName = null
                )
            }
            if (updatedPreset != null) {
                renderSheet(updatedPreset)
            }
        }

        uploadButton.setOnClickListener {
            dialog.dismiss()
            launchPresetImagePicker(index)
        }

        resetButton.setOnClickListener {
            emojiInputLayout.error = null
            emojiInput.setText("")
            val updatedPreset = updatePresetVisualInSheet(
                successMessage = { "${it.name} icon restored" }
            ) { current ->
                current.copy(
                    customEmoji = null,
                    customImageFileName = null,
                    appIconPackageName = null
                )
            }
            if (updatedPreset != null) {
                renderSheet(updatedPreset)
            }
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (presetArtworkSheetDialog === dialog) {
                presetArtworkSheetDialog = null
            }
        }

        renderSheet(initialPreset)
        dialog.show()

        val behavior = dialog.behavior
        behavior.isFitToContents = true
        behavior.skipCollapsed = false
        behavior.peekHeight = dpToPx(300)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        return true
    }

    private fun launchPresetImagePicker(index: Int) {
        pendingPresetArtworkIndex = index
        presetImagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun createPresetArtworkIconOption(
        icon: PresetIcon,
        isSelected: Boolean,
        onClick: () -> Unit
    ): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)).apply {
                marginEnd = dpToPx(10)
            }
            radius = dpToPx(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_surface))
            setStrokeColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isSelected) R.color.bifrost_accent else R.color.bifrost_text_secondary
                )
            )
            strokeWidth = dpToPx(if (isSelected) 2 else 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
        }

        val visualFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42))
        }
        val iconView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val emojiView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = android.view.Gravity.CENTER
            textSize = 22f
            visibility = View.GONE
        }

        PresetVisuals.bind(
            context = this,
            spec = PresetVisuals.fromBuiltIn(icon),
            iconView = iconView,
            emojiView = emojiView,
            targetSizePx = dpToPx(42)
        )

        visualFrame.addView(iconView)
        visualFrame.addView(emojiView)
        content.addView(visualFrame)
        card.addView(content)
        return card
    }

    private fun createPresetArtworkAppIconOption(
        iconDrawable: android.graphics.drawable.Drawable,
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(64), dpToPx(64)).apply {
                marginEnd = dpToPx(10)
            }
            radius = dpToPx(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_surface))
            setStrokeColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isSelected) R.color.bifrost_accent else R.color.bifrost_text_secondary
                )
            )
            strokeWidth = dpToPx(if (isSelected) 2 else 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            contentDescription = label
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
        }

        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(iconDrawable)
        }

        content.addView(iconView)
        card.addView(content)
        return card
    }

    private fun handleAppProfileToggleChange(isChecked: Boolean) {
        if (isSyncingAppProfileSwitches) return

        if (isChecked && !appProfileManager.hasUsageStatsPermission(this)) {
            syncAppProfileSwitches(false)
            updateManualPresetSwitchingUi(false)
            Toast.makeText(this, "Grant usage access to Bifrost in Settings", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        appProfileManager.isEnabled = isChecked
        appProfileManager.resetLastForegroundPackage()
        syncAppProfileSwitches(isChecked)
        updateManualPresetSwitchingUi(isChecked)
        maybeShowAppProfileInfoDialog(isChecked)

        if (!LEDService.isRunning || serviceController.isServiceTransitioning) return

        if (isChecked) {
            requestImmediateAppProfileResolution()
        } else {
            startService(createLedServiceIntent())
        }
    }

    private fun formatCardAnimationLabel(animationName: String): String {
        val formatted = animationName.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        return "Animation: $formatted"
    }

    private fun formatCardProfileLabel(profileName: String): String {
        val formatted = profileName.lowercase().replaceFirstChar { it.uppercase() }
        return "Profile: $formatted"
    }

    private fun getSelectedPresetName(): String? {
        val selectedPreset = presetSpinner.selectedItem as? LedPreset
        if (selectedPreset != null) return selectedPreset.name
        return if (::presetController.isInitialized) {
            presetController.getPresets().getOrNull(presetSpinner.selectedItemPosition)?.name
        } else {
            null
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).roundToInt()
    }

    private fun setupStatusBar() {
        window.statusBarColor = selectedUiTheme.backgroundColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isAppInitialized) return

        resumeStateSyncRunnable?.let(mainHandler::removeCallbacks)
        resumeStateSyncRunnable = Runnable {
            if (isAwaitingPermissionResult) {
                if (LEDService.isRunning) serviceToggle.isChecked = true
                isAwaitingPermissionResult = false
            } else {
                serviceToggle.isChecked = LEDService.isRunning
                enableRainbowBackground(LEDService.isRunning)
            }

            if (::presetController.isInitialized) {
                presetController.reloadFromPrefs()
            }

            if (prefs.getBoolean(PREF_LIVE_WALLPAPER_RESTORE_SETTINGS, false)) {
                prefs.edit().putBoolean(PREF_LIVE_WALLPAPER_RESTORE_SETTINGS, false).apply()
                setSettingsTab(SettingsTab.LIVE_WALLPAPER)
                openSettingsOverlay()
            }

            refreshCoverFlowFromPresets()
            if (prefs.getBoolean(PREF_LIVE_WALLPAPER_APPLY_IN_PROGRESS, false)) {
                scheduleLiveWallpaperApplyStateSync()
            } else {
                refreshLiveWallpaperActionButtons()
            }
        }
        mainHandler.postDelayed(resumeStateSyncRunnable!!, 100)
    }


    override fun onPause() {
        super.onPause()
        if (!isAppInitialized) return
        titleIntroAnimator?.cancel()
        headerSettleAnimator?.cancel()
        titleIntroAnimator = null
        headerSettleAnimator = null
        homeContainer.animate().cancel()
        homeContainer.alpha = if (settingsOverlay.visibility == View.VISIBLE) SETTINGS_HOME_DIM_ALPHA else 1f
        settingsOverlay.animate().cancel()
        isSettingsOverlayAnimating = false
        presetArtworkSheetDialog?.dismiss()
        stopAppProfileSync()
        serviceController.cancelPendingOperations()
        resumeStateSyncRunnable?.let(mainHandler::removeCallbacks)
        resumeStateSyncRunnable = null
        liveWallpaperApplyStateSyncRunnable?.let(mainHandler::removeCallbacks)
        liveWallpaperApplyStateSyncRunnable = null
        coverFlowSnapRunnable?.let(mainHandler::removeCallbacks)
        coverFlowSnapRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isAppInitialized) return
        prefs.unregisterOnSharedPreferenceChangeListener(liveWallpaperPrefsListener)
        titleIntroAnimator?.cancel()
        headerSettleAnimator?.cancel()
        titleIntroAnimator = null
        headerSettleAnimator = null
        homeContainer.animate().cancel()
        homeContainer.alpha = 1f
        settingsOverlay.animate().cancel()
        isSettingsOverlayAnimating = false
        presetArtworkSheetDialog?.dismiss()
        stopAppProfileSync()
        serviceController.cancelPendingOperations()
        resumeStateSyncRunnable?.let(mainHandler::removeCallbacks)
        resumeStateSyncRunnable = null
        coverFlowSnapRunnable?.let(mainHandler::removeCallbacks)
        coverFlowSnapRunnable = null
        rainbowDrawable?.stop()
        rainbowDrawable = null
    }

    private fun normalizedCyclePhase(phaseDegrees: Float): Float {
        return ((phaseDegrees % 360f) + 360f) % 360f / 360f
    }

    private fun computeIntroThemeColor(index: Int, maxIndex: Int, phaseDegrees: Float): Int {
        val palette = selectedUiTheme.headerPalette
        val phase = normalizedCyclePhase(phaseDegrees)
        val letterProgress = index / maxIndex.toFloat()
        val hueWindow = palette.introHueSpan * ((phase + letterProgress) % 1f)
        val hue = (palette.introHueStart + hueWindow) % 360f
        return Color.HSVToColor(floatArrayOf(hue, palette.introSaturation, palette.introValue))
    }

    private fun computeSettleThemeColor(index: Int, maxIndex: Int, phaseDegrees: Float): Int {
        val palette = selectedUiTheme.headerPalette
        val phase = normalizedCyclePhase(phaseDegrees)
        val letterProgress = index / maxIndex.toFloat()
        val hueProgress = (letterProgress + phase) % 1f
        val hue = (
            palette.settleHueStart +
                palette.settleHueSpan * hueProgress +
                palette.settleHueWobbleAmplitude * sin(letterProgress * PI).toFloat()
            ) % 360f
        val saturation = (
            palette.settleSaturationBase +
                palette.settleSaturationWave * ((sin(letterProgress * PI * 3.0) + 1.0) / 2.0).toFloat()
            ).coerceIn(0f, 1f)
        val value = (
            palette.settleValueBase +
                palette.settleValueWave * ((cos(letterProgress * PI * 2.0) + 1.0) / 2.0).toFloat()
            ).coerceIn(0f, 1f)
        val alpha = (
            palette.settleAlphaBase +
                palette.settleAlphaWave * ((sin(letterProgress * PI * 2.5) + 1.0) / 2.0)
            ).roundToInt().coerceIn(0, 255)
        return Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
    }

    private fun applyRainbowTitlePhase(text: String, phaseDegrees: Float) {
        if (!isColoredLogoEnabled) {
            bifrostTitleText.text = text
            bifrostTitleText.setTextColor(selectedUiTheme.textColor)
            return
        }

        val rainbowText = SpannableString(text)
        val maxIndex = (text.length - 1).coerceAtLeast(1)

        text.indices.forEach { index ->
            if (text[index].isWhitespace()) return@forEach

            val color = computeIntroThemeColor(index, maxIndex, phaseDegrees)
            rainbowText.setSpan(
                ForegroundColorSpan(color),
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        bifrostTitleText.text = rainbowText
    }

    private fun applyWatercolorTitlePhase(text: String, phaseDegrees: Float) {
        val watercolorText = SpannableString(text)
        val maxIndex = (text.length - 1).coerceAtLeast(1)

        text.indices.forEach { index ->
            if (text[index].isWhitespace()) return@forEach

            val color = computeSettleThemeColor(index, maxIndex, phaseDegrees)
            watercolorText.setSpan(
                ForegroundColorSpan(color),
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        bifrostTitleText.text = watercolorText
    }

    private fun playBifrostHeaderAnimation() {
        if (bifrostTitleLabel.isBlank()) return
        if (!isColoredLogoEnabled) {
            applyHeaderLogoPreference()
            return
        }

        titleIntroAnimator?.cancel()
        headerSettleAnimator?.cancel()

        titleIntroAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TITLE_INTRO_ANIMATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val phaseDegrees = progress * 720f
                applyRainbowTitlePhase(bifrostTitleLabel, phaseDegrees)
                applyBifrostLogoAnimationFrame(progress, phaseDegrees)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var wasCanceled = false

                override fun onAnimationCancel(animation: Animator) {
                    wasCanceled = true
                    settleHeaderToStillState()
                }

                override fun onAnimationEnd(animation: Animator) {
                    titleIntroAnimator = null
                    if (!wasCanceled) {
                        settleHeaderToStillState()
                    }
                }
            })
            start()
        }
    }

    private fun applyBifrostLogoAnimationFrame(progress: Float, phaseDegrees: Float) {
        val spinWave = sin(progress * PI * 10.0).toFloat()
        val pulseWave = ((1.0 - cos(progress * PI * 6.0)) / 2.0).toFloat()
        val driftWave = sin(progress * PI * 4.0).toFloat()

        bifrostLogoView.rotation = 16f * spinWave
        bifrostLogoView.scaleX = 1f + (0.12f * pulseWave)
        bifrostLogoView.scaleY = 1f + (0.12f * pulseWave)
        bifrostLogoView.translationY = -10f * driftWave
        bifrostLogoView.alpha = 0.9f + (0.1f * pulseWave)
    }

    private fun resetBifrostHeaderAnimationState() {
        if (bifrostTitleLabel.isBlank()) return

        if (isColoredLogoEnabled) {
            applyWatercolorTitlePhase(
                bifrostTitleLabel,
                selectedUiTheme.headerPalette.settlePhaseDegrees
            )
        } else {
            bifrostTitleText.text = bifrostTitleLabel
            bifrostTitleText.setTextColor(selectedUiTheme.textColor)
        }

        bifrostLogoView.rotation = 0f
        bifrostLogoView.scaleX = 1f
        bifrostLogoView.scaleY = 1f
        bifrostLogoView.translationY = 0f
        bifrostLogoView.alpha = 1f
        bifrostLogoView.clearColorFilter()
    }

    private fun settleHeaderToStillState() {
        if (bifrostTitleLabel.isBlank()) return

        headerSettleAnimator?.cancel()
        headerSettleAnimator = null

        bifrostLogoView.animate()
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(400L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                applyHeaderLogoPreference()
            }
            .start()
    }

    private fun setupAnimationSpinner() {
        val types = LedAnimationType.values().toList()
        val labels = types.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val adapter = ArrayAdapter(this, R.layout.item_spinner_bifrost, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_bifrost)
        animationSpinner.adapter = adapter
        animationSpinner.setSelection(types.indexOf(selectedAnimationType))

        animationSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return

                    val wasRunning = LEDService.isRunning
                    selectedAnimationType = types[position]
                    updateParameterVisibility()

                    if (wasRunning) {
                        if (selectedAnimationType.needsMediaProjection) {
                            if (mediaProjectionResultCode == null || mediaProjectionData == null) {
                                checkRagnarokWarningAndRestart(true)
                            } else {
                                checkRagnarokWarningAndRestart()
                            }
                        } else {
                            checkRagnarokWarningAndRestart()
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun setupProfileSpinner() {
        val profiles = PerformanceProfile.values().toList()
        val labels = profiles.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val adapter = ArrayAdapter(this, R.layout.item_spinner_bifrost, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_bifrost)
        profileSpinner.adapter = adapter
        profileSpinner.setSelection(profiles.indexOf(selectedProfile))

        profileSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return

                    val profilesList = PerformanceProfile.values().toList()
                    val newProfile = profilesList[position]

                    if (newProfile == PerformanceProfile.RAGNAROK &&
                        selectedAnimationType.needsMediaProjection
                    ) {
                        val presetName = getSelectedPresetName()
                        val preset = presetController.getPresets()
                            .firstOrNull { it.name == presetName }

                        if (preset?.ragnarokAccepted != true) {
                            ragnarokWarningDialog.show(
                                activity = this@MainActivity,
                                onConfirm = {
                                    presetController.markRagnarokAccepted(presetName)
                                    selectedProfile = newProfile
                                    if (LEDService.isRunning) {
                                        serviceController.restartDebounced {
                                            createLedServiceIntent()
                                        }
                                    }
                                },
                                onCancel = {
                                    val currentIndex = profilesList.indexOf(selectedProfile)
                                    profileSpinner.setSelection(currentIndex)
                                }
                            )
                        } else {
                            selectedProfile = newProfile
                            if (LEDService.isRunning) {
                                serviceController.restartDebounced { createLedServiceIntent() }
                            }
                        }
                    } else {
                        selectedProfile = newProfile
                        if (LEDService.isRunning) {
                            serviceController.restartDebounced { createLedServiceIntent() }
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun setupColorButton() {
        colorButton.setOnClickListener { showColorPicker(isRight = false) }
        colorButton.setBackgroundColor(selectedColor)
        rightColorButton.setOnClickListener { showColorPicker(isRight = true) }
        rightColorButton.setBackgroundColor(selectedRightColor)

        batteryLowColorButton.setOnClickListener {
            showOptionalColorPicker(selectedBatteryLowColorOverride ?: DEFAULT_BATTERY_LOW_COLOR) {
                selectedBatteryLowColorOverride = it
                refreshPaletteButtons()
                triggerPaletteLiveUpdateIfRunning()
            }
        }
        batteryMidColorButton.setOnClickListener {
            showOptionalColorPicker(selectedBatteryMidColorOverride ?: DEFAULT_BATTERY_MID_COLOR) {
                selectedBatteryMidColorOverride = it
                refreshPaletteButtons()
                triggerPaletteLiveUpdateIfRunning()
            }
        }
        batteryHighColorButton.setOnClickListener {
            showOptionalColorPicker(selectedBatteryHighColorOverride ?: DEFAULT_BATTERY_HIGH_COLOR) {
                selectedBatteryHighColorOverride = it
                refreshPaletteButtons()
                triggerPaletteLiveUpdateIfRunning()
            }
        }
        cpuCoolColorButton.setOnClickListener {
            showOptionalColorPicker(selectedCpuCoolColorOverride ?: DEFAULT_CPU_COOL_COLOR) {
                selectedCpuCoolColorOverride = it
                refreshPaletteButtons()
                triggerPaletteLiveUpdateIfRunning()
            }
        }
        cpuWarmColorButton.setOnClickListener {
            showOptionalColorPicker(selectedCpuWarmColorOverride ?: DEFAULT_CPU_WARM_COLOR) {
                selectedCpuWarmColorOverride = it
                refreshPaletteButtons()
                triggerPaletteLiveUpdateIfRunning()
            }
        }
        cpuHotColorButton.setOnClickListener {
            showOptionalColorPicker(selectedCpuHotColorOverride ?: DEFAULT_CPU_HOT_COLOR) {
                selectedCpuHotColorOverride = it
                refreshPaletteButtons()
                triggerPaletteLiveUpdateIfRunning()
            }
        }

        // Long press resets the override to the built-in default gradient color.
        batteryLowColorButton.setOnLongClickListener {
            selectedBatteryLowColorOverride = null
            refreshPaletteButtons()
            triggerPaletteLiveUpdateIfRunning()
            true
        }
        batteryMidColorButton.setOnLongClickListener {
            selectedBatteryMidColorOverride = null
            refreshPaletteButtons()
            triggerPaletteLiveUpdateIfRunning()
            true
        }
        batteryHighColorButton.setOnLongClickListener {
            selectedBatteryHighColorOverride = null
            refreshPaletteButtons()
            triggerPaletteLiveUpdateIfRunning()
            true
        }
        cpuCoolColorButton.setOnLongClickListener {
            selectedCpuCoolColorOverride = null
            refreshPaletteButtons()
            triggerPaletteLiveUpdateIfRunning()
            true
        }
        cpuWarmColorButton.setOnLongClickListener {
            selectedCpuWarmColorOverride = null
            refreshPaletteButtons()
            triggerPaletteLiveUpdateIfRunning()
            true
        }
        cpuHotColorButton.setOnLongClickListener {
            selectedCpuHotColorOverride = null
            refreshPaletteButtons()
            triggerPaletteLiveUpdateIfRunning()
            true
        }

        refreshPaletteButtons()
    }

    private fun showOptionalColorPicker(initialColor: Int, onColorPicked: (Int) -> Unit) {
        colorPickerDialog.show(activity = this, initialColor = initialColor) { color ->
            onColorPicked(color)
        }
    }

    private fun triggerPaletteLiveUpdateIfRunning() {
        if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
            sendLiveUpdateToLedService()
        }
    }

    private fun refreshPaletteButtons() {
        batteryLowColorButton.setBackgroundColor(selectedBatteryLowColorOverride ?: DEFAULT_BATTERY_LOW_COLOR)
        batteryMidColorButton.setBackgroundColor(selectedBatteryMidColorOverride ?: DEFAULT_BATTERY_MID_COLOR)
        batteryHighColorButton.setBackgroundColor(selectedBatteryHighColorOverride ?: DEFAULT_BATTERY_HIGH_COLOR)
        cpuCoolColorButton.setBackgroundColor(selectedCpuCoolColorOverride ?: DEFAULT_CPU_COOL_COLOR)
        cpuWarmColorButton.setBackgroundColor(selectedCpuWarmColorOverride ?: DEFAULT_CPU_WARM_COLOR)
        cpuHotColorButton.setBackgroundColor(selectedCpuHotColorOverride ?: DEFAULT_CPU_HOT_COLOR)
    }

    private fun setupBrightnessSeekBar() {
        brightnessSeekBar.max = 255
        brightnessSeekBar.progress = selectedBrightness
        brightnessSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedBrightness = progress
                    if (LEDService.isRunning && fromUser && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                        sendLiveUpdateToLedService()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
    }

    private fun setupSpeedSeekBar() {
        speedSeekBar.max = 100
        speedSeekBar.progress = (selectedSpeed * 100).toInt()
        speedSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSpeed = progress / 100f
                    selectedSmoothness = selectedSpeed
                    if (fromUser) {
                        smoothnessSeekBar.progress = progress
                    }
                    if (LEDService.isRunning && fromUser && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                        sendLiveUpdateToLedService()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
    }

    private fun setupSmoothnessSeekBar() {
        smoothnessSeekBar.max = 100
        smoothnessSeekBar.progress = (selectedSmoothness * 100).toInt()
        smoothnessSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSmoothness = progress / 100f
                    selectedSpeed = selectedSmoothness
                    if (fromUser) {
                        speedSeekBar.progress = progress
                    }
                    if (LEDService.isRunning && fromUser && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                        sendLiveUpdateToLedService()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
    }

    private fun setupSensitivitySeekBar() {
        sensitivitySeekBar.max = 100
        sensitivitySeekBar.progress = (selectedSensitivity * 100).toInt()
        sensitivitySeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSensitivity = progress / 100f
                    if (LEDService.isRunning && fromUser && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                        sendLiveUpdateToLedService()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
    }

    private fun setupSaturationBoostSeekBar() {
        saturationBoostSeekBar.max = 100
        saturationBoostSeekBar.progress = (selectedSaturationBoost * 100).toInt()
        saturationBoostSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSaturationBoost = progress / 100f
                    if (LEDService.isRunning && fromUser && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                        sendLiveUpdateToLedService()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
    }

    private fun setupCustomSamplingSwitch() {
        customSamplingSwitch.isChecked = selectedUseCustomSampling
        customSamplingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return@setOnCheckedChangeListener
            selectedUseCustomSampling = isChecked
            if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupSingleColorSwitch() {
        singleColorSwitch.isChecked = selectedUseSingleColor
        singleColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return@setOnCheckedChangeListener
            selectedUseSingleColor = isChecked
            if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupBreatheWhenChargingSwitch() {
        breatheWhenChargingSwitch.isChecked = selectedBreatheWhenCharging
        breatheWhenChargingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return@setOnCheckedChangeListener
            selectedBreatheWhenCharging = isChecked
            if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupChargingSpeedIndicatorSwitch() {
        chargingSpeedIndicatorSwitch.isChecked = selectedIndicateChargingSpeed
        chargingSpeedIndicatorSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return@setOnCheckedChangeListener
            selectedIndicateChargingSpeed = isChecked
            if (isChecked && !selectedBreatheWhenCharging) {
                selectedBreatheWhenCharging = true
                breatheWhenChargingSwitch.isChecked = true
            }
            if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupFlashWhenReadySwitch() {
        flashWhenReadySwitch.isChecked = selectedFlashWhenReady
        flashWhenReadySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (serviceController.isServiceTransitioning || isUpdatingFromPreset) return@setOnCheckedChangeListener
            selectedFlashWhenReady = isChecked
            if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupAutoStartupSwitch() {
        autoStartupSwitch.isChecked = HeimdallStartupManager.isAutoStartEnabled(prefs)

        autoStartupSwitch.setOnCheckedChangeListener { _, isChecked ->
            HeimdallStartupManager.setAutoStartEnabled(prefs, isChecked)

            if (LEDService.isRunning && !serviceController.isServiceTransitioning) {
                serviceController.restartDebounced { createLedServiceIntent() }
            }
        }
    }

    private fun setupPluggedBatteryOverrideSwitch() {
        selectedBatteryOverrideWhenPlugged =
            prefs.getBoolean(PREF_BATTERY_OVERRIDE_WHEN_PLUGGED, false)
        pluggedBatteryOverrideSwitch.isChecked = selectedBatteryOverrideWhenPlugged

        pluggedBatteryOverrideSwitch.setOnCheckedChangeListener { _, isChecked ->
            selectedBatteryOverrideWhenPlugged = isChecked
            prefs.edit().putBoolean(PREF_BATTERY_OVERRIDE_WHEN_PLUGGED, isChecked).apply()

            if (LEDService.isRunning && !serviceController.isServiceTransitioning) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupPersistentNotificationSwitch() {
        selectedPersistentNotification =
            prefs.getBoolean(PREF_PERSISTENT_NOTIFICATION, true)
        persistentNotificationSwitch.isChecked = selectedPersistentNotification

        persistentNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            selectedPersistentNotification = isChecked
            prefs.edit().putBoolean(PREF_PERSISTENT_NOTIFICATION, isChecked).apply()

            if (LEDService.isRunning && !serviceController.isServiceTransitioning) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupAdaptiveBrightnessSwitch() {
        selectedAdaptiveBrightness = prefs.getBoolean(PREF_ADAPTIVE_BRIGHTNESS, false)
        adaptiveBrightnessSwitch.isChecked = selectedAdaptiveBrightness

        adaptiveBrightnessSwitch.setOnCheckedChangeListener { _, isChecked ->
            selectedAdaptiveBrightness = isChecked
            prefs.edit().putBoolean(PREF_ADAPTIVE_BRIGHTNESS, isChecked).apply()

            if (LEDService.isRunning && !serviceController.isServiceTransitioning) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun setupExternalApiSwitch() {
        externalApiSwitch.isChecked = ExternalApiGate.isMasterEnabled(prefs)
        externalApiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(ExternalApiGate.PREF_ENABLED, isChecked).apply()
            // Enabling requires the service to survive activity close, so next
            // start picks up the OR'd allowBackgroundRun. If running, restart
            // so the new flag takes effect without waiting for the user to
            // stop/start manually.
            if (isChecked && LEDService.isRunning && !serviceController.isServiceTransitioning) {
                serviceController.restartDebounced(needsMediaProjectionCheck = false) {
                    createLedServiceIntent()
                }
            }
        }
    }

    private fun setupThorScreenPreference() {
        val thorCard = findViewById<View>(R.id.thorSettingsCard) ?: return
        if (!DeviceInfo.isAynThor) {
            thorCard.visibility = View.GONE
            return
        }
        thorCard.visibility = View.VISIBLE

        thorLaunchBottomSwitch = findViewById(R.id.thorLaunchBottomSwitch)
        thorLaunchBottomSwitch?.isChecked = prefs.getBoolean(PREF_THOR_BOTTOM_SCREEN, false)
        thorLaunchBottomSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_THOR_BOTTOM_SCREEN, isChecked).apply()
            mainHandler.post { maybeRelaunchOnCorrectDisplay(ignoreRetryGuard = true) }
        }

        thorAmbientBottomSwitch = findViewById(R.id.thorAmbientBottomSwitch)
        thorAmbientBottomSwitch?.isChecked = prefs.getBoolean(PREF_THOR_AMBIENT_BOTTOM_SCREEN, false)
        thorAmbientBottomSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_THOR_AMBIENT_BOTTOM_SCREEN, isChecked).apply()
            // Invalidate cached screen-capture grant so next start targets the new display
            mediaProjectionResultCode = null
            mediaProjectionData = null
            if (LEDService.isRunning && selectedAnimationType.needsMediaProjection) {
                serviceController.restartDebounced(needsMediaProjectionCheck = true) { createLedServiceIntent() }
            }
        }
    }

    /**
     * If running on an AYN Thor and the current display does not match the saved
     * screen preference, relaunches the activity on the correct display and returns true.
     * The caller should return immediately when this returns true.
     */
    private fun maybeRelaunchOnCorrectDisplay(ignoreRetryGuard: Boolean = false): Boolean {
        if (!DeviceInfo.isAynThor) return false

        val attempt = intent.getIntExtra(EXTRA_DISPLAY_RELAUNCH_ATTEMPT, 0)
        if (!ignoreRetryGuard && attempt >= MAX_DISPLAY_RELAUNCH_ATTEMPTS) return false

        val useBottomScreen = prefs.getBoolean(PREF_THOR_BOTTOM_SCREEN, false)
        val currentDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY

        val targetDisplayId = if (useBottomScreen) {
            getThorSecondaryDisplayId() ?: Display.DEFAULT_DISPLAY
        } else {
            Display.DEFAULT_DISPLAY
        }

        if (currentDisplayId == targetDisplayId) return false

        val newIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_DISPLAY_RELAUNCH_ATTEMPT, attempt + 1)
        }
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = targetDisplayId
        startActivity(newIntent, options.toBundle())
        finish()
        return true
    }

    private fun getThorSecondaryDisplayId(): Int? {
        val displayManager = getSystemService(DisplayManager::class.java)
        return displayManager.displays
            .map { it.displayId }
            .filter { it != Display.DEFAULT_DISPLAY }
            .minOrNull()
    }

    private fun maybeAutoStartHeimdallOnLaunch() {
        if (!HeimdallStartupManager.isAutoStartEnabled(prefs) || LEDService.isRunning) return
        if (!checkNotificationPermission()) return
        if (selectedAnimationType.needsMediaProjection &&
            (mediaProjectionResultCode == null || mediaProjectionData == null)
        ) {
            return
        }

        serviceToggle.isChecked = true
    }

    private fun setupAppProfileFeature() {
        syncAppProfileSwitches(appProfileManager.isEnabled)
        updateManualPresetSwitchingUi(appProfileManager.isEnabled)

        appProfileSwitch.setOnCheckedChangeListener { _, isChecked ->
            handleAppProfileToggleChange(isChecked)
        }

        homeAppProfileSwitch.setOnCheckedChangeListener { _, isChecked ->
            handleAppProfileToggleChange(isChecked)
        }

        appProfileDefaultSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncingAppProfileDefaultSwitch || !::presetController.isInitialized) {
                return@setOnCheckedChangeListener
            }

            val selectedIndex = presetSpinner.selectedItemPosition
            val changed = presetController.setAppProfileDefaultPreset(selectedIndex, isChecked)
            if (!changed) {
                syncAppProfileDefaultSwitch()
                return@setOnCheckedChangeListener
            }

            refreshCoverFlowFromPresets()

            if (appProfileManager.isEnabled && LEDService.isRunning && !serviceController.isServiceTransitioning) {
                appProfileManager.resetLastForegroundPackage()
                requestImmediateAppProfileResolution()
            }
        }

        assignAppButton.setOnClickListener { showAppPickerDialog() }
        manageAppsButton.setOnClickListener { showMappingsDialog() }
    }

    private fun showAppPickerDialog() {
        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null)
        val listView = view.findViewById<ListView>(R.id.appListView)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val currentPresetName = getSelectedPresetName() ?: "Default"

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = resolveInfos.size
            override fun getItem(position: Int) = resolveInfos[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView ?: LayoutInflater.from(this@MainActivity)
                    .inflate(android.R.layout.activity_list_item, parent, false)
                val ri = resolveInfos[position]
                val icon = row.findViewById<ImageView>(android.R.id.icon)
                val text = row.findViewById<TextView>(android.R.id.text1)
                icon.setImageDrawable(ri.loadIcon(pm))
                text.text = ri.loadLabel(pm)
                text.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_text))
                return row
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = resolveInfos[position].activityInfo.packageName
            val appName = resolveInfos[position].loadLabel(pm)
            appProfileManager.setMapping(pkg, currentPresetName)
            Toast.makeText(this, "$appName -> $currentPresetName", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMappingsDialog() {
        val mappings = appProfileManager.getMappings().toList().toMutableList()
        val pm = packageManager

        if (mappings.isEmpty()) {
            Toast.makeText(this, "No app profiles assigned", Toast.LENGTH_SHORT).show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_app_mappings, null)
        val listView = view.findViewById<ListView>(R.id.mappingsListView)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Done", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        fun refreshAdapter() {
            listView.adapter = object : BaseAdapter() {
                override fun getCount() = mappings.size
                override fun getItem(position: Int) = mappings[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val row = convertView ?: LayoutInflater.from(this@MainActivity)
                        .inflate(android.R.layout.activity_list_item, parent, false)
                    val (pkg, presetName) = mappings[position]
                    val icon = row.findViewById<ImageView>(android.R.id.icon)
                    val text = row.findViewById<TextView>(android.R.id.text1)
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
                    } catch (_: Exception) { pkg }
                    icon.setImageDrawable(try {
                        pm.getApplicationIcon(pkg)
                    } catch (_: Exception) { null })
                    text.text = "$appName -> $presetName"
                    text.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_text))
                    return row
                }
            }
        }
        refreshAdapter()

        listView.setOnItemClickListener { _, _, position, _ ->
            val (pkg, _) = mappings[position]
            appProfileManager.removeMapping(pkg)
            mappings.removeAt(position)
            if (mappings.isEmpty()) {
                dialog.dismiss()
                Toast.makeText(this, "All mappings removed", Toast.LENGTH_SHORT).show()
            } else {
                refreshAdapter()
            }
        }

        dialog.show()
    }

    private fun setupPresetFeature() {
        val initialConfigPreset = LedPreset(
            name = "Initial",
            animationType = selectedAnimationType,
            performanceProfile = selectedProfile,
            color = selectedColor,
            rightColor = selectedRightColor,
            brightness = selectedBrightness,
            speed = selectedSpeed,
            smoothness = selectedSmoothness,
            sensitivity = selectedSensitivity,
            saturationBoost = selectedSaturationBoost,
            useCustomSampling = selectedUseCustomSampling,
            useSingleColor = selectedUseSingleColor,
            breatheWhenCharging = selectedBreatheWhenCharging,
            indicateChargingSpeed = selectedIndicateChargingSpeed,
            flashWhenReady = selectedFlashWhenReady,
            batteryLowColorOverride = selectedBatteryLowColorOverride,
            batteryMidColorOverride = selectedBatteryMidColorOverride,
            batteryHighColorOverride = selectedBatteryHighColorOverride,
            cpuCoolColorOverride = selectedCpuCoolColorOverride,
            cpuWarmColorOverride = selectedCpuWarmColorOverride,
            cpuHotColorOverride = selectedCpuHotColorOverride
        )

        presetController = PresetController(
            activity = this,
            prefs = prefs,
            presetSpinner = presetSpinner,
            saveAsNewButton = savePresetButton,
            modifyButton = modifyPresetButton,
            deleteButton = deletePresetButton,
            getCurrentConfig = {
                LedPreset(
                    name = "",
                    animationType = selectedAnimationType,
                    performanceProfile = selectedProfile,
                    color = selectedColor,
                    rightColor = selectedRightColor,
                    brightness = selectedBrightness,
                    speed = selectedSpeed,
                    smoothness = selectedSmoothness,
                    sensitivity = selectedSensitivity,
                    saturationBoost = selectedSaturationBoost,
                    useCustomSampling = selectedUseCustomSampling,
                    useSingleColor = selectedUseSingleColor,
                    breatheWhenCharging = selectedBreatheWhenCharging,
                    indicateChargingSpeed = selectedIndicateChargingSpeed,
                    flashWhenReady = selectedFlashWhenReady,
                    batteryLowColorOverride = selectedBatteryLowColorOverride,
                    batteryMidColorOverride = selectedBatteryMidColorOverride,
                    batteryHighColorOverride = selectedBatteryHighColorOverride,
                    cpuCoolColorOverride = selectedCpuCoolColorOverride,
                    cpuWarmColorOverride = selectedCpuWarmColorOverride,
                    cpuHotColorOverride = selectedCpuHotColorOverride
                )
            },
            applyPresetToUi = { preset ->
                selectedAnimationType = preset.animationType
                selectedProfile = preset.performanceProfile
                selectedColor = preset.color
                selectedRightColor = preset.rightColor
                selectedBrightness = preset.brightness
                selectedSpeed = preset.speed
                selectedSmoothness = preset.smoothness
                selectedSensitivity = preset.sensitivity
                selectedSaturationBoost = preset.saturationBoost
                selectedUseCustomSampling = preset.useCustomSampling
                selectedUseSingleColor = preset.useSingleColor
                selectedBreatheWhenCharging = preset.breatheWhenCharging
                selectedIndicateChargingSpeed = preset.indicateChargingSpeed
                selectedFlashWhenReady = preset.flashWhenReady
                selectedBatteryLowColorOverride = preset.batteryLowColorOverride
                selectedBatteryMidColorOverride = preset.batteryMidColorOverride
                selectedBatteryHighColorOverride = preset.batteryHighColorOverride
                selectedCpuCoolColorOverride = preset.cpuCoolColorOverride
                selectedCpuWarmColorOverride = preset.cpuWarmColorOverride
                selectedCpuHotColorOverride = preset.cpuHotColorOverride

                val types = LedAnimationType.values().toList()
                animationSpinner.setSelection(types.indexOf(selectedAnimationType).coerceAtLeast(0))

                val profiles = PerformanceProfile.values().toList()
                profileSpinner.setSelection(profiles.indexOf(selectedProfile).coerceAtLeast(0))

                colorButton.setBackgroundColor(selectedColor)
                rightColorButton.setBackgroundColor(selectedRightColor)
                brightnessSeekBar.progress = selectedBrightness
                val progress = (selectedSpeed * 100).toInt()
                speedSeekBar.progress = progress
                smoothnessSeekBar.progress = progress
                sensitivitySeekBar.progress = (selectedSensitivity * 100).toInt()
                saturationBoostSeekBar.progress = (selectedSaturationBoost * 100).toInt()
                customSamplingSwitch.isChecked = selectedUseCustomSampling
                singleColorSwitch.isChecked = selectedUseSingleColor
                breatheWhenChargingSwitch.isChecked = selectedBreatheWhenCharging
                chargingSpeedIndicatorSwitch.isChecked = selectedIndicateChargingSpeed
                flashWhenReadySwitch.isChecked = selectedFlashWhenReady
                refreshPaletteButtons()
                syncAppProfileDefaultSwitch()

                updateParameterVisibility()
            },
            markIsUpdatingFromPreset = { value ->
                isUpdatingFromPreset = value
            },
            isUpdatingFromPreset = {
                isUpdatingFromPreset
            },
            onPresetApplied = {
                if (LEDService.isRunning) {
                    // When app-profile mode is active, animation is determined by
                    // foreground-app mappings, not the UI selection.  Do NOT prompt
                    // for screen-capture permission from the UI.
                    if (::appProfileManager.isInitialized && appProfileManager.isEnabled) {
                        // Just keep the service running; the periodic check will
                        // resolve the correct preset.
                    } else if (selectedAnimationType.needsMediaProjection) {
                        if (mediaProjectionResultCode == null || mediaProjectionData == null) {
                            handleMediaProjectionRequirement()
                        } else {
                            startService(createLedServiceIntent())
                        }
                    } else {
                        startService(createLedServiceIntent())
                    }
                }

                refreshCoverFlowFromPresets()
            },
            onRequestCustomPresetImage = { index ->
                launchPresetImagePicker(index)
            },
            onPresetRenamed = { oldName, newName ->
                appProfileManager.renamePresetInMappings(oldName, newName)
            }
        )

        presetController.init(initialConfigPreset)
        refreshCoverFlowFromPresets()
        syncAppProfileDefaultSwitch()

        exportPresetsButton.setOnClickListener {
            showBackupCategoryDialog(
                title = "BACKUP CATEGORIES",
                subtitle = "Choose what to include in the backup",
                confirmLabel = "BACKUP"
            ) { options ->
                pendingBackupExportOptions = options
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportPresetsLauncher.launch("bifrost_backup_$timestamp.bifrost_backup")
            }
        }

        importPresetsButton.setOnClickListener {
            showBackupCategoryDialog(
                title = "RESTORE CATEGORIES",
                subtitle = "Choose what to restore from archive",
                confirmLabel = "RESTORE"
            ) { options ->
                pendingBackupImportOptions = options
                importPresetsLauncher.launch(arrayOf("*/*"))
            }
        }

        exportThemeButton.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            exportThemeLauncher.launch("bifrost_theme_$timestamp.bifrost_theme")
        }

        importThemeButton.setOnClickListener {
            importThemeLauncher.launch(arrayOf("*/*"))
        }

        // If the app switching feature is already enabled, start syncing the UI after presets load.
        if (::appProfileManager.isInitialized && appProfileManager.isEnabled) {
            startAppProfileSync()
        }
    }

    private fun exportPresetBundle(uri: Uri, options: BackupArchiveTransfer.CategoryOptions) {
        val result = runCatching {
            BackupArchiveTransfer.exportToUri(context = this, uri = uri, options = options)
        }.getOrElse {
            Toast.makeText(this, "Backup export failed", Toast.LENGTH_LONG).show()
            return
        }

        if (result.warnings.isEmpty()) {
            Toast.makeText(
                this,
                "Backup saved (${result.preferenceCount} settings, ${result.iconCount} images)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val warningText = result.warnings.joinToString("\n")
        BifrostAlertDialog().show(
            activity = this,
            title = "BACKUP COMPLETED",
            subtitle = "Saved backup with ${result.warnings.size} warning(s)",
            body = warningText,
            positiveLabelResId = R.string.alert_action_ok,
            negativeLabelResId = null,
            cancelable = true,
            onConfirm = {}
        )
    }

    private fun importPresetBundle(uri: Uri, options: BackupArchiveTransfer.CategoryOptions) {
        val backupResult = runCatching {
            BackupArchiveTransfer.importFromUri(this, uri, options = options)
        }.getOrElse {
            BifrostAlertDialog().show(
                activity = this,
                title = "IMPORT FAILED",
                subtitle = "Selected file could not be imported",
                body = it.message,
                positiveLabelResId = R.string.alert_action_ok,
                negativeLabelResId = null,
                cancelable = true,
                onConfirm = {}
            )
            return
        }

        if (backupResult.errors.isEmpty()) {
            val categorySummary = describeBackupCategories(backupResult.appliedOptions)
            val warningText = if (backupResult.warnings.isEmpty()) {
                "Categories: $categorySummary\n\nRestore completed successfully."
            } else {
                "Categories: $categorySummary\n\nWarnings:\n${backupResult.warnings.joinToString("\n")}"
            }

            BifrostAlertDialog().show(
                activity = this,
                title = "RESTORE COMPLETED",
                subtitle = "Restored ${backupResult.preferenceCount} settings and ${backupResult.iconCount} images",
                body = warningText,
                positiveLabelResId = R.string.alert_action_ok,
                negativeLabelResId = null,
                cancelable = true,
                onConfirm = {
                    recreate()
                }
            )
            return
        }

        // Fallback: allow importing legacy preset bundles through the same picker.
        val result = runCatching {
            PresetArchiveTransfer.importFromUri(this, uri)
        }.getOrElse {
            val mergedErrorText = buildString {
                append(backupResult.errors.joinToString("\n"))
                if (isNotEmpty()) append("\n\n")
                append("Legacy preset import failed")
                it.message?.let { message ->
                    append(": ")
                    append(message)
                }
            }
            BifrostAlertDialog().show(
                activity = this,
                title = "IMPORT FAILED",
                subtitle = "Selected file is not a valid backup archive",
                body = mergedErrorText,
                positiveLabelResId = R.string.alert_action_ok,
                negativeLabelResId = null,
                cancelable = true,
                onConfirm = {}
            )
            return
        }

        if (result.presets.isEmpty()) {
            showImportReport(result)
            return
        }

        BifrostAlertDialog().show(
            activity = this,
            title = "IMPORT MODE",
            subtitle = "Choose how to apply imported presets",
            body = "Replace everything: current presets are overwritten.\nAdd: imported presets are appended to existing ones.",
            positiveLabelResId = R.string.action_replace,
            negativeLabelResId = R.string.action_add,
            cancelable = true,
            onConfirm = {
                applyImportedBundle(result, replaceEverything = true)
            },
            onCancel = {
                applyImportedBundle(result, replaceEverything = false)
            }
        )
    }

    private fun showBackupCategoryDialog(
        title: String,
        subtitle: String,
        confirmLabel: String,
        onConfirm: (BackupArchiveTransfer.CategoryOptions) -> Unit
    ) {
        val labels = arrayOf("Themes", "Profiles", "Images")
        val checked = booleanArrayOf(true, true, true)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(subtitle)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(confirmLabel) { _, _ ->
                val options = BackupArchiveTransfer.CategoryOptions(
                    themes = checked[0],
                    profiles = checked[1],
                    images = checked[2]
                )
                if (!options.hasAtLeastOneCategory()) {
                    Toast.makeText(this, "Select at least one category", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onConfirm(options)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun describeBackupCategories(options: BackupArchiveTransfer.CategoryOptions): String {
        val selected = mutableListOf<String>()
        if (options.themes) selected += "Themes"
        if (options.profiles) selected += "Profiles"
        if (options.images) selected += "Images"
        return if (selected.isEmpty()) "None" else selected.joinToString(" + ")
    }

    private fun applyImportedBundle(
        result: PresetArchiveTransfer.ImportResult,
        replaceEverything: Boolean
    ) {
        val presetsApplied = if (replaceEverything) {
            presetController.replaceAllPresetsFromImport(result.presets)
        } else {
            presetController.appendPresetsFromImport(result.presets)
        }

        if (presetsApplied) {
            if (replaceEverything) {
                appProfileManager.replaceMappings(result.mappings)
            } else {
                val mergedMappings = appProfileManager.getMappings().toMutableMap().apply {
                    putAll(result.mappings)
                }
                appProfileManager.replaceMappings(mergedMappings)
            }

            refreshCoverFlowFromPresets()

            if (LEDService.isRunning && !serviceController.isServiceTransitioning) {
                if (appProfileManager.isEnabled) {
                    appProfileManager.resetLastForegroundPackage()
                    requestImmediateAppProfileResolution()
                } else {
                    startService(createLedServiceIntent())
                }
            }
        }

        showImportReport(result)
    }

    private fun exportThemeBundle(uri: Uri) {
        val result = runCatching {
            ThemeArchiveTransfer.exportToUri(
                context = this,
                uri = uri,
                themeId = selectedUiTheme.id,
                coloredLogoEnabled = isColoredLogoEnabled
            )
        }.getOrElse {
            Toast.makeText(this, "Theme export failed", Toast.LENGTH_LONG).show()
            return
        }

        if (result.warnings.isEmpty()) {
            Toast.makeText(
                this,
                "Theme saved (${selectedUiTheme.label})",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        BifrostAlertDialog().show(
            activity = this,
            title = "THEME EXPORT COMPLETED",
            subtitle = "Saved ${selectedUiTheme.label} with ${result.warnings.size} warning(s)",
            body = result.warnings.joinToString("\n"),
            positiveLabelResId = R.string.alert_action_ok,
            negativeLabelResId = null,
            cancelable = true,
            onConfirm = {}
        )
    }

    private fun importThemeBundle(uri: Uri) {
        val result = runCatching {
            ThemeArchiveTransfer.importFromUri(this, uri)
        }.getOrElse {
            BifrostAlertDialog().show(
                activity = this,
                title = "THEME IMPORT FAILED",
                subtitle = "Selected file could not be imported",
                body = it.message,
                positiveLabelResId = R.string.alert_action_ok,
                negativeLabelResId = null,
                cancelable = true,
                onConfirm = {}
            )
            return
        }

        val importedTheme = builtInThemes.firstOrNull { it.id == result.themeId }
        val errors = result.errors.toMutableList()
        if (importedTheme == null) {
            errors += if (result.themeId.isNullOrBlank()) {
                "Theme bundle does not contain a supported theme."
            } else {
                "Theme '${result.themeId}' is not available in this build."
            }
        }

        if (errors.isNotEmpty()) {
            BifrostAlertDialog().show(
                activity = this,
                title = "THEME IMPORT FAILED",
                subtitle = "Selected file is not a valid supported theme bundle",
                body = (errors + result.warnings).joinToString("\n"),
                positiveLabelResId = R.string.alert_action_ok,
                negativeLabelResId = null,
                cancelable = true,
                onConfirm = {}
            )
            return
        }

        applyImportedTheme(importedTheme!!, result.coloredLogoEnabled)

        val body = if (result.warnings.isEmpty()) {
            "Theme applied successfully."
        } else {
            "Warnings:\n${result.warnings.joinToString("\n")}"
        }

        BifrostAlertDialog().show(
            activity = this,
            title = "THEME IMPORT COMPLETED",
            subtitle = "Applied ${importedTheme.label}",
            body = body,
            positiveLabelResId = R.string.alert_action_ok,
            negativeLabelResId = null,
            cancelable = true,
            onConfirm = {}
        )
    }

    private fun applyImportedTheme(theme: UiTheme, coloredLogoEnabled: Boolean) {
        prefs.edit()
            .putString(PREF_SELECTED_UI_THEME, theme.id)
            .putBoolean(PREF_COLORED_LOGO_ENABLED, coloredLogoEnabled)
            .apply()

        isColoredLogoEnabled = coloredLogoEnabled
        val themeIndex = builtInThemes.indexOfFirst { it.id == theme.id }.takeIf { it >= 0 } ?: 0
        themeSpinner.setSelection(themeIndex, false)
        applySelectedTheme(theme, persistSelection = false)
        if (coloredLogoSwitch.isChecked != coloredLogoEnabled) {
            coloredLogoSwitch.isChecked = coloredLogoEnabled
        } else {
            applyHeaderLogoPreference()
        }
    }

    private fun showImportReport(result: PresetArchiveTransfer.ImportResult) {
        val totalIssues = result.errors.size + result.warnings.size
        val subtitle = if (result.presets.isEmpty()) {
            "No presets imported"
        } else {
            "Imported ${result.presets.size} presets"
        }

        val body = buildString {
            if (result.errors.isNotEmpty()) {
                append("Errors:\n")
                append(result.errors.joinToString("\n"))
            }
            if (result.warnings.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Warnings:\n")
                append(result.warnings.joinToString("\n"))
            }
            if (isEmpty()) {
                append("Import completed successfully.")
            }
        }

        BifrostAlertDialog().show(
            activity = this,
            title = if (totalIssues == 0) "IMPORT COMPLETED" else "IMPORT COMPLETED WITH NOTES",
            subtitle = subtitle,
            body = body,
            positiveLabelResId = R.string.alert_action_ok,
            negativeLabelResId = null,
            cancelable = true,
            onConfirm = {}
        )
    }

    private fun updateParameterVisibility() {
        val needsColor = selectedAnimationType.needsColorSelection
        val needsProfile = selectedAnimationType.needsMediaProjection
        val needsSpeed = selectedAnimationType.supportsSpeed
        val needsSmoothness = selectedAnimationType.supportsSmoothness
        val needsSensitivity = selectedAnimationType.supportsAudioSensitivity
        val needsSaturationBoost = selectedAnimationType == LedAnimationType.AMBIENT ||
                selectedAnimationType == LedAnimationType.AMBIAURORA
        val needsCustomSampling = selectedAnimationType == LedAnimationType.AMBIENT ||
                selectedAnimationType == LedAnimationType.AMBIAURORA
        val needsSingleColor = selectedAnimationType == LedAnimationType.AMBIENT ||
                selectedAnimationType == LedAnimationType.AMBIAURORA
        val needsBreatheWhenCharging = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR
        val needsChargingSpeedIndicator = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR &&
            selectedBreatheWhenCharging
        val needsFlashWhenReady = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR
        val needsBatteryPalette = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR
        val needsCpuPalette = selectedAnimationType == LedAnimationType.CPU_TEMPERATURE

        val supportsBrightness = true

        colorCard.visibility = if (needsColor || supportsBrightness) View.VISIBLE else View.GONE

        if (colorCard.visibility == View.VISIBLE) {
            colorButton.visibility = if (needsColor) View.VISIBLE else View.GONE
            rightColorButton.visibility = if (needsColor) View.VISIBLE else View.GONE

            val colorCardTitle = findViewById<TextView>(R.id.colorCardTitle)
            if (needsColor) {
                colorCardTitle?.text = "COLOR & INTENSITY"
            } else {
                colorCardTitle?.text = "INTENSITY"
            }
        }

        performanceCard.visibility = if (needsProfile) View.VISIBLE else View.GONE
        animationCard.visibility = if (needsSpeed || needsSmoothness || needsSensitivity || needsSaturationBoost || needsCustomSampling || needsSingleColor || needsBreatheWhenCharging || needsChargingSpeedIndicator || needsFlashWhenReady || needsBatteryPalette || needsCpuPalette) View.VISIBLE else View.GONE

        if (animationCard.visibility == View.VISIBLE) {
            val speedLabel = findViewById<View>(R.id.speedLabel)
            val smoothnessLabel = findViewById<View>(R.id.smoothnessLabel)
            val sensitivityLabel = findViewById<View>(R.id.sensitivityLabel)
            val saturationBoostLabel = findViewById<View>(R.id.saturationBoostLabel)
            val customSamplingLabel = findViewById<View>(R.id.customSamplingLabel)
            val singleColorLabel = findViewById<View>(R.id.singleColorLabel)
            val breatheWhenChargingRow = findViewById<View>(R.id.breatheWhenChargingRow)
            val chargingSpeedIndicatorRow = findViewById<View>(R.id.chargingSpeedIndicatorRow)
            val flashWhenReadyRow = findViewById<View>(R.id.flashWhenReadyRow)
            val batteryPaletteRow = findViewById<View>(R.id.batteryPaletteRow)
            val cpuPaletteRow = findViewById<View>(R.id.cpuPaletteRow)
            val ignoreletterbox = findViewById<View>(R.id.ignoreletterbox)
            var bothSticksSameColor = findViewById<View>(R.id.bothSticksSameColor)

            speedLabel?.visibility = if (needsSpeed || needsSmoothness) View.VISIBLE else View.GONE
            speedSeekBar.visibility = if (needsSpeed || needsSmoothness) View.VISIBLE else View.GONE

            smoothnessLabel?.visibility = View.GONE
            smoothnessSeekBar.visibility = View.GONE

            sensitivityLabel?.visibility = if (needsSensitivity) View.VISIBLE else View.GONE
            sensitivitySeekBar.visibility = if (needsSensitivity) View.VISIBLE else View.GONE

            saturationBoostLabel?.visibility = if (needsSaturationBoost) View.VISIBLE else View.GONE
            saturationBoostSeekBar.visibility = if (needsSaturationBoost) View.VISIBLE else View.GONE

            customSamplingLabel?.visibility = if (needsCustomSampling) View.VISIBLE else View.GONE
            customSamplingSwitch.visibility = if (needsCustomSampling) View.VISIBLE else View.GONE
            ignoreletterbox.visibility = if (needsCustomSampling) View.VISIBLE else View.GONE

            singleColorLabel?.visibility = if (needsSingleColor) View.VISIBLE else View.GONE
            singleColorSwitch.visibility = if (needsSingleColor) View.VISIBLE else View.GONE
            bothSticksSameColor.visibility = if (needsSingleColor) View.VISIBLE else View.GONE

            breatheWhenChargingRow?.visibility = if (needsBreatheWhenCharging) View.VISIBLE else View.GONE
            chargingSpeedIndicatorRow?.visibility = if (needsChargingSpeedIndicator) View.VISIBLE else View.GONE
            flashWhenReadyRow?.visibility = if (needsFlashWhenReady) View.VISIBLE else View.GONE
            batteryPaletteRow?.visibility = if (needsBatteryPalette) View.VISIBLE else View.GONE
            cpuPaletteRow?.visibility = if (needsCpuPalette) View.VISIBLE else View.GONE
        }
    }

    private fun showColorPicker(isRight: Boolean = false) {
        colorPickerDialog.show(
            activity = this,
            initialColor = if (isRight) selectedRightColor else selectedColor
        ) { color ->
            if (isRight) {
                selectedRightColor = color
                rightColorButton.setBackgroundColor(selectedRightColor)
            } else {
                selectedColor = color
                colorButton.setBackgroundColor(selectedColor)
            }
            if (LEDService.isRunning && !serviceController.isServiceTransitioning && !isUpdatingFromPreset) {
                sendLiveUpdateToLedService()
            }
        }
    }

    private fun enableRainbowBackground(enabled: Boolean) {
        if (enabled) {
            if (rainbowDrawable == null) rainbowDrawable = AnimatedRainbowDrawable()
            systemStatusContainer.background = rainbowDrawable
            rainbowDrawable?.start()
        } else {
            rainbowDrawable?.stop()
            systemStatusContainer.setBackgroundResource(R.drawable.card_glow_bg)
        }
    }

    private fun checkRagnarokWarningAndRestart(needsMediaProjectionCheck: Boolean = false) {
        val presetName = getSelectedPresetName()
        val preset = presetController.getPresets().firstOrNull { it.name == presetName }

        val mustShow =
            selectedProfile == PerformanceProfile.RAGNAROK &&
                    selectedAnimationType.needsMediaProjection &&
                    preset?.ragnarokAccepted != true

        if (mustShow) {
            ragnarokWarningDialog.show(
                activity = this,
                onConfirm = {
                    presetController.markRagnarokAccepted(presetName)
                    serviceController.restartDebounced(needsMediaProjectionCheck) {
                        createLedServiceIntent()
                    }
                },
                onCancel = {
                    val profiles = PerformanceProfile.values().toList()
                    val currentIndex = profiles.indexOf(selectedProfile)
                    profileSpinner.setSelection(currentIndex)
                }
            )
        } else {
            serviceController.restartDebounced(needsMediaProjectionCheck) {
                createLedServiceIntent()
            }
        }
    }

    private fun handleMediaProjectionRequirement() {
        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return
        }
        requestScreenCapturePermission()
    }

    private fun handleStartWithCurrentSelection() {
        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return
        }
        // When app-profile mode is active the animation is determined by
        // foreground-app mappings, not the UI selection.  Skip the media-
        // projection gate – if the resolved preset needs it later, the
        // service will show a notification prompting the user to grant it.
        if (appProfileManager.isEnabled) {
            serviceController.startDebounced { createLedServiceIntent() }
            return
        }
        if (selectedAnimationType.needsMediaProjection) {
            if (mediaProjectionResultCode != null && mediaProjectionData != null) {
                serviceController.startDebounced { createLedServiceIntent() }
            } else {
                requestScreenCapturePermission()
            }
        } else {
            serviceController.startDebounced { createLedServiceIntent() }
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestScreenCapturePermission() {
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun getAmbientTargetDisplayId(): Int {
        if (!DeviceInfo.isAynThor) return Display.DEFAULT_DISPLAY
        if (!prefs.getBoolean(PREF_THOR_AMBIENT_BOTTOM_SCREEN, false)) return Display.DEFAULT_DISPLAY
        return getThorSecondaryDisplayId() ?: Display.DEFAULT_DISPLAY
    }

    private fun createLedServiceIntent(): Intent {
        return Intent(this, LEDService::class.java).apply {
            putExtra("animationType", selectedAnimationType.name)
            putExtra("performanceProfile", selectedProfile.name)
            putExtra("animationColor", selectedColor)
            putExtra("animationRightColor", selectedRightColor)
            putExtra("brightness", selectedBrightness)
            putExtra("speed", selectedSpeed)
            putExtra("smoothness", selectedSmoothness)
            putExtra("sensitivity", selectedSensitivity)
            putExtra("saturationBoost", selectedSaturationBoost)
            putExtra("useCustomSampling", selectedUseCustomSampling)
            putExtra("useSingleColor", selectedUseSingleColor)
            putExtra("breatheWhenCharging", selectedBreatheWhenCharging)
            putExtra("indicateChargingSpeed", selectedIndicateChargingSpeed)
            putExtra("flashWhenReady", selectedFlashWhenReady)
            putOptionalColorExtra(this, EXTRA_BATTERY_LOW_COLOR_OVERRIDE, selectedBatteryLowColorOverride)
            putOptionalColorExtra(this, EXTRA_BATTERY_MID_COLOR_OVERRIDE, selectedBatteryMidColorOverride)
            putOptionalColorExtra(this, EXTRA_BATTERY_HIGH_COLOR_OVERRIDE, selectedBatteryHighColorOverride)
            putOptionalColorExtra(this, EXTRA_CPU_COOL_COLOR_OVERRIDE, selectedCpuCoolColorOverride)
            putOptionalColorExtra(this, EXTRA_CPU_WARM_COLOR_OVERRIDE, selectedCpuWarmColorOverride)
            putOptionalColorExtra(this, EXTRA_CPU_HOT_COLOR_OVERRIDE, selectedCpuHotColorOverride)
            putExtra(
                LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                selectedBatteryOverrideWhenPlugged
            )
            putExtra(
                LEDService.EXTRA_PERSISTENT_NOTIFICATION,
                selectedPersistentNotification
            )
            putExtra(
                LEDService.EXTRA_ADAPTIVE_BRIGHTNESS,
                selectedAdaptiveBrightness
            )
            putExtra("ambientDisplayId", getAmbientTargetDisplayId())
            putExtra(
                LEDService.EXTRA_ALLOW_BACKGROUND_RUN,
                HeimdallStartupManager.isAutoStartEnabled(prefs) ||
                    ExternalApiGate.isMasterEnabled(prefs)
            )
            // When app profile mode is active, always include MP data if available,
            // regardless of the UI-selected animation type.  The actual animation is
            // determined by foreground-app mappings and may need MP even when the
            // UI-visible preset does not.
            val shouldIncludeMP = if (::appProfileManager.isInitialized && appProfileManager.isEnabled) {
                mediaProjectionResultCode != null && mediaProjectionData != null
            } else {
                selectedAnimationType.needsMediaProjection
            }
            if (shouldIncludeMP) {
                putExtra("resultCode", mediaProjectionResultCode)
                putExtra("data", mediaProjectionData)
            }
        }
    }

    private fun sendLiveUpdateToLedService() {
        if (!LEDService.isRunning) return
        val intent = Intent(this, LEDService::class.java).apply {
            action = LEDService.ACTION_UPDATE_PARAMS
            putExtra("animationColor", selectedColor)
            putExtra("animationRightColor", selectedRightColor)
            putExtra("brightness", selectedBrightness)
            putExtra("speed", selectedSpeed)
            putExtra("smoothness", selectedSmoothness)
            putExtra("sensitivity", selectedSensitivity)
            putExtra("saturationBoost", selectedSaturationBoost)
            putExtra("useCustomSampling", selectedUseCustomSampling)
            putExtra("useSingleColor", selectedUseSingleColor)
            putExtra("breatheWhenCharging", selectedBreatheWhenCharging)
            putExtra("indicateChargingSpeed", selectedIndicateChargingSpeed)
            putExtra("flashWhenReady", selectedFlashWhenReady)
            putOptionalColorExtra(this, EXTRA_BATTERY_LOW_COLOR_OVERRIDE, selectedBatteryLowColorOverride)
            putOptionalColorExtra(this, EXTRA_BATTERY_MID_COLOR_OVERRIDE, selectedBatteryMidColorOverride)
            putOptionalColorExtra(this, EXTRA_BATTERY_HIGH_COLOR_OVERRIDE, selectedBatteryHighColorOverride)
            putOptionalColorExtra(this, EXTRA_CPU_COOL_COLOR_OVERRIDE, selectedCpuCoolColorOverride)
            putOptionalColorExtra(this, EXTRA_CPU_WARM_COLOR_OVERRIDE, selectedCpuWarmColorOverride)
            putOptionalColorExtra(this, EXTRA_CPU_HOT_COLOR_OVERRIDE, selectedCpuHotColorOverride)
            putExtra(
                LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                selectedBatteryOverrideWhenPlugged
            )
            putExtra(
                LEDService.EXTRA_PERSISTENT_NOTIFICATION,
                selectedPersistentNotification
            )
            putExtra(
                LEDService.EXTRA_ADAPTIVE_BRIGHTNESS,
                selectedAdaptiveBrightness
            )
        }
        startService(intent)
    }

    private fun putOptionalColorExtra(intent: Intent, key: String, value: Int?) {
        intent.putExtra(key, value ?: COLOR_OVERRIDE_UNSET)
    }

    private fun showFirstLaunchAlertIfNeeded() {
        val shown = prefs.getBoolean(PREF_FIRST_LAUNCH_ALERT_SHOWN, false)
        if (!shown) {
            val dialog = BifrostAlertDialog()
            dialog.show(
                activity = this,
                title = getString(R.string.beta_alert_title),
                subtitle = getString(R.string.beta_alert_subtitle),
                body = getString(R.string.beta_alert_body),
                positiveLabelResId = R.string.alert_action_ok,
                negativeLabelResId = null,
                cancelable = false,
                onConfirm = {
                    prefs.edit().putBoolean(PREF_FIRST_LAUNCH_ALERT_SHOWN, true).apply()
                }
            )
        }
    }

    data class AssignedAppVisual(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private fun resolveAssignedAppVisuals(presetName: String): List<AssignedAppVisual> {
        val mappings = appProfileManager.getMappings()
        val pm = packageManager

        return mappings
            .asSequence()
            .filter { (_, mappedPresetName) -> mappedPresetName == presetName }
            .map { (packageName, _) -> packageName }
            .distinct()
            .mapNotNull { packageName ->
                val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return@mapNotNull null
                val appName = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()
                    ?: packageName
                val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull() ?: return@mapNotNull null
                AssignedAppVisual(packageName = packageName, appName = appName, icon = icon)
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    private fun maybeShowAppProfileInfoDialog(isEnabled: Boolean) {
        if (!isEnabled) return
        if (prefs.getBoolean(PREF_APP_PROFILE_INFO_SHOWN, false)) return

        val title = resolveStringByName("app_profile_info_title", "APP PROFILE MODE")
        val subtitle = resolveStringByName(
            "app_profile_info_subtitle",
            "Animations now switch automatically based on the foreground app."
        )
        val body = resolveStringByName(
            "app_profile_info_body",
            "In this mode, swiping presets will not change the running animation. Assign apps to presets in settings to control what plays automatically."
        )

        BifrostAlertDialog().show(
            activity = this,
            title = title,
            subtitle = subtitle,
            body = body,
            positiveLabelResId = R.string.alert_action_ok,
            negativeLabelResId = null,
            cancelable = true,
            onConfirm = {
                prefs.edit().putBoolean(PREF_APP_PROFILE_INFO_SHOWN, true).apply()
            }
        )
    }

    private fun resolveStringByName(resourceName: String, fallback: String): String {
        val resId = resources.getIdentifier(resourceName, "string", packageName)
        if (resId == 0) return fallback
        return runCatching { getString(resId) }.getOrDefault(fallback)
    }
}

