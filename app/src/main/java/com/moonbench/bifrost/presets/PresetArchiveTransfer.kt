package com.moonbench.bifrost

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.PerformanceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PresetArchiveTransfer {

    private const val ARCHIVE_SCHEMA = "bifrost_preset_bundle"
    private const val ARCHIVE_VERSION = 1
    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val ICONS_DIR_PREFIX = "icons/"

    data class ExportResult(
        val presetCount: Int,
        val iconCount: Int,
        val warnings: List<String>
    )

    data class ImportResult(
        val presets: List<LedPreset>,
        val mappings: Map<String, String>,
        val warnings: List<String>,
        val errors: List<String>
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        presets: List<LedPreset>,
        mappings: Map<String, String>,
        cancelSignal: CancellationSignal? = null
    ): ExportResult {
        val warnings = mutableListOf<String>()
        var exportedIconCount = 0

        val manifestPresets = JSONArray()
        presets.forEach { preset ->
            val presetJson = JSONObject()
            presetJson.put("name", preset.name)
            presetJson.put("animationType", preset.animationType.name)
            presetJson.put("performanceProfile", preset.performanceProfile.name)
            presetJson.put("color", preset.color)
            presetJson.put("rightColor", preset.rightColor)
            presetJson.put("brightness", preset.brightness)
            presetJson.put("speed", preset.speed.toDouble())
            presetJson.put("smoothness", preset.smoothness.toDouble())
            presetJson.put("sensitivity", preset.sensitivity.toDouble())
            presetJson.put("saturationBoost", preset.saturationBoost.toDouble())
            presetJson.put("useCustomSampling", preset.useCustomSampling)
            presetJson.put("useSingleColor", preset.useSingleColor)
            presetJson.put("breatheWhenCharging", preset.breatheWhenCharging)
            presetJson.put("indicateChargingSpeed", preset.indicateChargingSpeed)
            presetJson.put("flashWhenReady", preset.flashWhenReady)
            preset.batteryLowColorOverride?.let { presetJson.put("batteryLowColorOverride", it) }
            preset.batteryMidColorOverride?.let { presetJson.put("batteryMidColorOverride", it) }
            preset.batteryHighColorOverride?.let { presetJson.put("batteryHighColorOverride", it) }
            preset.cpuCoolColorOverride?.let { presetJson.put("cpuCoolColorOverride", it) }
            preset.cpuWarmColorOverride?.let { presetJson.put("cpuWarmColorOverride", it) }
            preset.cpuHotColorOverride?.let { presetJson.put("cpuHotColorOverride", it) }
            presetJson.put("isAppProfileDefault", preset.isAppProfileDefault)
            presetJson.put("ragnarokAccepted", preset.ragnarokAccepted)
            presetJson.put("icon", preset.icon.name)
            preset.customEmoji?.let { presetJson.put("customEmoji", it) }
            preset.customImageFileName?.let { presetJson.put("customImageFileName", it) }
            preset.appIconPackageName?.let { presetJson.put("appIconPackageName", it) }
            manifestPresets.put(presetJson)
        }

        val mappingsJson = JSONObject()
        mappings.forEach { (packageName, presetName) ->
            mappingsJson.put(packageName, presetName)
        }

        val manifest = JSONObject().apply {
            put("schema", ARCHIVE_SCHEMA)
            put("version", ARCHIVE_VERSION)
            put("presets", manifestPresets)
            put("appProfileMappings", mappingsJson)
        }

        val seenIconNames = linkedSetOf<String>()
        // Check for cancellation before starting heavy IO
        cancelSignal?.throwIfCanceled()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                cancelSignal?.throwIfCanceled()
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                presets.forEach { preset ->
                    cancelSignal?.throwIfCanceled()
                    val iconName = preset.customImageFileName?.trim().orEmpty()
                    if (iconName.isEmpty() || !seenIconNames.add(iconName)) return@forEach

                    val input = PresetImageStorage.openIconInputStream(context, iconName)
                    if (input == null) {
                        warnings += "Image not found for preset icon '$iconName'; skipping."
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
        }

        return ExportResult(
            presetCount = presets.size,
            iconCount = exportedIconCount,
            warnings = warnings
        )
    }

    fun importFromUri(context: Context, uri: Uri, cancelSignal: CancellationSignal? = null): ImportResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val zipEntries = mutableMapOf<String, ByteArray>()
        // Check cancellation before reading
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
            presets = emptyList(),
            mappings = emptyMap(),
            warnings = emptyList(),
            errors = listOf("Unable to read selected file.")
        )

        val manifestRaw = zipEntries[MANIFEST_ENTRY_NAME]
            ?: return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = listOf("Archive is missing manifest.json")
            )

        val manifest = runCatching {
            JSONObject(manifestRaw.toString(Charsets.UTF_8))
        }.getOrElse {
            return ImportResult(
                presets = emptyList(),
                mappings = emptyMap(),
                warnings = emptyList(),
                errors = listOf("manifest.json is invalid JSON")
            )
        }

        if (manifest.optString("schema") != ARCHIVE_SCHEMA) {
            errors += "Unknown preset bundle schema."
        }

        val version = manifest.optInt("version", -1)
        if (version < 1) {
            errors += "Unsupported preset bundle version: $version"
        } else if (version > ARCHIVE_VERSION) {
            warnings += "Bundle version $version is newer than supported version $ARCHIVE_VERSION. Attempting compatible import."
        }

        val presetArray = manifest.optJSONArray("presets") ?: JSONArray()
        val importedPresets = mutableListOf<LedPreset>()

        for (index in 0 until presetArray.length()) {
            cancelSignal?.throwIfCanceled()
            val obj = presetArray.optJSONObject(index) ?: continue
            importedPresets += parsePreset(context, obj, index, zipEntries, warnings, cancelSignal)
        }

        val mappings = parseMappings(context, manifest.optJSONObject("appProfileMappings"), warnings, cancelSignal)

        return ImportResult(
            presets = importedPresets,
            mappings = mappings,
            warnings = warnings,
            errors = errors
        )
    }

    private fun parsePreset(
        context: Context,
        obj: JSONObject,
        index: Int,
        zipEntries: Map<String, ByteArray>,
        warnings: MutableList<String>,
        cancelSignal: CancellationSignal? = null
    ): LedPreset {
        cancelSignal?.throwIfCanceled()
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Imported Preset ${index + 1}"

        val animationTypeName = obj.optString("animationType", LedAnimationType.STATIC.name)
        val animationType = runCatching { LedAnimationType.valueOf(animationTypeName) }
            .onFailure { warnings += "Preset '$name': unknown animation '$animationTypeName', using STATIC." }
            .getOrDefault(LedAnimationType.STATIC)

        val profileName = obj.optString("performanceProfile", PerformanceProfile.HIGH.name)
        val profile = runCatching { PerformanceProfile.valueOf(profileName) }
            .onFailure { warnings += "Preset '$name': unknown profile '$profileName', using HIGH." }
            .getOrDefault(PerformanceProfile.HIGH)

        val iconName = obj.optString("icon", PresetIcon.defaultFor(animationType).name)
        val icon = PresetIcon.fromStoredName(iconName)

        val customEmoji = obj.optString("customEmoji").takeIf { it.isNotBlank() }

        val importedIconName = obj.optString("customImageFileName").takeIf { it.isNotBlank() }
        val customImageFileName = if (importedIconName != null) {
            cancelSignal?.throwIfCanceled()
            val archiveEntryName = "$ICONS_DIR_PREFIX$importedIconName"
            val iconBytes = zipEntries[archiveEntryName]
            if (iconBytes == null) {
                warnings += "Preset '$name': missing icon file '$importedIconName'."
                null
            } else {
                PresetImageStorage.importIconFromBytes(context, importedIconName, iconBytes)
                    ?: run {
                        warnings += "Preset '$name': could not import icon '$importedIconName'."
                        null
                    }
            }
        } else {
            null
        }

        val requestedAppIconPackage = obj.optString("appIconPackageName").takeIf { it.isNotBlank() }
        val appIconPackageName = requestedAppIconPackage?.takeIf {
            cancelSignal?.throwIfCanceled(); isPackageInstalled(context.packageManager, it)
        } ?: run {
            if (!requestedAppIconPackage.isNullOrBlank()) {
                warnings += "Preset '$name': assigned app icon package '$requestedAppIconPackage' is not installed, skipping."
            }
            null
        }

        val color = obj.optInt("color", -1)

        return LedPreset(
            name = name,
            animationType = animationType,
            performanceProfile = profile,
            color = color,
            rightColor = obj.optInt("rightColor", color),
            brightness = obj.optInt("brightness", 255).coerceIn(0, 255),
            speed = obj.optDouble("speed", 0.5).toFloat().coerceIn(0f, 1f),
            smoothness = obj.optDouble("smoothness", 0.5).toFloat().coerceIn(0f, 1f),
            sensitivity = obj.optDouble("sensitivity", 0.5).toFloat().coerceIn(0f, 1f),
            saturationBoost = obj.optDouble("saturationBoost", 0.0).toFloat().coerceIn(0f, 1f),
            useCustomSampling = obj.optBoolean("useCustomSampling", false),
            useSingleColor = obj.optBoolean("useSingleColor", false),
            breatheWhenCharging = obj.optBoolean("breatheWhenCharging", false),
            indicateChargingSpeed = obj.optBoolean("indicateChargingSpeed", false),
            flashWhenReady = obj.optBoolean("flashWhenReady", false),
            batteryLowColorOverride = obj.optInt("batteryLowColorOverride").takeIf { obj.has("batteryLowColorOverride") },
            batteryMidColorOverride = obj.optInt("batteryMidColorOverride").takeIf { obj.has("batteryMidColorOverride") },
            batteryHighColorOverride = obj.optInt("batteryHighColorOverride").takeIf { obj.has("batteryHighColorOverride") },
            cpuCoolColorOverride = obj.optInt("cpuCoolColorOverride").takeIf { obj.has("cpuCoolColorOverride") },
            cpuWarmColorOverride = obj.optInt("cpuWarmColorOverride").takeIf { obj.has("cpuWarmColorOverride") },
            cpuHotColorOverride = obj.optInt("cpuHotColorOverride").takeIf { obj.has("cpuHotColorOverride") },
            isAppProfileDefault = obj.optBoolean("isAppProfileDefault", false),
            ragnarokAccepted = obj.optBoolean("ragnarokAccepted", false),
            icon = icon,
            customEmoji = customEmoji,
            customImageFileName = customImageFileName,
            appIconPackageName = appIconPackageName
        )
    }

    private fun parseMappings(
        context: Context,
        mappingsObj: JSONObject?,
        warnings: MutableList<String>,
        cancelSignal: CancellationSignal? = null
    ): Map<String, String> {
        if (mappingsObj == null) return emptyMap()

        val mappings = linkedMapOf<String, String>()
        val iterator = mappingsObj.keys()
        while (iterator.hasNext()) {
            cancelSignal?.throwIfCanceled()
            val packageName = iterator.next()
            val presetName = mappingsObj.optString(packageName).takeIf { it.isNotBlank() }
            if (presetName == null) continue

            if (!isPackageInstalled(context.packageManager, packageName)) {
                warnings += "Mapping ignored: package '$packageName' is not installed."
                continue
            }
            mappings[packageName] = presetName
        }
        return mappings
    }

    private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}

