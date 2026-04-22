package com.moonbench.bifrost.external

import android.content.SharedPreferences
import com.moonbench.bifrost.PresetIcon
import com.moonbench.bifrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes the same `presets_json` blob PresetController owns, but
 * only for presets tagged with `ownerPackage`. The in-memory list PresetController
 * keeps is refreshed on the next MainActivity.onResume sweep, so callers don't
 * need to coordinate with the UI process for installs to become visible.
 */
object ExternalProfileStore {

    private const val PREF_PRESETS = "presets_json"

    fun installManagedPreset(
        prefs: SharedPreferences,
        command: ExternalApiCommand.InstallProfile
    ): Boolean {
        val list = readList(prefs)
        val existingIndex = list.indexOfFirst { obj ->
            obj.optString("name") == command.profileName &&
                obj.optString("ownerPackage") == command.callerPackage
        }
        if (existingIndex >= 0 && !command.replaceIfExists) return false

        val serialized = command.toPresetJson()
        if (existingIndex >= 0) list[existingIndex] = serialized else list.add(serialized)
        saveList(prefs, list)
        return true
    }

    fun uninstallManagedPreset(
        prefs: SharedPreferences,
        callerPackage: String,
        profileName: String
    ): Boolean {
        val list = readList(prefs)
        val before = list.size
        list.removeAll { obj ->
            obj.optString("name") == profileName &&
                obj.optString("ownerPackage") == callerPackage
        }
        if (list.size == before) return false
        saveList(prefs, list)
        return true
    }

    fun removePresetsOwnedBy(prefs: SharedPreferences, pkg: String): List<String> {
        val list = readList(prefs)
        val removed = mutableListOf<String>()
        val iter = list.iterator()
        while (iter.hasNext()) {
            val obj = iter.next()
            if (obj.optString("ownerPackage") == pkg) {
                removed.add(obj.optString("name"))
                iter.remove()
            }
        }
        if (removed.isNotEmpty()) saveList(prefs, list)
        return removed
    }

    private fun ExternalApiCommand.InstallProfile.toPresetJson(): JSONObject = JSONObject().apply {
        put("name", profileName)
        put("animationType", effect.name)
        put("performanceProfile", PerformanceProfile.HIGH.name)
        put("color", color)
        put("rightColor", colorRight)
        put("brightness", intensity)
        put("speed", speed.toDouble())
        put("smoothness", smoothness.toDouble())
        put("sensitivity", sensitivity.toDouble())
        put("saturationBoost", saturationBoost.toDouble())
        put("useCustomSampling", useCustomSampling)
        put("useSingleColor", useSingleColor)
        put("breatheWhenCharging", breatheWhenCharging)
        put("indicateChargingSpeed", indicateChargingSpeed)
        put("flashWhenReady", flashWhenReady)
        batteryLowColor?.let { put("batteryLowColorOverride", it) }
        batteryMidColor?.let { put("batteryMidColorOverride", it) }
        batteryHighColor?.let { put("batteryHighColorOverride", it) }
        cpuCoolColor?.let { put("cpuCoolColorOverride", it) }
        cpuWarmColor?.let { put("cpuWarmColorOverride", it) }
        cpuHotColor?.let { put("cpuHotColorOverride", it) }
        put("isAppProfileDefault", false)
        put("ragnarokAccepted", false)
        put("icon", PresetIcon.defaultFor(effect).name)
        put("ownerPackage", callerPackage)
    }

    private fun readList(prefs: SharedPreferences): MutableList<JSONObject> {
        val json = prefs.getString(PREF_PRESETS, null)
        val array = if (json.isNullOrBlank()) JSONArray()
        else runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { list.add(it) }
        }
        return list
    }

    private fun saveList(prefs: SharedPreferences, list: List<JSONObject>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(PREF_PRESETS, array.toString()).apply()
    }
}
