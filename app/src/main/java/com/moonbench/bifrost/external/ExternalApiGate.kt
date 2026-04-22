package com.moonbench.bifrost.external

import android.content.Context
import android.content.SharedPreferences

/**
 * Single gate for the external API. The master toggle is the only required
 * check for v1 — the per-package allowlist plumbing is intentionally absent
 * so settings stays a single switch. When that lands later it drops in here
 * without touching the receiver.
 */
object ExternalApiGate {

    const val PREFS_NAME = "bifrost_prefs"
    const val PREF_ENABLED = "external_api_enabled"

    fun isMasterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_ENABLED, false)

    fun isAllowed(context: Context, @Suppress("UNUSED_PARAMETER") callerPackage: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return isMasterEnabled(prefs)
    }
}
