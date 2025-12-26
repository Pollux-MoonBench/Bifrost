package com.moonbench.bifrost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.ServiceController
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.ui.AnimatedRainbowDrawable
import com.moonbench.bifrost.ui.BifrostAlertDialog
import com.moonbench.bifrost.ui.ColorPickerDialog
import com.moonbench.bifrost.ui.RagnarokWarningDialog

class MainActivity : AppCompatActivity() {

    private lateinit var serviceToggle: SwitchMaterial
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var animationSpinner: Spinner
    private lateinit var profileSpinner: Spinner
    private lateinit var presetSpinner: Spinner
    private lateinit var savePresetButton: MaterialButton
    private lateinit var modifyPresetButton: MaterialButton
    private lateinit var deletePresetButton: MaterialButton
    private lateinit var colorButton: MaterialButton
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var speedSeekBar: SeekBar
    private lateinit var smoothnessSeekBar: SeekBar
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var saturationBoostSeekBar: SeekBar
    private lateinit var customSamplingSwitch: SwitchMaterial
    private lateinit var singleColorSwitch: SwitchMaterial
    private lateinit var modeCard: MaterialCardView
    private lateinit var colorCard: MaterialCardView
    private lateinit var animationCard: MaterialCardView
    private lateinit var performanceCard: MaterialCardView
    private lateinit var systemStatusContainer: View

    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }

    companion object {
        var mediaProjectionResultCode: Int? = null
        var mediaProjectionData: Intent? = null
        private const val DEBOUNCE_DELAY = 500L
        private const val SERVICE_RESTART_DELAY = 400L
        private const val PREF_FIRST_LAUNCH_ALERT_SHOWN = "first_launch_alert_shown"
    }

    private var selectedAnimationType: LedAnimationType = LedAnimationType.AMBILIGHT
    private var selectedProfile: PerformanceProfile = PerformanceProfile.HIGH
    private var selectedColor: Int = Color.WHITE
    private var selectedBrightness: Int = 255
    private var selectedSpeed: Float = 0.5f
    private var selectedSmoothness: Float = 0.5f
    private var selectedSensitivity: Float = 0.5f
    private var selectedSaturationBoost: Float = 0.0f
    private var selectedUseCustomSampling: Boolean = false
    private var selectedUseSingleColor: Boolean = false
    private var isAwaitingPermissionResult = false
    private var isUpdatingFromPreset = false
    private var rainbowDrawable: AnimatedRainbowDrawable? = null
    private var isAppInitialized = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var presetController: PresetController
    private lateinit var serviceController: ServiceController
    private val colorPickerDialog = ColorPickerDialog()
    private val ragnarokWarningDialog = RagnarokWarningDialog()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupStatusBar()

        if (intent.getBooleanExtra("finish", false)) {
            finishAffinity()
            return
        }

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

        serviceToggle = findViewById(R.id.serviceToggle)
        animationSpinner = findViewById(R.id.animationSpinner)
        profileSpinner = findViewById(R.id.profileSpinner)
        presetSpinner = findViewById(R.id.presetSpinner)
        savePresetButton = findViewById(R.id.savePresetButton)
        modifyPresetButton = findViewById(R.id.modifyPresetButton)
        deletePresetButton = findViewById(R.id.deletePresetButton)
        colorButton = findViewById(R.id.colorButton)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        smoothnessSeekBar = findViewById(R.id.smoothnessSeekBar)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        saturationBoostSeekBar = findViewById(R.id.saturationBoostSeekBar)
        customSamplingSwitch = findViewById(R.id.customSamplingSwitch)
        singleColorSwitch = findViewById(R.id.singleColorSwitch)
        modeCard = findViewById(R.id.modeCard)
        colorCard = findViewById(R.id.colorCard)
        animationCard = findViewById(R.id.animationCard)
        performanceCard = findViewById(R.id.performanceCard)
        systemStatusContainer = findViewById(R.id.systemStatusContainer)

        serviceController = ServiceController(
            activity = this,
            handler = mainHandler,
            debounceDelay = DEBOUNCE_DELAY,
            restartDelay = SERVICE_RESTART_DELAY
        )

        serviceController.onNeedsMediaProjectionCheck = {
            handleMediaProjectionRequirement()
        }

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

        isAppInitialized = true
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
        }, 100)
    }

    override fun onPause() {
        super.onPause()
        if (!isAppInitialized) return
        serviceController.cancelPendingOperations()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isAppInitialized) return
        serviceController.cancelPendingOperations()
        rainbowDrawable?.stop()
        rainbowDrawable = null
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
                        val presetName = presetSpinner.selectedItem?.toString()
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
        colorButton.setOnClickListener { showColorPicker() }
        colorButton.setBackgroundColor(selectedColor)
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

    private fun setupPresetFeature() {
        val initialConfigPreset = LedPreset(
            name = "Initial",
            animationType = selectedAnimationType,
            performanceProfile = selectedProfile,
            color = selectedColor,
            brightness = selectedBrightness,
            speed = selectedSpeed,
            smoothness = selectedSmoothness,
            sensitivity = selectedSensitivity,
            saturationBoost = selectedSaturationBoost,
            useCustomSampling = selectedUseCustomSampling,
            useSingleColor = selectedUseSingleColor
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
                    brightness = selectedBrightness,
                    speed = selectedSpeed,
                    smoothness = selectedSmoothness,
                    sensitivity = selectedSensitivity,
                    saturationBoost = selectedSaturationBoost,
                    useCustomSampling = selectedUseCustomSampling,
                    useSingleColor = selectedUseSingleColor
                )
            },
            applyPresetToUi = { preset ->
                selectedAnimationType = preset.animationType
                selectedProfile = preset.performanceProfile
                selectedColor = preset.color
                selectedBrightness = preset.brightness
                selectedSpeed = preset.speed
                selectedSmoothness = preset.speed
                selectedSensitivity = preset.sensitivity
                selectedSaturationBoost = preset.saturationBoost
                selectedUseCustomSampling = preset.useCustomSampling
                selectedUseSingleColor = preset.useSingleColor

                val types = LedAnimationType.values().toList()
                animationSpinner.setSelection(types.indexOf(selectedAnimationType).coerceAtLeast(0))

                val profiles = PerformanceProfile.values().toList()
                profileSpinner.setSelection(profiles.indexOf(selectedProfile).coerceAtLeast(0))

                colorButton.setBackgroundColor(selectedColor)
                brightnessSeekBar.progress = selectedBrightness
                val progress = (selectedSpeed * 100).toInt()
                speedSeekBar.progress = progress
                smoothnessSeekBar.progress = progress
                sensitivitySeekBar.progress = (selectedSensitivity * 100).toInt()
                saturationBoostSeekBar.progress = (selectedSaturationBoost * 100).toInt()
                customSamplingSwitch.isChecked = selectedUseCustomSampling
                singleColorSwitch.isChecked = selectedUseSingleColor

                updateParameterVisibility()
            },
            markIsUpdatingFromPreset = { value ->
                isUpdatingFromPreset = value
            },
            isUpdatingFromPreset = {
                isUpdatingFromPreset
            },
            onPresetApplied = {
                if (LEDService.isRunning && !serviceController.isServiceTransitioning) {
                    if (selectedAnimationType.needsMediaProjection) {
                        if (mediaProjectionResultCode == null || mediaProjectionData == null) {
                            handleMediaProjectionRequirement()
                        } else {
                            serviceController.restartDebounced { createLedServiceIntent() }
                        }
                    } else {
                        serviceController.restartDebounced { createLedServiceIntent() }
                    }
                }
            }
        )

        presetController.init(initialConfigPreset)
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

        val supportsBrightness = selectedAnimationType != LedAnimationType.AMBILIGHT &&
                selectedAnimationType != LedAnimationType.AMBIAURORA &&
                selectedAnimationType != LedAnimationType.AUDIO_REACTIVE

        colorCard.visibility = if (needsColor || supportsBrightness) View.VISIBLE else View.GONE

        if (colorCard.visibility == View.VISIBLE) {
            colorButton.visibility = if (needsColor) View.VISIBLE else View.GONE

            val colorCardTitle = findViewById<TextView>(R.id.colorCardTitle)
            if (needsColor) {
                colorCardTitle?.text = "COLOR & INTENSITY"
            } else {
                colorCardTitle?.text = "INTENSITY"
            }
        }

        performanceCard.visibility = if (needsProfile) View.VISIBLE else View.GONE
        animationCard.visibility = if (needsSpeed || needsSmoothness || needsSensitivity || needsSaturationBoost || needsCustomSampling || needsSingleColor) View.VISIBLE else View.GONE

        if (animationCard.visibility == View.VISIBLE) {
            val speedLabel = findViewById<View>(R.id.speedLabel)
            val smoothnessLabel = findViewById<View>(R.id.smoothnessLabel)
            val sensitivityLabel = findViewById<View>(R.id.sensitivityLabel)
            val saturationBoostLabel = findViewById<View>(R.id.saturationBoostLabel)
            val customSamplingLabel = findViewById<View>(R.id.customSamplingLabel)
            val singleColorLabel = findViewById<View>(R.id.singleColorLabel)
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
        }
    }

    private fun showColorPicker() {
        colorPickerDialog.show(
            activity = this,
            initialColor = selectedColor
        ) { color ->
            selectedColor = color
            colorButton.setBackgroundColor(selectedColor)
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
        val presetName = presetSpinner.selectedItem?.toString()
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

    private fun createLedServiceIntent(): Intent {
        return Intent(this, LEDService::class.java).apply {
            putExtra("animationType", selectedAnimationType.name)
            putExtra("performanceProfile", selectedProfile.name)
            putExtra("animationColor", selectedColor)
            putExtra("brightness", selectedBrightness)
            putExtra("speed", selectedSpeed)
            putExtra("smoothness", selectedSmoothness)
            putExtra("sensitivity", selectedSensitivity)
            putExtra("saturationBoost", selectedSaturationBoost)
            putExtra("useCustomSampling", selectedUseCustomSampling)
            putExtra("useSingleColor", selectedUseSingleColor)
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
            putExtra("brightness", selectedBrightness)
            putExtra("speed", selectedSpeed)
            putExtra("smoothness", selectedSmoothness)
            putExtra("sensitivity", selectedSensitivity)
            putExtra("saturationBoost", selectedSaturationBoost)
            putExtra("useCustomSampling", selectedUseCustomSampling)
            putExtra("useSingleColor", selectedUseSingleColor)
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