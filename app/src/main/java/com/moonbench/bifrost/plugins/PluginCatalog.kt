package com.moonbench.bifrost.plugins

import org.json.JSONObject

/**
 * The plugin store data model.
 *
 * A "plugin" is a downloadable preset bundle (the existing
 * PresetArchiveTransfer ZIP — a preset + its app-profile mapping) plus the
 * catalogue metadata below. The catalogue itself is a single JSON document
 * hosted on GitHub (the plugins sub-repo) listing what's available; Bifrost
 * fetches it, compares versions against what's installed, and installs/updates
 * bundles on demand.
 *
 * Wire format (catalog.json):
 * {
 *   "schema": "bifrost_plugin_catalog",
 *   "version": 1,
 *   "plugins": [ { CatalogEntry }, ... ]
 * }
 */
data class PluginCatalog(
    val schemaVersion: Int,
    val plugins: List<CatalogEntry>,
) {
    companion object {
        const val SCHEMA = "bifrost_plugin_catalog"
        const val SUPPORTED_SCHEMA_VERSION = 1

        /**
         * Parse a catalogue JSON document. Throws CatalogParseException on a
         * malformed document or unsupported schema; skips individual entries
         * that are missing required fields (a single bad entry shouldn't sink
         * the whole store).
         */
        fun parse(json: String): PluginCatalog {
            val root = try {
                JSONObject(json)
            } catch (t: Throwable) {
                throw CatalogParseException("catalogue is not valid JSON: ${t.message}")
            }
            val schema = root.optString("schema")
            if (schema != SCHEMA) {
                throw CatalogParseException("unexpected schema '$schema' (want '$SCHEMA')")
            }
            val schemaVersion = root.optInt("version", 0)
            if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
                throw CatalogParseException(
                    "catalogue schema v$schemaVersion is newer than supported " +
                        "v$SUPPORTED_SCHEMA_VERSION — update Bifrost")
            }
            val arr = root.optJSONArray("plugins") ?: org.json.JSONArray()
            val entries = ArrayList<CatalogEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                CatalogEntry.parseOrNull(obj)?.let(entries::add)
            }
            return PluginCatalog(schemaVersion, entries)
        }
    }
}

/**
 * One plugin in the catalogue.
 *
 * [version] is the monotonic update key — an installed plugin is "out of date"
 * when the catalogue's [version] exceeds the recorded installed version.
 * [versionName] is for display only. [bundleUrl] points at the .bfplugin ZIP.
 */
data class CatalogEntry(
    val id: String,
    val name: String,
    val author: String,
    val version: Int,
    val versionName: String,
    val description: String,
    val icon: String,                  // a PresetIcon enum name; falls back to LIGHT
    val targetPackage: String?,        // app this plugin is for (informational/badge)
    val minBifrostVersionCode: Int,    // 0 = no floor
    val bundleUrl: String,
    val bundleSha256: String?,         // optional integrity check (lower-case hex)
) {
    companion object {
        /** Lenient parse — returns null if a required field is absent. */
        fun parseOrNull(o: JSONObject): CatalogEntry? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val bundleUrl = o.optString("bundleUrl").takeIf { it.isNotBlank() } ?: return null
            if (!o.has("version")) return null
            return CatalogEntry(
                id = id,
                name = o.optString("name", id),
                author = o.optString("author", ""),
                version = o.optInt("version", 1),
                versionName = o.optString("versionName", o.optInt("version", 1).toString()),
                description = o.optString("description", ""),
                icon = o.optString("icon", "LIGHT"),
                targetPackage = o.optString("targetPackage", "").takeIf { it.isNotBlank() },
                minBifrostVersionCode = o.optInt("minBifrostVersionCode", 0),
                bundleUrl = bundleUrl,
                bundleSha256 = o.optString("bundleSha256", "").takeIf { it.isNotBlank() }?.lowercase(),
            )
        }
    }
}

class CatalogParseException(message: String) : Exception(message)

/** An installed plugin that has a newer version available in the catalogue. */
data class PluginUpdate(
    val entry: CatalogEntry,
    val installedVersion: Int,
)
