package com.moonbench.bifrost

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.Manifest
import android.app.ActivityOptions
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.services.AppProfileManager
import com.moonbench.bifrost.services.HeimdallStartupManager
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.ServiceController
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

class MainActivity : AppCompatActivity() {

    private lateinit var serviceToggle: SwitchMaterial
    private lateinit var autoStartupSwitch: SwitchMaterial
    private lateinit var pluggedBatteryOverrideSwitch: SwitchMaterial
    private lateinit var persistentNotificationSwitch: SwitchMaterial
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var animationSpinner: Spinner
    private lateinit var profileSpinner: Spinner
    private lateinit var presetSpinner: Spinner
    private lateinit var savePresetButton: MaterialButton
    private lateinit var modifyPresetButton: MaterialButton
    private lateinit var deletePresetButton: MaterialButton
    private lateinit var colorButton: MaterialButton
    private lateinit var rightColorButton: MaterialButton
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
    private lateinit var assignAppButton: MaterialButton
    private lateinit var manageAppsButton: MaterialButton
    private lateinit var settingsOverlay: View
    private lateinit var homeContainer: View
    private lateinit var homeSettingsButton: MaterialButton
    private lateinit var closeSettingsButton: MaterialButton
    private lateinit var presetCoverFlowScroll: LockableHorizontalScrollView
    private lateinit var presetCoverFlowContainer: LinearLayout
    private lateinit var activePresetInfoCard: MaterialCardView
    private lateinit var activePresetNameText: TextView
    private lateinit var activePresetStatusBadge: TextView
    private lateinit var activePresetAnimationText: TextView
    private lateinit var activePresetProfileText: TextView
    private lateinit var homePresetHintText: TextView
    private lateinit var modeCard: MaterialCardView
    private lateinit var colorCard: MaterialCardView
    private lateinit var animationCard: MaterialCardView
    private lateinit var performanceCard: MaterialCardView
    private lateinit var systemStatusContainer: View
    private lateinit var bifrostLogoView: ImageView
    private lateinit var bifrostTitleText: TextView
    private var thorLaunchBottomSwitch: SwitchMaterial? = null
    private var thorAmbilightBottomSwitch: SwitchMaterial? = null

    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }

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
        private const val APP_PROFILE_SYNC_INTERVAL_MS = 1200L
        private const val PREF_KEY_LAST_PRESET = "last_preset_name"

        // Note: this key is shared with PresetController for backward compatibility.
        // Do not change unless updating persistence logic across components.

        private const val TITLE_INTRO_ANIMATION_MS = 3200L
        private const val PREF_FIRST_LAUNCH_ALERT_SHOWN = "first_launch_alert_shown"
        private const val PREF_THOR_BOTTOM_SCREEN = "thor_bottom_screen"
        private const val PREF_THOR_AMBILIGHT_BOTTOM_SCREEN = "thor_ambilight_bottom_screen"
        private const val PREF_BATTERY_OVERRIDE_WHEN_PLUGGED = "battery_override_when_plugged"
        private const val PREF_PERSISTENT_NOTIFICATION = "persistent_notification_enabled"
    }

    private var selectedAnimationType: LedAnimationType = LedAnimationType.AMBILIGHT
    private var selectedProfile: PerformanceProfile = PerformanceProfile.HIGH
    private var selectedColor: Int = Color.WHITE
    private var selectedRightColor: Int = Color.WHITE
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
    private var isAwaitingPermissionResult = false
    private var isUpdatingFromPreset = false
    private var rainbowDrawable: AnimatedRainbowDrawable? = null
    private var titleIntroAnimator: ValueAnimator? = null
    private var headerSettleAnimator: ValueAnimator? = null
    private var isAppInitialized = false
    private var bifrostTitleLabel: String = ""
    private var selectedCoverFlowIndex: Int = 0
    private var coverFlowSnapRunnable: Runnable? = null
    private var suppressNextCoverFlowSnap: Boolean = false
    private var isCoverFlowDragging: Boolean = false
    private var isSettingsOverlayAnimating: Boolean = false
    private var isSyncingAppProfileSwitches: Boolean = false
    private var pendingPresetArtworkIndex: Int? = null
    private var presetArtworkSheetDialog: BottomSheetDialog? = null
    private var appProfileSyncRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var presetController: PresetController
    private lateinit var serviceController: ServiceController
    private val colorPickerDialog = ColorPickerDialog()
    private val ragnarokWarningDialog = RagnarokWarningDialog()
    private lateinit var appProfileManager: AppProfileManager

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
                serviceController.startDebounced { createLedServiceIntent() }
            } else {
                isAwaitingPermissionResult = false
                serviceToggle.isChecked = false
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
                    customImageFileName = storedFileName
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupStatusBar()

        if (intent.getBooleanExtra("finish", false)) {
            finishAffinity()
            return
        }

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

    private fun initializeApp() {
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        settingsOverlay = findViewById(R.id.settingsOverlay)
        homeContainer = findViewById(R.id.homeContainer)
        homeSettingsButton = findViewById(R.id.homeSettingsButton)
        closeSettingsButton = findViewById(R.id.closeSettingsButton)
        presetCoverFlowScroll = findViewById(R.id.presetCoverFlowScroll)
        presetCoverFlowContainer = findViewById(R.id.presetCoverFlowContainer)
        activePresetInfoCard = findViewById(R.id.activePresetInfoCard)
        activePresetNameText = findViewById(R.id.activePresetNameText)
        activePresetStatusBadge = findViewById(R.id.activePresetStatusBadge)
        activePresetAnimationText = findViewById(R.id.activePresetAnimationText)
        activePresetProfileText = findViewById(R.id.activePresetProfileText)
        homePresetHintText = findViewById(R.id.homePresetHintText)

        serviceToggle = findViewById(R.id.serviceToggle)
        autoStartupSwitch = findViewById(R.id.autoStartupSwitch)
        pluggedBatteryOverrideSwitch = findViewById(R.id.pluggedBatteryOverrideSwitch)
        persistentNotificationSwitch = findViewById(R.id.persistentNotificationSwitch)
        animationSpinner = findViewById(R.id.animationSpinner)
        profileSpinner = findViewById(R.id.profileSpinner)
        presetSpinner = findViewById(R.id.presetSpinner)
        savePresetButton = findViewById(R.id.savePresetButton)
        modifyPresetButton = findViewById(R.id.modifyPresetButton)
        deletePresetButton = findViewById(R.id.deletePresetButton)
        colorButton = findViewById(R.id.colorButton)
        rightColorButton = findViewById(R.id.rightColorButton)
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
        homeAppProfileSwitch = findViewById(R.id.homeAppProfileSwitch)
        assignAppButton = findViewById(R.id.assignAppButton)
        manageAppsButton = findViewById(R.id.manageAppsButton)
        modeCard = findViewById(R.id.modeCard)
        colorCard = findViewById(R.id.colorCard)
        animationCard = findViewById(R.id.animationCard)
        performanceCard = findViewById(R.id.performanceCard)
        systemStatusContainer = findViewById(R.id.systemStatusContainer)
        bifrostLogoView = findViewById(R.id.homeBifrostLogoView)
        bifrostTitleText = findViewById(R.id.homeBifrostTitleText)

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
        setupRainbowTitleText()
        appProfileManager = AppProfileManager(prefs)
        setupAppProfileFeature()
        setupAutoStartupSwitch()
        setupPluggedBatteryOverrideSwitch()
        setupPersistentNotificationSwitch()
        setupThorScreenPreference()
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
                handleStartWithCurrentSelection()
            } else {
                serviceController.stopDebounced()
            }
        }

        maybeAutoStartHeimdallOnLaunch()

        isAppInitialized = true
    }

    private fun setupHomeSurface() {
        homeSettingsButton.setOnClickListener { openSettingsOverlay() }
        closeSettingsButton.setOnClickListener { closeSettingsOverlay() }

        presetCoverFlowScroll.setOnTouchListener { _, event ->
            if (::appProfileManager.isInitialized && appProfileManager.isEnabled) {
                return@setOnTouchListener true
            }

            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (suppressNextCoverFlowSnap) {
                    suppressNextCoverFlowSnap = false
                    return@setOnTouchListener false
                }
                snapCoverFlowToNearestPreset()
            }
            false
        }

        presetCoverFlowScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            updateCoverFlowCardTransforms()
        }
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
                    if (index in presets.indices && index != selectedCoverFlowIndex) {
                        selectPresetFromCoverFlow(index, animate = true, applyPreset = false)
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
            activePresetNameText.text = "No presets"
            activePresetAnimationText.text = "Animation: -"
            activePresetProfileText.text = "Profile: -"
            selectedCoverFlowIndex = 0
            return
        }

        presetCoverFlowContainer.removeAllViews()
        presets.forEachIndexed { index, preset ->
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
                    if (autoSwitchEnabled) return@setOnLongClickListener true
                    if (isCoverFlowDragging) return@setOnLongClickListener true
                    suppressNextCoverFlowSnap = true
                    showPresetArtworkMenu(it, index)
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

            val dragHandle = TextView(this).apply {
                text = "\u2261"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.END
                }
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bifrost_text_secondary))
                textSize = 18f
                contentDescription = "Drag to reorder"
                setOnLongClickListener {
                    if (autoSwitchEnabled) return@setOnLongClickListener true
                    suppressNextCoverFlowSnap = true
                    val dragData = ClipData.newPlainText("preset-index", index.toString())
                    it.startDragAndDrop(dragData, View.DragShadowBuilder(card), index, 0)
                    true
                }
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
            content.addView(dragHandle)
            content.addView(centerIconContainer)
            card.addView(content)
            presetCoverFlowContainer.addView(card)
        }

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

        updateManualPresetSwitchingUi(appProfileManager.isEnabled)
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

        val centerX = presetCoverFlowScroll.scrollX + (presetCoverFlowScroll.width / 2f)
        var nearestIndex = 0
        var nearestDistance = Float.MAX_VALUE

        for (index in 0 until presetCoverFlowContainer.childCount) {
            val card = presetCoverFlowContainer.getChildAt(index) ?: continue
            val cardCenterX = card.left + (card.width / 2f)
            val distance = abs(centerX - cardCenterX)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }

        selectPresetFromCoverFlow(nearestIndex, animate = true)
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
                val isSelected = index == selectedCoverFlowIndex
                card.scaleX = 1f
                card.scaleY = 1f
                card.alpha = if (autoSwitchEnabled) {
                    if (isSelected) 0.72f else 0.26f
                } else {
                    if (isSelected) 1f else 0.5f
                }

                card.strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
                card.setStrokeColor(if (isSelected) accentColor else secondaryColor)
            }
            return
        }

        for (index in 0 until presetCoverFlowContainer.childCount) {
            val card = presetCoverFlowContainer.getChildAt(index) as? MaterialCardView ?: continue
            val cardCenterX = card.left + (card.width / 2f)
            val distance = abs(centerX - cardCenterX)
            val normalizedDistance = (distance / (scrollWidth * 0.9f)).coerceIn(0f, 1f)
            val scale = 1f - (0.2f * normalizedDistance)
            val isSelected = index == selectedCoverFlowIndex

            card.scaleX = scale
            card.scaleY = scale
            card.alpha = if (autoSwitchEnabled) {
                if (isSelected) {
                    0.76f
                } else {
                    0.22f + (0.12f * (1f - normalizedDistance))
                }
            } else {
                0.5f + (0.5f * (1f - normalizedDistance))
            }

            card.strokeWidth = if (isSelected) dpToPx(2) else dpToPx(1)
            card.setStrokeColor(if (isSelected) accentColor else secondaryColor)
        }
    }

    private fun syncAppProfileSwitches(isChecked: Boolean) {
        isSyncingAppProfileSwitches = true
        appProfileSwitch.isChecked = isChecked
        homeAppProfileSwitch.isChecked = isChecked
        isSyncingAppProfileSwitches = false
    }

    private fun updateManualPresetSwitchingUi(autoSwitchEnabled: Boolean) {
        presetCoverFlowScroll.isEnabled = !autoSwitchEnabled
        presetCoverFlowScroll.scrollLocked = autoSwitchEnabled
        activePresetInfoCard.alpha = 1f
        activePresetStatusBadge.visibility = if (autoSwitchEnabled) View.VISIBLE else View.GONE
        presetCoverFlowScroll.alpha = if (autoSwitchEnabled) 0.95f else 1f
        presetSpinner.isEnabled = true
        presetSpinner.alpha = 1f
        homePresetHintText.text = if (!autoSwitchEnabled) {
            "Swipe or tap a card to load a preset instantly. Long-press a card to change its art."
        } else {
            "App-based switching is on. Preset tiles are locked until you turn it off."
        }

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
            resetButton.visibility = if (!preset.customEmoji.isNullOrBlank() || !preset.customImageFileName.isNullOrBlank()) {
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
                                    customImageFileName = null
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
                    customImageFileName = null
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
                    customImageFileName = null
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
        window.statusBarColor = getColor(R.color.bifrost_bg)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isAppInitialized) return

        mainHandler.postDelayed({
            if (isAwaitingPermissionResult) {
                if (LEDService.isRunning) serviceToggle.isChecked = true
                isAwaitingPermissionResult = false
            } else {
                serviceToggle.isChecked = LEDService.isRunning
                enableRainbowBackground(LEDService.isRunning)
            }

            refreshCoverFlowFromPresets()
        }, 100)
    }

    override fun onBackPressed() {
        if (::settingsOverlay.isInitialized && settingsOverlay.visibility == View.VISIBLE) {
            closeSettingsOverlay()
            return
        }
        super.onBackPressed()
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
        serviceController.cancelPendingOperations()
        coverFlowSnapRunnable?.let(mainHandler::removeCallbacks)
        coverFlowSnapRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isAppInitialized) return
        titleIntroAnimator?.cancel()
        headerSettleAnimator?.cancel()
        titleIntroAnimator = null
        headerSettleAnimator = null
        homeContainer.animate().cancel()
        homeContainer.alpha = 1f
        settingsOverlay.animate().cancel()
        isSettingsOverlayAnimating = false
        presetArtworkSheetDialog?.dismiss()
        serviceController.cancelPendingOperations()
        coverFlowSnapRunnable?.let(mainHandler::removeCallbacks)
        coverFlowSnapRunnable = null
        rainbowDrawable?.stop()
        rainbowDrawable = null
    }

    private fun setupRainbowTitleText() {
        bifrostTitleLabel = bifrostTitleText.text.toString()
        if (bifrostTitleLabel.isBlank()) return

        bifrostLogoView.setOnClickListener { playBifrostHeaderAnimation() }
        bifrostTitleText.setOnClickListener { playBifrostHeaderAnimation() }
        resetBifrostHeaderAnimationState()
        playBifrostHeaderAnimation()
    }

    private fun applyRainbowTitlePhase(text: String, phaseDegrees: Float) {
        val rainbowText = SpannableString(text)
        val maxIndex = (text.length - 1).coerceAtLeast(1)

        text.indices.forEach { index ->
            if (text[index].isWhitespace()) return@forEach

            val hue = (phaseDegrees + (360f * index / maxIndex)) % 360f
            val color = Color.HSVToColor(floatArrayOf(hue, 0.82f, 1f))
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

            val letterProgress = index / maxIndex.toFloat()
            val hue = (phaseDegrees + 300f * letterProgress + 10f * sin(letterProgress * PI).toFloat()) % 360f
            val saturation = (0.28f + 0.14f * ((sin(letterProgress * PI * 3.0) + 1.0) / 2.0).toFloat())
                .coerceIn(0f, 1f)
            val value = (0.92f + 0.08f * ((cos(letterProgress * PI * 2.0) + 1.0) / 2.0).toFloat())
                .coerceIn(0f, 1f)
            val alpha = (224 + 31 * ((sin(letterProgress * PI * 2.5) + 1.0) / 2.0)).roundToInt()
                .coerceIn(0, 255)
            val color = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))

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

        applyWatercolorTitlePhase(bifrostTitleLabel, 18f)
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

        bifrostLogoView.animate()
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(400L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { bifrostLogoView.clearColorFilter() }
            .start()

        val rainbowColors = getRainbowColors(bifrostTitleLabel, 720f)
        val watercolorColors = getWatercolorColors(bifrostTitleLabel, 18f)

        headerSettleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                applyBlendedTitleColors(rainbowColors, watercolorColors, t)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyWatercolorTitlePhase(bifrostTitleLabel, 18f)
                }
            })
            start()
        }
    }

    private fun getRainbowColors(text: String, phaseDegrees: Float): IntArray {
        val colors = IntArray(text.length)
        val maxIndex = (text.length - 1).coerceAtLeast(1)
        text.indices.forEach { index ->
            if (text[index].isWhitespace()) {
                colors[index] = Color.TRANSPARENT
            } else {
                val hue = (phaseDegrees + (360f * index / maxIndex)) % 360f
                colors[index] = Color.HSVToColor(floatArrayOf(hue, 0.82f, 1f))
            }
        }
        return colors
    }

    private fun getWatercolorColors(text: String, phaseDegrees: Float): IntArray {
        val colors = IntArray(text.length)
        val maxIndex = (text.length - 1).coerceAtLeast(1)
        text.indices.forEach { index ->
            if (text[index].isWhitespace()) {
                colors[index] = Color.TRANSPARENT
            } else {
                val letterProgress = index / maxIndex.toFloat()
                val hue = (phaseDegrees + 300f * letterProgress + 10f * sin(letterProgress * PI).toFloat()) % 360f
                val saturation = (0.28f + 0.14f * ((sin(letterProgress * PI * 3.0) + 1.0) / 2.0).toFloat())
                    .coerceIn(0f, 1f)
                val value = (0.92f + 0.08f * ((cos(letterProgress * PI * 2.0) + 1.0) / 2.0).toFloat())
                    .coerceIn(0f, 1f)
                val alpha = (224 + 31 * ((sin(letterProgress * PI * 2.5) + 1.0) / 2.0)).roundToInt()
                    .coerceIn(0, 255)
                colors[index] = Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
            }
        }
        return colors
    }

    private fun applyBlendedTitleColors(startColors: IntArray, endColors: IntArray, t: Float) {
        val rainbowText = SpannableString(bifrostTitleLabel)
        val blend = t.coerceIn(0f, 1f)
        val maxIndex = (bifrostTitleLabel.length - 1).coerceAtLeast(1)

        bifrostTitleLabel.indices.forEach { index ->
            if (bifrostTitleLabel[index].isWhitespace()) return@forEach
            val blended = androidx.core.graphics.ColorUtils.blendARGB(
                startColors.getOrNull(index) ?: Color.WHITE,
                endColors.getOrNull(index) ?: Color.WHITE,
                blend
            )
            rainbowText.setSpan(
                ForegroundColorSpan(blended),
                index,
                index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        bifrostTitleText.text = rainbowText
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
        }

        thorAmbilightBottomSwitch = findViewById(R.id.thorAmbilightBottomSwitch)
        thorAmbilightBottomSwitch?.isChecked = prefs.getBoolean(PREF_THOR_AMBILIGHT_BOTTOM_SCREEN, false)
        thorAmbilightBottomSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_THOR_AMBILIGHT_BOTTOM_SCREEN, isChecked).apply()
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
    private fun maybeRelaunchOnCorrectDisplay(): Boolean {
        if (!DeviceInfo.isAynThor) return false
        if (intent.getBooleanExtra("display_relaunched", false)) return false

        val useBottomScreen = prefs.getBoolean(PREF_THOR_BOTTOM_SCREEN, false)
        val displayManager = getSystemService(DisplayManager::class.java)
        val currentDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY

        val targetDisplayId = if (useBottomScreen) {
            displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.displayId
                ?: Display.DEFAULT_DISPLAY
        } else {
            Display.DEFAULT_DISPLAY
        }

        if (currentDisplayId == targetDisplayId) return false

        val newIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("display_relaunched", true)
        }
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = targetDisplayId
        startActivity(newIntent, options.toBundle())
        finish()
        return true
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
            flashWhenReady = selectedFlashWhenReady
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
                    flashWhenReady = selectedFlashWhenReady
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
                    if (selectedAnimationType.needsMediaProjection) {
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
            }
        )

        presetController.init(initialConfigPreset)
        refreshCoverFlowFromPresets()

        // If the app switching feature is already enabled, start syncing the UI after presets load.
        if (::appProfileManager.isInitialized && appProfileManager.isEnabled) {
            startAppProfileSync()
        }
    }

    private fun updateParameterVisibility() {
        val needsColor = selectedAnimationType.needsColorSelection
        val needsProfile = selectedAnimationType.needsMediaProjection
        val needsSpeed = selectedAnimationType.supportsSpeed
        val needsSmoothness = selectedAnimationType.supportsSmoothness
        val needsSensitivity = selectedAnimationType.supportsAudioSensitivity
        val needsSaturationBoost = selectedAnimationType == LedAnimationType.AMBILIGHT ||
                selectedAnimationType == LedAnimationType.AMBIAURORA
        val needsCustomSampling = selectedAnimationType == LedAnimationType.AMBILIGHT ||
                selectedAnimationType == LedAnimationType.AMBIAURORA
        val needsSingleColor = selectedAnimationType == LedAnimationType.AMBILIGHT ||
                selectedAnimationType == LedAnimationType.AMBIAURORA
        val needsBreatheWhenCharging = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR
        val needsChargingSpeedIndicator = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR &&
            selectedBreatheWhenCharging
        val needsFlashWhenReady = selectedAnimationType == LedAnimationType.BATTERY_INDICATOR

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
        animationCard.visibility = if (needsSpeed || needsSmoothness || needsSensitivity || needsSaturationBoost || needsCustomSampling || needsSingleColor || needsBreatheWhenCharging || needsChargingSpeedIndicator || needsFlashWhenReady) View.VISIBLE else View.GONE

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

    private fun getAmbilightTargetDisplayId(): Int {
        if (!DeviceInfo.isAynThor) return Display.DEFAULT_DISPLAY
        if (!prefs.getBoolean(PREF_THOR_AMBILIGHT_BOTTOM_SCREEN, false)) return Display.DEFAULT_DISPLAY
        val dm = getSystemService(DisplayManager::class.java)
        return dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.displayId
            ?: Display.DEFAULT_DISPLAY
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
            putExtra(
                LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                selectedBatteryOverrideWhenPlugged
            )
            putExtra(
                LEDService.EXTRA_PERSISTENT_NOTIFICATION,
                selectedPersistentNotification
            )
            putExtra("ambilightDisplayId", getAmbilightTargetDisplayId())
            putExtra(
                LEDService.EXTRA_ALLOW_BACKGROUND_RUN,
                HeimdallStartupManager.isAutoStartEnabled(prefs)
            )
            if (selectedAnimationType.needsMediaProjection) {
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
            putExtra(
                LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                selectedBatteryOverrideWhenPlugged
            )
            putExtra(
                LEDService.EXTRA_PERSISTENT_NOTIFICATION,
                selectedPersistentNotification
            )
        }
        startService(intent)
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
}