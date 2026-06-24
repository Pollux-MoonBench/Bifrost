package com.moonbench.bifrost.plugins

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.moonbench.bifrost.LedPreset
import com.moonbench.bifrost.PresetArchiveTransfer
import com.moonbench.bifrost.external.ExternalProfileStore
import com.moonbench.bifrost.services.AppProfileManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Installs / updates / removes plugin bundles.
 *
 * A plugin bundle is the existing PresetArchiveTransfer ZIP (preset(s) + app
 * mappings). Installing = download → import → write the preset(s) into the
 * shared `presets_json` tagged with an owner key, apply the app→preset
 * mapping(s), and record the installed version. Everything is written to
 * SharedPreferences (UI-decoupled); MainActivity.onResume's reloadFromPrefs
 * sweep makes it visible. Updates are just a re-install of a newer version
 * (the prior version's owned presets are cleared first). Uninstall removes the
 * owned presets, the mappings that referenced them, and the version record.
 *
 * Blocking (network + disk) — call off the main thread.
 */
object PluginInstaller {

    private const val PREF_PRESETS = "presets_json"
    private const val TAG = "PluginInstaller"

    sealed class Result {
        data class Success(val presetNames: List<String>) : Result()
        data class Failure(val message: String) : Result()
    }

    /** Owner tag stored on a plugin's presets, used to clean them up later. */
    fun ownerOf(id: String): String = "plugin:$id"

    fun install(context: Context, prefs: SharedPreferences, entry: CatalogEntry): Result {
        if (entry.minBifrostVersionCode > appVersionCode(context)) {
            return Result.Failure(
                "needs Bifrost version ${entry.minBifrostVersionCode}+ (you have ${appVersionCode(context)})")
        }

        val cache = File(context.cacheDir, "plugins/${entry.id}-${entry.version}.bfplugin")
        when (val dl = PluginRepository.downloadBundle(entry, cache)) {
            is PluginRepository.DownloadResult.Failure -> return Result.Failure(dl.message)
            is PluginRepository.DownloadResult.Success -> Unit
        }

        val imported = try {
            PresetArchiveTransfer.importFromUri(context, Uri.fromFile(cache))
        } catch (t: Throwable) {
            return Result.Failure("import failed: ${t.message}")
        } finally {
            if (cache.exists() && !cache.delete()) {
                Log.w(TAG, "could not delete plugin cache: ${cache.absolutePath}")
            }
        }
        if (imported.errors.isNotEmpty()) return Result.Failure(imported.errors.joinToString("; "))
        if (imported.presets.isEmpty()) return Result.Failure("bundle contains no presets")

        val owner = ownerOf(entry.id)
        // Clear any prior version's presets + live policies first (clean update).
        ExternalProfileStore.removePresetsOwnedBy(prefs, owner)
        LivePolicyStore.removeByOwner(prefs, entry.id)

        val list = readPresets(prefs)
        imported.presets.forEach { list.put(presetToJson(it, owner)) }
        savePresets(prefs, list)

        // Apply the app→preset mappings (so app-profile mode auto-plays it).
        val apm = AppProfileManager(prefs)
        imported.mappings.forEach { (pkg, presetName) -> apm.setMapping(pkg, presetName) }

        // Register the generic live-feed policies (effect name → policy), which
        // Bifrost applies to live overrides of that effect. Owner-tagged so
        // uninstall removes exactly these.
        LivePolicyStore.putAll(prefs, entry.id, imported.livePolicies)

        PluginPrefs.setInstalled(prefs, entry.id, entry.version)
        return Result.Success(imported.presets.map { it.name })
    }

    fun uninstall(prefs: SharedPreferences, entry: CatalogEntry): Result {
        val removed = ExternalProfileStore.removePresetsOwnedBy(prefs, ownerOf(entry.id))
        if (removed.isNotEmpty()) {
            AppProfileManager(prefs).removeMappingsReferencing(removed)
        }
        LivePolicyStore.removeByOwner(prefs, entry.id)
        PluginPrefs.removeInstalled(prefs, entry.id)
        return Result.Success(removed)
    }

    // ---- preset serialization (faithful to PresetController's format) -----

    private fun presetToJson(p: LedPreset, owner: String): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("animationType", p.animationType.name)
        put("performanceProfile", p.performanceProfile.name)
        put("color", p.color)
        put("rightColor", p.rightColor)
        put("brightness", p.brightness)
        put("speed", p.speed.toDouble())
        put("smoothness", p.smoothness.toDouble())
        put("sensitivity", p.sensitivity.toDouble())
        put("saturationBoost", p.saturationBoost.toDouble())
        put("useCustomSampling", p.useCustomSampling)
        put("useSingleColor", p.useSingleColor)
        put("breatheWhenCharging", p.breatheWhenCharging)
        put("indicateChargingSpeed", p.indicateChargingSpeed)
        put("flashWhenReady", p.flashWhenReady)
        p.batteryLowColorOverride?.let { put("batteryLowColorOverride", it) }
        p.batteryMidColorOverride?.let { put("batteryMidColorOverride", it) }
        p.batteryHighColorOverride?.let { put("batteryHighColorOverride", it) }
        p.cpuCoolColorOverride?.let { put("cpuCoolColorOverride", it) }
        p.cpuWarmColorOverride?.let { put("cpuWarmColorOverride", it) }
        p.cpuHotColorOverride?.let { put("cpuHotColorOverride", it) }
        put("isAppProfileDefault", p.isAppProfileDefault)
        put("ragnarokAccepted", p.ragnarokAccepted)
        put("icon", p.icon.name)
        p.customEmoji?.let { put("customEmoji", it) }
        p.customImageFileName?.let { put("customImageFileName", it) }
        p.appIconPackageName?.let { put("appIconPackageName", it) }
        put("ownerPackage", owner)
    }

    private fun readPresets(prefs: SharedPreferences): JSONArray {
        val json = prefs.getString(PREF_PRESETS, null)
        return if (json.isNullOrBlank()) JSONArray()
        else runCatching { JSONArray(json) }.getOrDefault(JSONArray())
    }

    private fun savePresets(prefs: SharedPreferences, array: JSONArray) {
        prefs.edit().putString(PREF_PRESETS, array.toString()).apply()
    }

    private fun appVersionCode(context: Context): Int = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (t: Throwable) {
        0
    }
}
