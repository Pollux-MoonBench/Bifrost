package com.moonbench.bifrost.services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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

    data class SwitchResult(
        val presetName: String?,
        val preset: LedPreset?
    )

    companion object {
        private const val PREF_KEY_MAPPINGS = "app_profile_mappings"
        private const val PREF_KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val FOREGROUND_QUERY_WINDOW_MS = 2500L
        private const val FOREGROUND_QUERY_CACHE_MS = 350L
        private const val HOME_PACKAGES_CACHE_MS = 10_000L
    }

    @Volatile
    private var lastForegroundPackage: String? = null
    @Volatile
    private var lastResolvedPresetName: String? = null
    private var cachedMappingsRaw: String? = null
    private var cachedMappings: Map<String, String> = emptyMap()
    private var lastForegroundQueryAt: Long = 0L
    private var cachedForegroundPackage: String? = null
    private var lastHomePackagesQueryAt: Long = 0L
    private var cachedHomePackages: Set<String> = emptySet()

    var isEnabled: Boolean
        get() = prefs.getBoolean(PREF_KEY_AUTO_SWITCH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_AUTO_SWITCH_ENABLED, value).apply()

    fun getMappings(): Map<String, String> {
        val json = prefs.getString(PREF_KEY_MAPPINGS, null)
        if (json.isNullOrBlank()) {
            cachedMappingsRaw = null
            cachedMappings = emptyMap()
            return emptyMap()
        }

        if (json == cachedMappingsRaw) {
            return cachedMappings
        }

        val parsed = runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.optString(key))
                }
            }
        }.getOrDefault(emptyMap())

        cachedMappingsRaw = json
        cachedMappings = parsed
        return parsed
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

    fun replaceMappings(mappings: Map<String, String>) {
        saveMappings(mappings)
    }

    private fun saveMappings(mappings: Map<String, String>) {
        val obj = JSONObject()
        mappings.forEach { (k, v) -> obj.put(k, v) }
        val raw = obj.toString()
        prefs.edit().putString(PREF_KEY_MAPPINGS, raw).apply()
        cachedMappingsRaw = raw
        cachedMappings = mappings.toMap()
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

        val now = System.currentTimeMillis()
        if (now - lastForegroundQueryAt < FOREGROUND_QUERY_CACHE_MS) {
            return cachedForegroundPackage
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // UsageEvents is typically more reactive than aggregated UsageStats.
        val latestFromEvents = resolveForegroundFromEvents(usm, now)
        if (!latestFromEvents.isNullOrBlank()) {
            lastForegroundQueryAt = now
            cachedForegroundPackage = latestFromEvents
            return latestFromEvents
        }

        val stats = runCatching {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - FOREGROUND_QUERY_WINDOW_MS,
                now
            )
        }.getOrNull()

        lastForegroundQueryAt = now
        if (stats.isNullOrEmpty()) {
            cachedForegroundPackage = null
            return null
        }
        val latest = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        cachedForegroundPackage = latest
        return latest
    }

    private fun resolveForegroundFromEvents(
        usageStatsManager: UsageStatsManager,
        now: Long
    ): String? {
        val events = runCatching {
            usageStatsManager.queryEvents(now - FOREGROUND_QUERY_WINDOW_MS, now)
        }.getOrNull() ?: return null

        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            if (!isForegroundEvent) continue

            val packageName = event.packageName ?: continue
            if (event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = packageName
            }
        }

        return latestPackage
    }

    /**
     * Resolves the effective app-profile preset from current foreground package.
     * Returns null when no effective change happened since the previous check.
     */
    fun checkForSwitch(context: Context): SwitchResult? {
        if (!isEnabled) return null
        if (!hasUsageStatsPermission(context)) return null

        val currentPackage = getForegroundPackage(context)
        lastForegroundPackage = currentPackage

        val mappings = getMappings()
        val fallbackPresetName = resolveDefaultPresetName()

        // Returning to launcher/home (or transiently no detected foreground package)
        // should resolve to the app-profile fallback instead of keeping the old app preset.
        val shouldUseFallback = currentPackage.isNullOrBlank() ||
            currentPackage == context.packageName ||
            isHomePackage(context, currentPackage)

        val presetName = if (shouldUseFallback) {
            fallbackPresetName
        } else {
            mappings[currentPackage] ?: fallbackPresetName
        }

        if (presetName == lastResolvedPresetName) return null
        lastResolvedPresetName = presetName

        val preset = presetName?.let { loadPresetByName(it) }
        return SwitchResult(presetName = presetName, preset = preset)
    }

    fun resetLastForegroundPackage() {
        lastForegroundPackage = null
        lastResolvedPresetName = null
        cachedForegroundPackage = null
        lastForegroundQueryAt = 0L
        lastHomePackagesQueryAt = 0L
        cachedHomePackages = emptySet()
    }

    private fun isHomePackage(context: Context, packageName: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastHomePackagesQueryAt >= HOME_PACKAGES_CACHE_MS || cachedHomePackages.isEmpty()) {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            cachedHomePackages = runCatching {
                context.packageManager.queryIntentActivities(homeIntent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
            lastHomePackagesQueryAt = now
        }
        return packageName in cachedHomePackages
    }

    private fun resolveDefaultPresetName(): String? {
        val json = prefs.getString("presets_json", null) ?: return null
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (!obj.optBoolean("isAppProfileDefault", false)) continue
            val name = obj.optString("name").takeIf { it.isNotBlank() }
            if (name != null) return name
        }

        return null
    }

    private fun loadPresetByName(name: String): LedPreset? {
        val json = prefs.getString("presets_json", null) ?: return null
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null

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
            val appIconPackageName = obj.optString("appIconPackageName")
                .takeIf { it.isNotBlank() }

            val color = obj.optInt("color", Color.WHITE)
            return LedPreset(
                name = name,
                animationType = type,
                performanceProfile = profile,
                color = color,
                rightColor = obj.optInt("rightColor", color),
                brightness = obj.optInt("brightness", 255).coerceIn(0, 255),
                speed = obj.optDouble("speed", 0.5).toFloat().coerceIn(0f, 1f),
                smoothness = obj.optDouble("smoothness", 0.5).toFloat().coerceIn(0f, 1f),
                sensitivity = obj.optDouble("sensitivity", 0.5).toFloat().coerceIn(0f, 1f),
                saturationBoost = obj.optDouble("saturationBoost", 0.0).toFloat().coerceIn(0f, 1f),
                useCustomSampling = obj.optBoolean("useCustomSampling", false),
                useSingleColor = obj.optBoolean("useSingleColor", false),
                breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
                indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
                flashWhenReady = obj.optBoolean("flashWhenReady", false),
                ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
                icon = icon,
                customEmoji = customEmoji,
                customImageFileName = customImageFileName,
                appIconPackageName = appIconPackageName
            )
        }
        return null
    }
}
