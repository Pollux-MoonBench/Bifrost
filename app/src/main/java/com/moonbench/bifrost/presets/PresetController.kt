package com.moonbench.bifrost

import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.PerformanceProfile
import com.moonbench.bifrost.ui.DeletePresetDialog
import org.json.JSONArray
import org.json.JSONObject

class PresetController(
    private val activity: AppCompatActivity,
    private val prefs: SharedPreferences,
    private val presetSpinner: Spinner,
    private val saveAsNewButton: MaterialButton,
    private val modifyButton: MaterialButton,
    private val deleteButton: MaterialButton,
    private val getCurrentConfig: () -> LedPreset,
    private val applyPresetToUi: (LedPreset) -> Unit,
    private val markIsUpdatingFromPreset: (Boolean) -> Unit,
    private val isUpdatingFromPreset: () -> Boolean,
    private val onPresetApplied: () -> Unit
) {

    companion object {
        private const val PREF_KEY_PRESETS = "presets_json"
        private const val PREF_KEY_LAST_PRESET = "last_preset_name"
    }

    private val presets: MutableList<LedPreset> = mutableListOf()
    private var selectedIndex: Int = 0
    private val deleteDialog = DeletePresetDialog()

    fun init(initialConfig: LedPreset): LedPreset {
        presets.clear()
        presets.addAll(loadPresetsFromPrefs())
        val initialPreset = resolveInitialPreset(initialConfig)
        markIsUpdatingFromPreset(true)
        applyPresetToUi(initialPreset)
        markIsUpdatingFromPreset(false)
        setupPresetControls(initialPreset)
        return initialPreset
    }

    fun getPresets(): List<LedPreset> = presets

    fun applyPresetAt(index: Int, syncSpinner: Boolean = true) {
        if (index !in presets.indices) return

        selectedIndex = index
        val preset = presets[index]
        saveLastPresetName(preset.name)

        markIsUpdatingFromPreset(true)
        if (syncSpinner && presetSpinner.selectedItemPosition != index) {
            presetSpinner.setSelection(index)
        }
        applyPresetToUi(preset)
        markIsUpdatingFromPreset(false)

        onPresetApplied()
    }

    fun movePreset(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in presets.indices || toIndex !in presets.indices) return false
        if (fromIndex == toIndex) return false

        val selectedPresetName = presets.getOrNull(selectedIndex)?.name
        val movedPreset = presets.removeAt(fromIndex)
        presets.add(toIndex, movedPreset)

        savePresetsToPrefs()
        saveLastPresetName(selectedPresetName ?: movedPreset.name)
        refreshPresetSpinner(selectedPresetName ?: movedPreset.name)
        return true
    }

    fun updatePresetVisual(index: Int, transform: (LedPreset) -> LedPreset): LedPreset? {
        if (index !in presets.indices) return null

        val selectedPresetName = presets.getOrNull(selectedIndex)?.name
        val updatedPreset = transform(presets[index])
        replacePreset(
            index = index,
            updatedPreset = updatedPreset,
            applyToUi = false,
            notifyPresetApplied = false,
            selectedNameAfterSave = selectedPresetName ?: updatedPreset.name
        )
        return updatedPreset
    }

    fun markRagnarokAccepted(name: String?) {
        if (name == null) return
        val index = presets.indexOfFirst { it.name == name }
        if (index < 0) return
        val p = presets[index]
        presets[index] = p.copy(ragnarokAccepted = true)
        savePresetsToPrefs()
    }

    private fun setupPresetControls(initialPreset: LedPreset) {
        refreshPresetSpinner(initialPreset.name)

        saveAsNewButton.setOnClickListener { showSaveAsNewPresetDialog() }
        modifyButton.setOnClickListener { modifyCurrentPreset() }
        deleteButton.setOnClickListener { showDeleteDialog() }

        presetSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (isUpdatingFromPreset()) return
                    applyPresetAt(position, syncSpinner = false)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun refreshPresetSpinner(selectedName: String?) {
        val adapter = IconLabelSpinnerAdapter(
            activity = activity,
            items = presets.toList(),
            itemLayoutRes = R.layout.item_spinner_preset,
            dropdownLayoutRes = R.layout.item_spinner_preset_dropdown,
            labelProvider = { it.name },
            visualProvider = { PresetVisuals.fromPreset(it) }
        )
        presetSpinner.adapter = adapter

        val index = selectedName?.let { name ->
            presets.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: 0
        } ?: 0

        selectedIndex = index.coerceIn(0, (presets.size - 1).coerceAtLeast(0))

        markIsUpdatingFromPreset(true)
        if (presets.isNotEmpty()) presetSpinner.setSelection(selectedIndex)
        markIsUpdatingFromPreset(false)
    }

    private fun loadPresetsFromPrefs(): MutableList<LedPreset> {
        val json = prefs.getString(PREF_KEY_PRESETS, null) ?: return mutableListOf()
        val array = JSONArray(json)
        val list = mutableListOf<LedPreset>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue

            val name = obj.optString("name", "Preset ${i + 1}")
            val type = runCatching {
                LedAnimationType.valueOf(obj.optString("animationType", LedAnimationType.STATIC.name))
            }.getOrDefault(LedAnimationType.STATIC)

            val profile = runCatching {
                PerformanceProfile.valueOf(obj.optString("performanceProfile", PerformanceProfile.HIGH.name))
            }.getOrDefault(PerformanceProfile.HIGH)
            val icon = PresetIcon.fromStoredName(
                obj.optString("icon", PresetIcon.defaultFor(type).name)
            )
            val customEmoji = obj.optString("customEmoji")
                .takeIf { it.isNotBlank() }
            val customImageFileName = obj.optString("customImageFileName")
                .takeIf { it.isNotBlank() }

            val accepted = obj.optBoolean("ragnarokAccepted", false)
            val useCustomSampling = obj.optBoolean("useCustomSampling", false)
            val useSingleColor = obj.optBoolean("useSingleColor", false)
            val breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false)
            val indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false)
            val flashWhenReady = obj.optBoolean("flashWhenReady", false)

            val color = obj.optInt("color", Color.WHITE)
            list.add(
                LedPreset(
                    name = name,
                    animationType = type,
                    performanceProfile = profile,
                    color = color,
                    rightColor = obj.optInt("rightColor", color),
                    brightness = obj.optInt("brightness", 255),
                    speed = obj.optDouble("speed", 0.5).toFloat(),
                    smoothness = obj.optDouble("smoothness", 0.5).toFloat(),
                    sensitivity = obj.optDouble("sensitivity", 0.5).toFloat(),
                    saturationBoost = obj.optDouble("saturationBoost", 0.0).toFloat(),
                    useCustomSampling = useCustomSampling,
                    useSingleColor = useSingleColor,
                    breatheWhenCharging = breatheWhenCharging,
                    indicateChargingSpeed = indicateChargingSpeed,
                    flashWhenReady = flashWhenReady,
                    ragnarokAccepted = accepted,
                    icon = icon,
                    customEmoji = customEmoji,
                    customImageFileName = customImageFileName
                )
            )
        }

        return list
    }

    private fun savePresetsToPrefs() {
        val array = JSONArray()

        presets.forEach { preset ->
            val obj = JSONObject()
            obj.put("name", preset.name)
            obj.put("animationType", preset.animationType.name)
            obj.put("performanceProfile", preset.performanceProfile.name)
            obj.put("color", preset.color)
            obj.put("rightColor", preset.rightColor)
            obj.put("brightness", preset.brightness)
            obj.put("speed", preset.speed.toDouble())
            obj.put("smoothness", preset.smoothness.toDouble())
            obj.put("sensitivity", preset.sensitivity.toDouble())
            obj.put("saturationBoost", preset.saturationBoost.toDouble())
            obj.put("useCustomSampling", preset.useCustomSampling)
            obj.put("useSingleColor", preset.useSingleColor)
            obj.put("breatheWhenCharging", preset.breatheWhenCharging)
            obj.put("indicateChargingSpeed", preset.indicateChargingSpeed)
            obj.put("flashWhenReady", preset.flashWhenReady)
            obj.put("ragnarokAccepted", preset.ragnarokAccepted)
            obj.put("icon", preset.icon.name)
            preset.customEmoji?.let { obj.put("customEmoji", it) }
            preset.customImageFileName?.let { obj.put("customImageFileName", it) }
            array.put(obj)
        }

        prefs.edit().putString(PREF_KEY_PRESETS, array.toString()).apply()
    }

    private fun saveLastPresetName(name: String) {
        prefs.edit().putString(PREF_KEY_LAST_PRESET, name).apply()
    }

    private fun resolveInitialPreset(initialConfig: LedPreset): LedPreset {
        if (presets.isEmpty()) {
            val defaultPreset = initialConfig.copy(name = "Default")
            presets.add(defaultPreset)
            savePresetsToPrefs()
            saveLastPresetName(defaultPreset.name)
            return defaultPreset
        }

        val last = prefs.getString(PREF_KEY_LAST_PRESET, null)
        if (last != null) {
            val found = presets.firstOrNull { it.name == last }
            if (found != null) return found
        }

        val first = presets.first()
        saveLastPresetName(first.name)
        return first
    }

    private fun showSaveAsNewPresetDialog() {
        val defaultName = "Preset ${presets.size + 1}"
        val initialIcon = presets.getOrNull(selectedIndex)?.icon
            ?: PresetIcon.defaultFor(getCurrentConfig().animationType)

        showPresetEditorDialog(
            title = "SAVE PRESET",
            subtitle = "Name this configuration for quick access",
            positiveButtonLabel = "Save",
            initialName = defaultName,
            initialIcon = initialIcon,
            showNameInput = true
        ) { rawName, icon ->
                val base = getCurrentConfig()
                val desired = if (rawName.isEmpty()) defaultName else rawName
                val unique = ensureUniqueName(desired)
                val newPreset = base.copy(
                    name = unique,
                    icon = icon,
                    customEmoji = null,
                    customImageFileName = null,
                    ragnarokAccepted = base.performanceProfile == PerformanceProfile.RAGNAROK
                )
                presets.add(newPreset)
                savePresetsToPrefs()
                saveLastPresetName(unique)
                refreshPresetSpinner(unique)
        }
    }

    private fun ensureUniqueName(base: String): String {
        if (presets.none { it.name == base }) return base
        var i = 2
        while (true) {
            val name = "$base ($i)"
            if (presets.none { it.name == name }) return name
            i++
        }
    }

    private fun modifyCurrentPreset() {
        if (selectedIndex !in presets.indices) return

        val current = presets[selectedIndex]
        showPresetEditorDialog(
            title = "UPDATE PRESET",
            subtitle = "Save the current configuration to ${current.name} and choose its icon",
            positiveButtonLabel = "Update",
            initialName = current.name,
            initialIcon = current.icon,
            showNameInput = false
        ) { _, icon ->
            val base = getCurrentConfig()
            val accepted = current.ragnarokAccepted || base.performanceProfile == PerformanceProfile.RAGNAROK

            val final = base.copy(
                name = current.name,
                ragnarokAccepted = accepted,
                icon = icon,
                customEmoji = null,
                customImageFileName = null
            )

            replacePreset(
                index = selectedIndex,
                updatedPreset = final,
                applyToUi = true,
                notifyPresetApplied = true
            )
        }
    }

    private fun replacePreset(
        index: Int,
        updatedPreset: LedPreset,
        applyToUi: Boolean,
        notifyPresetApplied: Boolean,
        selectedNameAfterSave: String = updatedPreset.name
    ) {
        if (index !in presets.indices) return

        val previousPreset = presets[index]
        cleanupReplacedImage(previousPreset, updatedPreset)

        presets[index] = updatedPreset
        savePresetsToPrefs()
        saveLastPresetName(selectedNameAfterSave)
        refreshPresetSpinner(selectedNameAfterSave)

        if (applyToUi) {
            markIsUpdatingFromPreset(true)
            applyPresetToUi(updatedPreset)
            markIsUpdatingFromPreset(false)
        }

        if (notifyPresetApplied) {
            onPresetApplied()
        }
    }

    private fun cleanupReplacedImage(previousPreset: LedPreset, updatedPreset: LedPreset) {
        val previousFileName = previousPreset.customImageFileName
        if (!previousFileName.isNullOrBlank() && previousFileName != updatedPreset.customImageFileName) {
            PresetImageStorage.deleteIfExists(activity, previousFileName)
        }
    }

    private fun showPresetEditorDialog(
        title: String,
        subtitle: String,
        positiveButtonLabel: String,
        initialName: String,
        initialIcon: PresetIcon,
        showNameInput: Boolean,
        onConfirm: (String, PresetIcon) -> Unit
    ) {
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_preset_name, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val subtitleView = view.findViewById<TextView>(R.id.dialogSubtitle)
        val nameInputLayout = view.findViewById<TextInputLayout>(R.id.presetNameInputLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.presetNameInput)
        val iconSpinner = view.findViewById<Spinner>(R.id.presetIconSpinner)
        val icons = PresetIcon.values().toList()

        titleView.text = title
        subtitleView.text = subtitle
        nameInput.setText(initialName)

        nameInputLayout.visibility = if (showNameInput) View.VISIBLE else View.GONE
        if (showNameInput) {
            nameInput.setSelection(initialName.length)
        }

        iconSpinner.adapter = IconLabelSpinnerAdapter(
            activity = activity,
            items = icons,
            itemLayoutRes = R.layout.item_spinner_preset,
            dropdownLayoutRes = R.layout.item_spinner_preset_dropdown,
            labelProvider = { it.label },
            visualProvider = { PresetVisuals.fromBuiltIn(it) }
        )
        iconSpinner.setSelection(icons.indexOf(initialIcon).coerceAtLeast(0), false)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(positiveButtonLabel) { _, _ ->
                val resolvedName = if (showNameInput) {
                    nameInput.text?.toString()?.trim().orEmpty()
                } else {
                    initialName
                }
                val resolvedIcon = icons.getOrElse(iconSpinner.selectedItemPosition) { initialIcon }
                onConfirm(resolvedName, resolvedIcon)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showDeleteDialog() {
        if (selectedIndex !in presets.indices) return
        val preset = presets[selectedIndex]

        deleteDialog.show(
            activity = activity,
            presetName = preset.name,
            onConfirm = {
                PresetImageStorage.deleteIfExists(activity, preset.customImageFileName)
                presets.removeAt(selectedIndex)
                savePresetsToPrefs()

                if (presets.isEmpty()) {
                    saveLastPresetName("")
                    refreshPresetSpinner(null)
                } else {
                    val newIndex = selectedIndex.coerceAtMost(presets.size - 1)
                    val newPreset = presets[newIndex]
                    saveLastPresetName(newPreset.name)
                    refreshPresetSpinner(newPreset.name)
                    markIsUpdatingFromPreset(true)
                    applyPresetToUi(newPreset)
                    markIsUpdatingFromPreset(false)
                    onPresetApplied()
                }
            }
        )
    }
}

class IconLabelSpinnerAdapter<T>(
    activity: AppCompatActivity,
    private val items: List<T>,
    @LayoutRes private val itemLayoutRes: Int,
    @LayoutRes private val dropdownLayoutRes: Int,
    private val labelProvider: (T) -> CharSequence,
    private val visualProvider: (T) -> PresetVisualSpec
) : ArrayAdapter<T>(activity, itemLayoutRes, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): T = items[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return bindView(position, convertView, parent, itemLayoutRes)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return bindView(position, convertView, parent, dropdownLayoutRes)
    }

    private fun bindView(position: Int, convertView: View?, parent: ViewGroup, layoutRes: Int): View {
        val row = convertView ?: inflater.inflate(layoutRes, parent, false)
        val item = items[position]
        val text = row.findViewById<TextView>(android.R.id.text1)
        val icon = row.findViewById<ImageView>(android.R.id.icon)
        val emoji = row.findViewById<TextView>(R.id.presetIconEmoji)
        val visual = visualProvider(item)

        text.text = labelProvider(item)

        val iconSize = maxOf(icon.layoutParams?.width ?: 0, icon.layoutParams?.height ?: 0, 20)
        PresetVisuals.bind(context, visual, icon, emoji, iconSize)

        return row
    }
}