package com.moonbench.bifrost.schedule

import android.content.SharedPreferences
import org.json.JSONArray

object ScheduleStore {

    private const val PREF_KEY_RULES = "schedule_rules_json"
    private const val PREF_KEY_ENABLED = "schedule_enabled"

    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_KEY_ENABLED, false)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()
    }

    fun load(prefs: SharedPreferences): List<ScheduleRule> {
        val raw = prefs.getString(PREF_KEY_RULES, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val rules = mutableListOf<ScheduleRule>()
        for (i in 0 until array.length()) {
            ScheduleRule.parse(array.optJSONObject(i))?.let { rules += it }
        }
        return rules
    }

    fun save(prefs: SharedPreferences, rules: List<ScheduleRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.serialise()) }
        prefs.edit().putString(PREF_KEY_RULES, array.toString()).apply()
    }
}
