package com.moonbench.bifrost

import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
                    if (position !in presets.indices) return

                    selectedIndex = position
                    val preset = presets[position]
                    saveLastPresetName(preset.name)
                    markIsUpdatingFromPreset(true)
                    applyPresetToUi(preset)
                    markIsUpdatingFromPreset(false)
                    onPresetApplied()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun refreshPresetSpinner(selectedName: String?) {
        val adapter = ArrayAdapter(activity, R.layout.item_spinner_bifrost, presets.map { it.name })
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown_bifrost)
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

            val accepted = obj.optBoolean("ragnarokAccepted", false)
            val useCustomSampling = obj.optBoolean("useCustomSampling", false)

            list.add(
                LedPreset(
                    name = name,
                    animationType = type,
                    performanceProfile = profile,
                    color = obj.optInt("color", Color.WHITE),
                    brightness = obj.optInt("brightness", 255),
                    speed = obj.optDouble("speed", 0.5).toFloat(),
                    smoothness = obj.optDouble("smoothness", 0.5).toFloat(),
                    sensitivity = obj.optDouble("sensitivity", 0.5).toFloat(),
                    saturationBoost = obj.optDouble("saturationBoost", 0.0).toFloat(),
                    useCustomSampling = useCustomSampling,
                    ragnarokAccepted = accepted
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
            obj.put("brightness", preset.brightness)
            obj.put("speed", preset.speed.toDouble())
            obj.put("smoothness", preset.smoothness.toDouble())
            obj.put("sensitivity", preset.sensitivity.toDouble())
            obj.put("saturationBoost", preset.saturationBoost.toDouble())
            obj.put("useCustomSampling", preset.useCustomSampling)
            obj.put("ragnarokAccepted", preset.ragnarokAccepted)
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
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_preset_name, null)
        val input = view.findViewById<TextInputEditText>(R.id.presetNameInput)

        val defaultName = "Preset ${presets.size + 1}"
        input.setText(defaultName)
        input.setSelection(defaultName.length)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val base = getCurrentConfig()
                val raw = input.text?.toString()?.trim().orEmpty()
                val desired = if (raw.isEmpty()) defaultName else raw
                val unique = ensureUniqueName(desired)
                val newPreset = base.copy(
                    name = unique,
                    ragnarokAccepted = base.performanceProfile == PerformanceProfile.RAGNAROK
                )
                presets.add(newPreset)
                savePresetsToPrefs()
                saveLastPresetName(unique)
                refreshPresetSpinner(unique)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
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
        val base = getCurrentConfig()

        val accepted = current.ragnarokAccepted || base.performanceProfile == PerformanceProfile.RAGNAROK

        val final = base.copy(
            name = current.name,
            ragnarokAccepted = accepted
        )

        presets[selectedIndex] = final
        savePresetsToPrefs()
        saveLastPresetName(final.name)

        markIsUpdatingFromPreset(true)
        applyPresetToUi(final)
        markIsUpdatingFromPreset(false)

        onPresetApplied()
    }

    private fun showDeleteDialog() {
        if (selectedIndex !in presets.indices) return
        val preset = presets[selectedIndex]

        deleteDialog.show(
            activity = activity,
            presetName = preset.name,
            onConfirm = {
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