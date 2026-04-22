package com.moonbench.bifrost

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.CancellationSignal
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupArchiveTransfer {

    private const val BACKUP_SCHEMA = "bifrost_full_backup"
    private const val BACKUP_VERSION = 1
    private const val PREF_FILE_NAME = "bifrost_prefs"
    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val PREFS_ENTRY_NAME = "prefs.json"
    private const val ICONS_DIR_PREFIX = "icons/"
    private val THEME_PREF_KEYS = setOf(
        "selected_ui_theme",
        "colored_logo_enabled"
    )
    private val PROFILE_PREF_KEYS = setOf(
        "presets_json",
        "last_preset_name",
        "app_profile_mappings",
        "auto_switch_enabled",
        "pending_projection_package",
        "pending_projection_preset",
        "pending_projection_notified"
    )

    data class CategoryOptions(
        val themes: Boolean = true,
        val profiles: Boolean = true,
        val images: Boolean = true
    ) {
        fun hasAtLeastOneCategory(): Boolean = themes || profiles || images
    }

    data class ExportResult(
        val preferenceCount: Int,
        val iconCount: Int,
        val appliedOptions: CategoryOptions,
        val warnings: List<String>
    )

    data class ImportResult(
        val preferenceCount: Int,
        val iconCount: Int,
        val appliedOptions: CategoryOptions,
        val warnings: List<String>,
        val errors: List<String>
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        options: CategoryOptions = CategoryOptions(),
        cancelSignal: CancellationSignal? = null
    ): ExportResult {
        if (!options.hasAtLeastOneCategory()) {
            return ExportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = listOf("No categories were selected for backup.")
            )
        }

        val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        val warnings = mutableListOf<String>()
        val prefsJson = serializePreferences(prefs, options)
        val iconNames = if (options.images) {
            PresetImageStorage.listStoredIconFileNames(context)
        } else {
            emptyList()
        }
        var exportedIconCount = 0

        val manifest = JSONObject().apply {
            put("schema", BACKUP_SCHEMA)
            put("version", BACKUP_VERSION)
            put("prefsFile", PREF_FILE_NAME)
            put("iconDirectory", "preset_icons")
            put("categories", JSONObject().apply {
                put("themes", options.themes)
                put("profiles", options.profiles)
                put("images", options.images)
            })
        }

        cancelSignal?.throwIfCanceled()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                cancelSignal?.throwIfCanceled()
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                cancelSignal?.throwIfCanceled()
                zip.putNextEntry(ZipEntry(PREFS_ENTRY_NAME))
                zip.write(prefsJson.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                iconNames.forEach { iconName ->
                    cancelSignal?.throwIfCanceled()
                    val input = PresetImageStorage.openIconInputStream(context, iconName)
                    if (input == null) {
                        warnings += "Image not found for '$iconName'; skipping."
                        return@forEach
                    }

                    input.use { iconStream ->
                        cancelSignal?.throwIfCanceled()
                        zip.putNextEntry(ZipEntry("$ICONS_DIR_PREFIX$iconName"))
                        iconStream.copyTo(zip)
                        zip.closeEntry()
                        exportedIconCount++
                    }
                }
            }
        } ?: return ExportResult(
            preferenceCount = 0,
            iconCount = 0,
            appliedOptions = options,
            warnings = listOf("Unable to write selected file.")
        )

        return ExportResult(
            preferenceCount = prefs.all.size,
            iconCount = exportedIconCount,
            appliedOptions = options,
            warnings = warnings
        )
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
        options: CategoryOptions = CategoryOptions(),
        cancelSignal: CancellationSignal? = null
    ): ImportResult {
        if (!options.hasAtLeastOneCategory()) {
            return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = emptyList(),
                errors = listOf("No categories were selected for restore.")
            )
        }

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val zipEntries = mutableMapOf<String, ByteArray>()
        cancelSignal?.throwIfCanceled()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    cancelSignal?.throwIfCanceled()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val data = ByteArrayOutputStream().use { output ->
                        zip.copyTo(output)
                        output.toByteArray()
                    }
                    zipEntries[entry.name] = data
                    zip.closeEntry()
                }
            }
        } ?: return ImportResult(
            preferenceCount = 0,
            iconCount = 0,
            appliedOptions = options,
            warnings = emptyList(),
            errors = listOf("Unable to read selected file.")
        )

        val manifestRaw = zipEntries[MANIFEST_ENTRY_NAME]
            ?: return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = emptyList(),
                errors = listOf("Archive is missing manifest.json")
            )

        val manifest = runCatching {
            JSONObject(manifestRaw.toString(Charsets.UTF_8))
        }.getOrElse {
            return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = emptyList(),
                errors = listOf("manifest.json is invalid JSON")
            )
        }

        if (manifest.optString("schema") != BACKUP_SCHEMA) {
            errors += "Unknown backup schema."
            return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = warnings,
                errors = errors
            )
        }

        val version = manifest.optInt("version", -1)
        if (version < 1) {
            errors += "Unsupported backup version: $version"
            return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = warnings,
                errors = errors
            )
        } else if (version > BACKUP_VERSION) {
            warnings += "Backup version $version is newer than supported version $BACKUP_VERSION. Attempting compatible restore."
        }

        val prefsRaw = zipEntries[PREFS_ENTRY_NAME]
            ?: return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = warnings,
                errors = listOf("Archive is missing prefs.json")
            )

        val prefsJson = runCatching {
            JSONObject(prefsRaw.toString(Charsets.UTF_8))
        }.getOrElse {
            return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = options,
                warnings = warnings,
                errors = listOf("prefs.json is invalid JSON")
            )
        }

        val effectiveOptions = resolveEffectiveOptions(manifest, options)

        val prefItems = prefsJson.optJSONArray("items") ?: JSONArray()
        val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (effectiveOptions.themes) {
            THEME_PREF_KEYS.forEach { editor.remove(it) }
        }
        if (effectiveOptions.profiles) {
            PROFILE_PREF_KEYS.forEach { editor.remove(it) }
        }

        var importedPrefCount = 0
        for (index in 0 until prefItems.length()) {
            cancelSignal?.throwIfCanceled()
            val item = prefItems.optJSONObject(index) ?: continue
            val key = item.optString("key").takeIf { it.isNotBlank() } ?: continue
            if (!shouldIncludePreference(key, effectiveOptions)) continue
            val type = item.optString("type")

            when (type) {
                "boolean" -> {
                    editor.putBoolean(key, item.optBoolean("value", false))
                    importedPrefCount++
                }

                "int" -> {
                    editor.putInt(key, item.optInt("value", 0))
                    importedPrefCount++
                }

                "long" -> {
                    val value = item.optLong("value", 0L)
                    editor.putLong(key, value)
                    importedPrefCount++
                }

                "float" -> {
                    val value = item.optDouble("value", 0.0).toFloat()
                    editor.putFloat(key, value)
                    importedPrefCount++
                }

                "string" -> {
                    editor.putString(key, item.optString("value"))
                    importedPrefCount++
                }

                "string_set" -> {
                    val valueArray = item.optJSONArray("value") ?: JSONArray()
                    val valueSet = linkedSetOf<String>()
                    for (setIndex in 0 until valueArray.length()) {
                        val value = valueArray.optString(setIndex)
                        if (value.isNotEmpty()) valueSet += value
                    }
                    editor.putStringSet(key, valueSet)
                    importedPrefCount++
                }

                else -> warnings += "Preference '$key' uses unsupported type '$type'; skipping."
            }
        }

        if (!editor.commit()) {
            return ImportResult(
                preferenceCount = 0,
                iconCount = 0,
                appliedOptions = effectiveOptions,
                warnings = warnings,
                errors = listOf("Failed to commit restored preferences")
            )
        }

        var importedIconCount = 0
        if (effectiveOptions.images) {
            PresetImageStorage.clearAllIcons(context)
            zipEntries.forEach { (entryName, bytes) ->
                cancelSignal?.throwIfCanceled()
                if (!entryName.startsWith(ICONS_DIR_PREFIX)) return@forEach

                val fileName = entryName.removePrefix(ICONS_DIR_PREFIX).trim()
                if (fileName.isBlank()) return@forEach

                if (PresetImageStorage.writeIconWithExactName(context, fileName, bytes)) {
                    importedIconCount++
                } else {
                    warnings += "Could not restore image '$fileName'."
                }
            }
        }

        if (effectiveOptions.profiles && !effectiveOptions.images) {
            warnings += "Profiles restored without images; presets that reference uploaded images may show missing artwork."
        }

        return ImportResult(
            preferenceCount = importedPrefCount,
            iconCount = importedIconCount,
            appliedOptions = effectiveOptions,
            warnings = warnings,
            errors = errors
        )
    }

    private fun serializePreferences(prefs: SharedPreferences, options: CategoryOptions): JSONObject {
        val items = JSONArray()

        prefs.all.toSortedMap().forEach { (key, value) ->
            if (!shouldIncludePreference(key, options)) {
                return@forEach
            }

            val item = JSONObject().apply { put("key", key) }
            when (value) {
                is Boolean -> {
                    item.put("type", "boolean")
                    item.put("value", value)
                }

                is Int -> {
                    item.put("type", "int")
                    item.put("value", value)
                }

                is Long -> {
                    item.put("type", "long")
                    item.put("value", value)
                }

                is Float -> {
                    item.put("type", "float")
                    item.put("value", value.toDouble())
                }

                is String -> {
                    item.put("type", "string")
                    item.put("value", value)
                }

                is Set<*> -> {
                    val setValues = value
                        .filterIsInstance<String>()
                        .sorted()
                    item.put("type", "string_set")
                    item.put("value", JSONArray(setValues))
                }

                else -> {
                    // Ignore unsupported preference types
                    return@forEach
                }
            }
            items.put(item)
        }

        return JSONObject().apply {
            put("items", items)
        }
    }

    private fun shouldIncludePreference(key: String, options: CategoryOptions): Boolean {
        return (options.themes && key in THEME_PREF_KEYS) ||
            (options.profiles && key in PROFILE_PREF_KEYS)
    }

    private fun resolveEffectiveOptions(
        manifest: JSONObject,
        requested: CategoryOptions
    ): CategoryOptions {
        val categories = manifest.optJSONObject("categories")
        if (categories == null) {
            return requested
        }

        return CategoryOptions(
            themes = requested.themes && categories.optBoolean("themes", true),
            profiles = requested.profiles && categories.optBoolean("profiles", true),
            images = requested.images && categories.optBoolean("images", true)
        )
    }
}
