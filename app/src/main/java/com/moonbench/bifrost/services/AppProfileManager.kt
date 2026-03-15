package com.moonbench.bifrost.services

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Process
import com.moonbench.bifrost.LedPreset
import com.moonbench.bifrost.PresetIcon
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject

class AppProfileManager(private val prefs: SharedPreferences) {

    companion object {
        private const val PREF_KEY_MAPPINGS = "app_profile_mappings"
        private const val PREF_KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
    }

    private var lastForegroundPackage: String? = null

    var isEnabled: Boolean
        get() = prefs.getBoolean(PREF_KEY_AUTO_SWITCH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_AUTO_SWITCH_ENABLED, value).apply()

    fun getMappings(): Map<String, String> {
        val json = prefs.getString(PREF_KEY_MAPPINGS, null) ?: return emptyMap()
        val obj = JSONObject(json)
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { key -> map[key] = obj.getString(key) }
        return map
    }

    fun setMapping(packageName: String, presetName: String) {
        val mappings = getMappings().toMutableMap()
        mappings[packageName] = presetName
        saveMappings(mappings)
    }

    fun removeMapping(packageName: String) {
        val mappings = getMappings().toMutableMap()
        mappings.remove(packageName)
        saveMappings(mappings)
    }

    private fun saveMappings(mappings: Map<String, String>) {
        val obj = JSONObject()
        mappings.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(PREF_KEY_MAPPINGS, obj.toString()).apply()
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
        if (stats.isNullOrEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    /**
     * Checks if the foreground app changed and returns the mapped preset if so.
     * Returns null if auto-switch is disabled, no change occurred, or no mapping exists.
     */
    fun checkForSwitch(context: Context): LedPreset? {
        if (!isEnabled) return null

        val currentPackage = getForegroundPackage(context) ?: return null
        if (currentPackage == lastForegroundPackage) return null
        lastForegroundPackage = currentPackage

        // Don't switch when Bifrost itself is in foreground
        if (currentPackage == context.packageName) return null

        val mappings = getMappings()
        val presetName = mappings[currentPackage] ?: return null

        return loadPresetByName(presetName)
    }

    fun resetLastForegroundPackage() {
        lastForegroundPackage = null
    }

    private fun loadPresetByName(name: String): LedPreset? {
        val json = prefs.getString("presets_json", null) ?: return null
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("name") != name) continue

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

            val color = obj.optInt("color", Color.WHITE)
            return LedPreset(
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
                useCustomSampling = obj.optBoolean("useCustomSampling", false),
                useSingleColor = obj.optBoolean("useSingleColor", false),
                breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
                indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
                flashWhenReady = obj.optBoolean("flashWhenReady", false),
                ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
                icon = icon,
                customEmoji = customEmoji,
                customImageFileName = customImageFileName
            )
        }
        return null
    }
}
