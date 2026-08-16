package com.moonbench.bifrost.presets

import android.content.SharedPreferences
import com.moonbench.bifrost.animations.LedAnimationType
import org.json.JSONArray

object PresetMigration {

    private const val PREF_KEY_PRESETS = "presets_json"
    private const val PREF_KEY_DONE = "preset_migration_animation_names"

    fun run(prefs: SharedPreferences) {
        if (prefs.getBoolean(PREF_KEY_DONE, false)) return

        val stored = prefs.getString(PREF_KEY_PRESETS, null)
        val migrated = stored?.let { migrateAnimationNames(it) }

        prefs.edit().apply {
            if (migrated != null && migrated != stored) putString(PREF_KEY_PRESETS, migrated)
            putBoolean(PREF_KEY_DONE, true)
        }.apply()
    }

    fun migrateAnimationNames(json: String): String? {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
        var changed = false

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val stored = obj.optString("animationType")
            if (stored.isBlank()) continue

            val resolved = LedAnimationType.fromStoredName(stored) ?: continue
            if (resolved.name != stored) {
                obj.put("animationType", resolved.name)
                changed = true
            }
        }

        return if (changed) array.toString() else json
    }
}
