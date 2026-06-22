package com.moonbench.bifrost.plugins

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog

/**
 * Wires the plugin store into app launch:
 *
 *  - [maybePromptForUpdateChecks]: a ONE-TIME prompt, shown on first launch,
 *    asking whether to check for plugin updates at startup. The setting itself
 *    defaults OFF — nothing phones home until the user opts in.
 *
 *  - [maybeCheckAtLaunch]: if the user opted in, fetch the catalogue off the
 *    main thread and report any installed plugin that has a newer version. No
 *    network call at all when disabled or when nothing is installed.
 */
object PluginLaunchManager {

    /**
     * First-launch consent prompt. Marks itself shown immediately so it never
     * reappears (even if dismissed), and leaves the setting OFF unless the user
     * taps Enable. Safe to call every launch — no-ops after the first.
     */
    fun maybePromptForUpdateChecks(activity: Activity) {
        val prefs = PluginPrefs.prefs(activity)
        if (PluginPrefs.updatePromptShown(prefs)) return
        // This runs from a posted MainActivity init callback; the AYN launcher's
        // display 4 → 0 relaunch can leave it executing against a destroyed
        // activity, and AlertDialog.show() on a dead token throws BadTokenException
        // (fatal). Bail BEFORE marking shown so a live launch still gets the
        // one-time prompt rather than silently consuming it on the dead instance.
        if (activity.isFinishing || activity.isDestroyed) return
        PluginPrefs.markUpdatePromptShown(prefs)

        AlertDialog.Builder(activity)
            .setTitle("Check for plugin updates?")
            .setMessage(
                "Bifrost can check the plugin store for updates each time it " +
                    "starts. That's a small network request at launch. It's OFF " +
                    "by default — you can change it anytime in the Plugin Store."
            )
            .setPositiveButton("Enable") { _, _ ->
                PluginPrefs.setCheckUpdatesAtLaunch(prefs, true)
            }
            .setNegativeButton("Not now") { _, _ ->
                PluginPrefs.setCheckUpdatesAtLaunch(prefs, false)
            }
            .setCancelable(true)
            .show()
    }

    /**
     * If the "check at launch" setting is on AND at least one plugin is
     * installed, fetch the catalogue on a background thread and invoke
     * [onUpdatesAvailable] on the main thread with any out-of-date plugins.
     * Completely silent (no network) otherwise.
     */
    fun maybeCheckAtLaunch(context: Context, onUpdatesAvailable: (List<PluginUpdate>) -> Unit) {
        val prefs = PluginPrefs.prefs(context)
        if (!PluginPrefs.checkUpdatesAtLaunch(prefs)) return
        if (PluginPrefs.installedVersions(prefs).isEmpty()) return

        Thread {
            val result = PluginRepository.fetchCatalog(PluginPrefs.catalogUrl(prefs))
            if (result is PluginRepository.CatalogResult.Success) {
                val updates = PluginRepository.computeUpdates(result.catalog, prefs)
                if (updates.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).post { onUpdatesAvailable(updates) }
                }
            }
        }.apply { isDaemon = true; name = "plugin-update-check" }.start()
    }
}
