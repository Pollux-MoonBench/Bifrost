package com.moonbench.bifrost.plugins

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persistent state for the plugin store, all in the shared "bifrost_prefs".
 *
 *  - the catalogue URL (overridable; defaults to the GitHub plugins sub-repo)
 *  - "check for updates at launch" — DEFAULT OFF (no surprise network calls)
 *  - whether the first-launch enable-prompt has been shown
 *  - installed pluginId → version map (the update key)
 */
object PluginPrefs {

    const val PREFS_NAME = "bifrost_prefs"

    const val PREF_CATALOG_URL = "plugin_catalog_url"
    const val PREF_CHECK_UPDATES_AT_LAUNCH = "plugin_check_updates_at_launch"
    const val PREF_UPDATE_PROMPT_SHOWN = "plugin_update_prompt_shown"
    const val PREF_INSTALLED_VERSIONS = "plugin_installed_versions"  // JSON {id:version}

    /** Catalogue id of the Fallout Pip-Boy plugin (the one honouring mirror mode). */
    const val FALLOUT_PLUGIN_ID = "fallout4-pipboy"

    /**
     * Default catalogue. Served from the upstream Bifrost repo's dedicated
     * `plugin-catalog` branch (content-only, gh-pages style — permanent, so the
     * URL never breaks when feature branches merge). Overridable in the plugin
     * store UI for forks / local testing.
     */
    const val DEFAULT_CATALOG_URL =
        "https://raw.githubusercontent.com/Pollux-MoonBench/Bifrost/plugin-catalog/catalog.json"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun catalogUrl(prefs: SharedPreferences): String =
        prefs.getString(PREF_CATALOG_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_CATALOG_URL

    fun setCatalogUrl(prefs: SharedPreferences, url: String) {
        prefs.edit().putString(PREF_CATALOG_URL, url.trim()).apply()
    }

    /** "Check for plugin updates at launch" — defaults OFF. */
    fun checkUpdatesAtLaunch(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_CHECK_UPDATES_AT_LAUNCH, false)

    fun setCheckUpdatesAtLaunch(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_CHECK_UPDATES_AT_LAUNCH, enabled).apply()
    }

    /** Has the one-time "enable update checks?" prompt been shown yet? */
    fun updatePromptShown(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_UPDATE_PROMPT_SHOWN, false)

    fun markUpdatePromptShown(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PREF_UPDATE_PROMPT_SHOWN, true).apply()
    }

    /**
     * Per-plugin "mirror the screen instead of the plugin's native effect"
     * toggle (OFF by default). Currently only the Fallout Pip-Boy plugin honours
     * it: ON makes Bifrost AMBIENT-mirror whichever display shows the Pip-Boy
     * instead of running the event-driven feed. Heavier; needs the accessibility
     * screenshot service. Keyed by plugin id.
     */
    fun isMirrorScreen(prefs: SharedPreferences, id: String): Boolean =
        prefs.getBoolean("plugin_mirror_screen_$id", false)

    fun setMirrorScreen(prefs: SharedPreferences, id: String, enabled: Boolean) {
        prefs.edit().putBoolean("plugin_mirror_screen_$id", enabled).apply()
    }

    // ---- installed-version map ------------------------------------------

    fun installedVersions(prefs: SharedPreferences): Map<String, Int> {
        val raw = prefs.getString(PREF_INSTALLED_VERSIONS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optInt(k, 0)) }
            }
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    fun installedVersion(prefs: SharedPreferences, id: String): Int? =
        installedVersions(prefs)[id]

    fun setInstalled(prefs: SharedPreferences, id: String, version: Int) {
        val map = installedVersions(prefs).toMutableMap()
        map[id] = version
        writeInstalled(prefs, map)
    }

    fun removeInstalled(prefs: SharedPreferences, id: String) {
        val map = installedVersions(prefs).toMutableMap()
        if (map.remove(id) != null) writeInstalled(prefs, map)
    }

    private fun writeInstalled(prefs: SharedPreferences, map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(PREF_INSTALLED_VERSIONS, obj.toString()).apply()
    }
}
