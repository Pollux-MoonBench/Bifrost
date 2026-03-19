package com.moonbench.bifrost.services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Process
import android.util.Log
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
        private const val TAG = "BIBI"
        private const val PREF_KEY_MAPPINGS = "app_profile_mappings"
        private const val PREF_KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val PREF_KEY_PENDING_PROJECTION_PACKAGE = "pending_projection_package"
        private const val PREF_KEY_PENDING_PROJECTION_PRESET = "pending_projection_preset"
        private const val PREF_KEY_PENDING_PROJECTION_NOTIFIED = "pending_projection_notified"
        private const val FOREGROUND_QUERY_WINDOW_MS = 2500L
        private const val FOREGROUND_QUERY_CACHE_MS = 350L
        private const val HOME_PACKAGES_CACHE_MS = 10_000L
    }

    @Volatile
    private var lastForegroundPackage: String? = null
    @Volatile
    private var lastResolvedPresetName: String? = null
    @Volatile
    private var hasResolvedPresetOnce: Boolean = false
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

    fun renamePresetInMappings(oldName: String, newName: String) {
        if (oldName == newName) return
        val updated = getMappings().mapValues { (_, value) ->
            if (value == oldName) newName else value
        }
        saveMappings(updated)
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

        // UsageEvents is the most reactive & accurate source for foreground detection.
        val latestFromEvents = resolveForegroundFromEvents(usm, now)
        if (!latestFromEvents.isNullOrBlank()) {
            lastForegroundQueryAt = now
            cachedForegroundPackage = latestFromEvents
            Log.d(TAG, "getForegroundPackage: resolved from events → '$latestFromEvents'")
            return latestFromEvents
        }

        // No recent foreground events (e.g. user has been on home screen for a while
        // and the events window has scrolled past).  If we already have a cached
        // result (from a previous events-based detection), keep using it – falling
        // back to queryUsageStats here would return the *most recently used app*
        // (by lastTimeUsed), which is stale and wrong (e.g. it would return app A
        // even though the user is on the home screen).
        if (cachedForegroundPackage != null) {
            lastForegroundQueryAt = now
            Log.d(TAG, "getForegroundPackage: no recent events, keeping cached → '$cachedForegroundPackage'")
            return cachedForegroundPackage
        }

        // First-time bootstrap only: no events and no cache yet.
        // Use queryUsageStats as a last resort to seed the initial value.
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
            Log.d(TAG, "getForegroundPackage: bootstrap — no usage stats → null")
            return null
        }
        val latest = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        cachedForegroundPackage = latest
        Log.d(TAG, "getForegroundPackage: bootstrap from stats → '$latest'")
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
        if (!isEnabled) {
            Log.d(TAG, "checkForSwitch: app profile disabled, returning null")
            return null
        }
        if (!hasUsageStatsPermission(context)) {
            Log.d(TAG, "checkForSwitch: no usage stats permission, returning null")
            return null
        }

        val currentPackage = getForegroundPackage(context)
        Log.d(TAG, "checkForSwitch: currentPackage=$currentPackage, lastForegroundPackage=$lastForegroundPackage, lastResolvedPresetName=$lastResolvedPresetName, hasResolvedPresetOnce=$hasResolvedPresetOnce")
        lastForegroundPackage = currentPackage

        val mappings = getMappings()
        val fallbackPresetName = resolveDefaultPresetName()
        Log.d(TAG, "checkForSwitch: fallbackPresetName=$fallbackPresetName, mappingsCount=${mappings.size}")

        // If the foreground package is null/blank, this is a transient detection failure.
        // Do NOT switch away from the current preset.
        if (currentPackage.isNullOrBlank()) {
            Log.d(TAG, "checkForSwitch: foreground is null/blank → transient, keeping current preset (no switch)")
            return null
        }

        val isBifrostSelf = currentPackage == context.packageName
        val isHome = isHomePackage(context, currentPackage)
        val shouldUseFallback = isBifrostSelf || isHome
        Log.d(TAG, "checkForSwitch: isBifrostSelf=$isBifrostSelf, isHome=$isHome, shouldUseFallback=$shouldUseFallback")

        val presetName = if (shouldUseFallback) {
            fallbackPresetName
        } else {
            val mappedPreset = mappings[currentPackage]
            if (mappedPreset != null) {
                Log.d(TAG, "checkForSwitch: package '$currentPackage' is mapped to preset '$mappedPreset'")
            } else {
                Log.d(TAG, "checkForSwitch: package '$currentPackage' has NO mapping → using fallback")
            }
            mappedPreset ?: fallbackPresetName
        }

        Log.d(TAG, "checkForSwitch: resolved presetName='$presetName', lastResolvedPresetName='$lastResolvedPresetName', hasResolvedPresetOnce=$hasResolvedPresetOnce")

        if (hasResolvedPresetOnce && presetName == lastResolvedPresetName) {
            Log.d(TAG, "checkForSwitch: same preset as before, returning null (no change)")
            return null
        }
        lastResolvedPresetName = presetName
        hasResolvedPresetOnce = true

        val preset = presetName?.let { loadPresetByName(it) }
        Log.d(TAG, "checkForSwitch: SWITCHING → presetName='$presetName', preset animationType=${preset?.animationType}, presetIsNull=${preset == null}")
        return SwitchResult(presetName = presetName, preset = preset)
    }

    fun resetLastForegroundPackage() {
        Log.d(TAG, "resetLastForegroundPackage: clearing all tracking state")
        lastForegroundPackage = null
        lastResolvedPresetName = null
        hasResolvedPresetOnce = false
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
                batteryLowColorOverride = obj.optInt("batteryLowColorOverride").takeIf { obj.has("batteryLowColorOverride") },
                batteryMidColorOverride = obj.optInt("batteryMidColorOverride").takeIf { obj.has("batteryMidColorOverride") },
                batteryHighColorOverride = obj.optInt("batteryHighColorOverride").takeIf { obj.has("batteryHighColorOverride") },
                cpuCoolColorOverride = obj.optInt("cpuCoolColorOverride").takeIf { obj.has("cpuCoolColorOverride") },
                cpuWarmColorOverride = obj.optInt("cpuWarmColorOverride").takeIf { obj.has("cpuWarmColorOverride") },
                cpuHotColorOverride = obj.optInt("cpuHotColorOverride").takeIf { obj.has("cpuHotColorOverride") },
                ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
                icon = icon,
                customEmoji = customEmoji,
                customImageFileName = customImageFileName,
                appIconPackageName = appIconPackageName
            )
        }
        return null
    }

    // ── Pending projection token ──────────────────────────────────────────

    fun setPendingProjectionToken(packageName: String, presetName: String) {
        Log.d(TAG, "setPendingProjectionToken: packageName=$packageName, presetName=$presetName")
        prefs.edit()
            .putString(PREF_KEY_PENDING_PROJECTION_PACKAGE, packageName)
            .putString(PREF_KEY_PENDING_PROJECTION_PRESET, presetName)
            .putBoolean(PREF_KEY_PENDING_PROJECTION_NOTIFIED, false)
            .apply()
    }

    fun getPendingProjectionPackage(): String? =
        prefs.getString(PREF_KEY_PENDING_PROJECTION_PACKAGE, null)?.takeIf { it.isNotBlank() }

    fun getPendingProjectionPresetName(): String? =
        prefs.getString(PREF_KEY_PENDING_PROJECTION_PRESET, null)?.takeIf { it.isNotBlank() }

    fun hasPendingProjectionToken(): Boolean =
        getPendingProjectionPackage() != null

    fun isPendingProjectionNotified(): Boolean =
        prefs.getBoolean(PREF_KEY_PENDING_PROJECTION_NOTIFIED, false)

    fun markPendingProjectionNotified() {
        prefs.edit().putBoolean(PREF_KEY_PENDING_PROJECTION_NOTIFIED, true).apply()
    }

    fun clearPendingProjectionToken() {
        Log.d(TAG, "clearPendingProjectionToken: clearing pending projection state")
        prefs.edit()
            .remove(PREF_KEY_PENDING_PROJECTION_PACKAGE)
            .remove(PREF_KEY_PENDING_PROJECTION_PRESET)
            .remove(PREF_KEY_PENDING_PROJECTION_NOTIFIED)
            .apply()
    }

    /**
     * Force the next [checkForSwitch] to re-evaluate even if the preset name
     * would normally match the dedup cache.  This is used after projection
     * data is supplied so the MP-requiring preset is actually applied.
     */
    fun forceNextResolution() {
        Log.d(TAG, "forceNextResolution: clearing lastResolvedPresetName so next check forces a result")
        lastResolvedPresetName = null
        hasResolvedPresetOnce = false
    }
}
