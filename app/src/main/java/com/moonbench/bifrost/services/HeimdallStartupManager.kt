package com.moonbench.bifrost.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject

object HeimdallStartupManager {
    private const val PREF_KEY_AUTO_START_HEIMDALL = "auto_start_heimdall"
    private const val PREF_KEY_PRESETS = "presets_json"
    private const val PREF_KEY_LAST_PRESET = "last_preset_name"
    private const val PREF_KEY_BATTERY_OVERRIDE_WHEN_PLUGGED = "battery_override_when_plugged"
    private const val PREF_KEY_PERSISTENT_NOTIFICATION = "persistent_notification_enabled"

    fun isAutoStartEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_KEY_AUTO_START_HEIMDALL, false)
    }

    fun setAutoStartEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_AUTO_START_HEIMDALL, enabled).apply()
    }

    enum class StartupSkipReason {
        NO_PRESET_AVAILABLE,
        MEDIA_PROJECTION_REQUIRES_USER_ACTION
    }

    data class StartupDecision(
        val serviceIntent: Intent?,
        val skipReason: StartupSkipReason? = null
    )

    fun buildStartupServiceIntent(context: Context, prefs: SharedPreferences): Intent? {
        return buildStartupDecision(context, prefs).serviceIntent
    }

    fun buildStartupDecision(context: Context, prefs: SharedPreferences): StartupDecision {
        val preset = loadStartupPreset(prefs)
            ?: return StartupDecision(
                serviceIntent = null,
                skipReason = StartupSkipReason.NO_PRESET_AVAILABLE
            )

        // MediaProjection sessions are not restorable at boot without user interaction.
        if (preset.animationType.needsMediaProjection) {
            return StartupDecision(
                serviceIntent = null,
                skipReason = StartupSkipReason.MEDIA_PROJECTION_REQUIRES_USER_ACTION
            )
        }

        val serviceIntent = Intent(context, LEDService::class.java).apply {
            putExtra("animationType", preset.animationType.name)
            putExtra("performanceProfile", preset.performanceProfile.name)
            putExtra("animationColor", preset.color)
            putExtra("animationRightColor", preset.rightColor)
            putExtra("brightness", preset.brightness)
            putExtra("speed", preset.speed)
            putExtra("smoothness", preset.smoothness)
            putExtra("sensitivity", preset.sensitivity)
            putExtra("saturationBoost", preset.saturationBoost)
            putExtra("useCustomSampling", preset.useCustomSampling)
            putExtra("useSingleColor", preset.useSingleColor)
            putExtra("breatheWhenCharging", preset.breatheWhenCharging)
            putExtra("indicateChargingSpeed", preset.indicateChargingSpeed)
            putExtra("flashWhenReady", preset.flashWhenReady)
            putExtra(
                LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED,
                prefs.getBoolean(PREF_KEY_BATTERY_OVERRIDE_WHEN_PLUGGED, false)
            )
            putExtra(
                LEDService.EXTRA_PERSISTENT_NOTIFICATION,
                prefs.getBoolean(PREF_KEY_PERSISTENT_NOTIFICATION, true)
            )
            putExtra(LEDService.EXTRA_ALLOW_BACKGROUND_RUN, true)
        }

        return StartupDecision(serviceIntent = serviceIntent)
    }

    private fun loadStartupPreset(prefs: SharedPreferences): StartupPreset? {
        val raw = prefs.getString(PREF_KEY_PRESETS, null) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        if (array.length() == 0) return null

        val lastPresetName = prefs.getString(PREF_KEY_LAST_PRESET, null)
        val presetObj = findPresetObject(array, lastPresetName) ?: return null
        return parsePreset(presetObj)
    }

    private fun findPresetObject(array: JSONArray, name: String?): JSONObject? {
        if (name != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("name") == name) {
                    return obj
                }
            }
        }

        return array.optJSONObject(0)
    }

    private fun parsePreset(obj: JSONObject): StartupPreset {
        val animationType = runCatching {
            LedAnimationType.valueOf(obj.optString("animationType", LedAnimationType.STATIC.name))
        }.getOrDefault(LedAnimationType.STATIC)

        val performanceProfile = runCatching {
            PerformanceProfile.valueOf(
                obj.optString("performanceProfile", PerformanceProfile.HIGH.name)
            )
        }.getOrDefault(PerformanceProfile.HIGH)

        val color = obj.optInt("color", Color.WHITE)

        return StartupPreset(
            animationType = animationType,
            performanceProfile = performanceProfile,
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
            flashWhenReady = obj.optBoolean("flashWhenReady", false)
        )
    }

    private data class StartupPreset(
        val animationType: LedAnimationType,
        val performanceProfile: PerformanceProfile,
        val color: Int,
        val rightColor: Int,
        val brightness: Int,
        val speed: Float,
        val smoothness: Float,
        val sensitivity: Float,
        val saturationBoost: Float,
        val useCustomSampling: Boolean,
        val useSingleColor: Boolean,
        val breatheWhenCharging: Boolean,
        val indicateChargingSpeed: Boolean,
        val flashWhenReady: Boolean
    )
}
