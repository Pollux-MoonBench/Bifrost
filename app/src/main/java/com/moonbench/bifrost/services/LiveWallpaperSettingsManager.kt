package com.moonbench.bifrost.services

import android.content.SharedPreferences
import android.net.Uri

object LiveWallpaperSettingsManager {
    private const val PREF_KEY_VIDEO_URI = "live_wallpaper_video_uri"
    private const val PREF_KEY_TARGET_FPS = "live_wallpaper_target_fps"
    private const val PREF_KEY_PERFORMANCE_MODE = "live_wallpaper_performance_mode"
    private const val PREF_KEY_AUTO_START = "live_wallpaper_auto_start"
    private const val PREF_KEY_IS_APPLIED = "live_wallpaper_is_applied"

    const val DEFAULT_TARGET_FPS = 30

    enum class PerformanceMode {
        ECO,
        BALANCED,
        PERFORMANCE
    }

    fun getVideoUri(prefs: SharedPreferences): Uri? {
        val raw = prefs.getString(PREF_KEY_VIDEO_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun setVideoUri(prefs: SharedPreferences, uri: Uri) {
        prefs.edit().putString(PREF_KEY_VIDEO_URI, uri.toString()).apply()
    }

    fun clearVideoUri(prefs: SharedPreferences) {
        prefs.edit().remove(PREF_KEY_VIDEO_URI).apply()
    }

    fun getTargetFps(prefs: SharedPreferences): Int {
        return prefs.getInt(PREF_KEY_TARGET_FPS, DEFAULT_TARGET_FPS).coerceIn(15, 120)
    }

    fun setTargetFps(prefs: SharedPreferences, fps: Int) {
        prefs.edit().putInt(PREF_KEY_TARGET_FPS, fps.coerceIn(15, 120)).apply()
    }

    fun getPerformanceMode(prefs: SharedPreferences): PerformanceMode {
        val raw = prefs.getString(PREF_KEY_PERFORMANCE_MODE, PerformanceMode.BALANCED.name)
        return runCatching { PerformanceMode.valueOf(raw ?: PerformanceMode.BALANCED.name) }
            .getOrDefault(PerformanceMode.BALANCED)
    }

    fun setPerformanceMode(prefs: SharedPreferences, mode: PerformanceMode) {
        prefs.edit().putString(PREF_KEY_PERFORMANCE_MODE, mode.name).apply()
    }

    fun isAutoStartEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_KEY_AUTO_START, false)
    }

    fun setAutoStartEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_AUTO_START, enabled).apply()
    }

    fun isWallpaperApplied(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_KEY_IS_APPLIED, false)
    }

    fun setWallpaperApplied(prefs: SharedPreferences, applied: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_IS_APPLIED, applied).apply()
    }
}
