package com.moonbench.bifrost.plugins

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Which screen's brightness a [LivePolicy] scales the LED brightness by.
 * NONE = don't scale (use the cap flat). Extend as new sources are needed.
 */
enum class BrightnessSource { NONE, MAIN_SCREEN, BOTTOM_SCREEN }

/**
 * A generic, plugin-declared policy for transforming a LIVE feed — the stream of
 * external `ACTION_DISPLAY` overrides a companion app pushes — before Bifrost
 * renders it. This is the universal plugin↔Bifrost interface for live bridging: a
 * plugin attaches a policy to an EFFECT TYPE (e.g. "PIPBOY") in its manifest, and
 * Bifrost applies it to live overrides of that effect with NO app-specific
 * knowledge. (Keyed by effect, not caller package, because a broadcast's caller
 * identity isn't recoverable in a BroadcastReceiver — it always resolves to
 * Bifrost's own uid.) Add fields here (and parse them in [fromJson]) to grow the
 * interface; older plugins omit them and fall back to defaults, newer Bifrost
 * ignores unknown extras.
 *
 * Fields currently understood:
 *  - [brightnessCapPercent]  cap the rendered LED brightness at this % of full
 *                            (null = no cap, pass the feed's own intensity through).
 *  - [brightnessSource]      scale that cap by a screen's brightness level, so the
 *                            sticks track e.g. the bottom screen (0..100 → 0..cap).
 *  - [colorStabilize]        reject transmission "collapse" frames — a frame that
 *                            is just the established hue with channels dropped out
 *                            (reads as a wrong/red flash) — holding the hue and
 *                            expressing the drop as a brightness duck instead.
 *  - [duckSeverity]          0..1 depth of that duck (0 = pure hold, 1 = full dip).
 */
data class LivePolicy(
    val brightnessCapPercent: Int? = null,
    val brightnessSource: BrightnessSource = BrightnessSource.NONE,
    val colorStabilize: Boolean = false,
    val duckSeverity: Float = 0.5f,
) {
    /** True if this policy has anything to do (else callers can skip it). */
    val isActive: Boolean
        get() = brightnessCapPercent != null || colorStabilize

    fun toJson(): JSONObject = JSONObject().apply {
        brightnessCapPercent?.let { put("brightnessCapPercent", it) }
        put("brightnessSource", brightnessSource.name)
        put("colorStabilize", colorStabilize)
        put("duckSeverity", duckSeverity.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): LivePolicy = LivePolicy(
            brightnessCapPercent =
                if (o.has("brightnessCapPercent")) o.optInt("brightnessCapPercent").coerceIn(0, 100) else null,
            brightnessSource = runCatching {
                BrightnessSource.valueOf(o.optString("brightnessSource", "NONE"))
            }.getOrDefault(BrightnessSource.NONE),
            colorStabilize = o.optBoolean("colorStabilize", false),
            duckSeverity = o.optDouble("duckSeverity", 0.5).toFloat().coerceIn(0f, 1f),
        )
    }
}

/**
 * Persists plugin-declared [LivePolicy]s, keyed by the effect type they apply to
 * (e.g. "PIPBOY"), in the shared prefs (a single JSON object). Each entry records
 * the owning plugin id so uninstall can clean up exactly what it installed —
 * mirroring how presets are owner-tagged. Lives in prefs (UI-decoupled), read by
 * LEDService.
 */
object LivePolicyStore {
    const val PREF = "live_policies_json"
    private const val OWNER_KEY = "_ownerPlugin"

    /** The policy registered for live overrides of [effectName], or null. */
    fun forEffect(prefs: SharedPreferences, effectName: String): LivePolicy? {
        val raw = prefs.getString(PREF, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val entry = root.optJSONObject(effectName) ?: return null
        return LivePolicy.fromJson(entry)
    }

    /** Install [policies] (effect name → policy) tagged with [ownerPluginId]. */
    fun putAll(prefs: SharedPreferences, ownerPluginId: String, policies: Map<String, LivePolicy>) {
        if (policies.isEmpty()) return
        val root = runCatching { JSONObject(prefs.getString(PREF, null) ?: "{}") }
            .getOrDefault(JSONObject())
        policies.forEach { (effect, policy) ->
            root.put(effect, policy.toJson().put(OWNER_KEY, ownerPluginId))
        }
        prefs.edit().putString(PREF, root.toString()).apply()
    }

    /** Remove every policy installed by [ownerPluginId] (used on uninstall/update). */
    fun removeByOwner(prefs: SharedPreferences, ownerPluginId: String) {
        val raw = prefs.getString(PREF, null) ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val drop = mutableListOf<String>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (root.optJSONObject(k)?.optString(OWNER_KEY) == ownerPluginId) drop.add(k)
        }
        if (drop.isEmpty()) return
        drop.forEach { root.remove(it) }
        prefs.edit().putString(PREF, root.toString()).apply()
    }
}
