package com.moonbench.bifrost

import android.content.Context
import android.net.Uri
import org.json.JSONObject

object ThemeArchiveTransfer {

    private const val THEME_SCHEMA = "bifrost_theme_bundle"
    private const val THEME_VERSION = 1

    data class ExportResult(
        val themeId: String,
        val warnings: List<String>
    )

    data class ImportResult(
        val themeId: String?,
        val coloredLogoEnabled: Boolean,
        val warnings: List<String>,
        val errors: List<String>
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        themeId: String,
        coloredLogoEnabled: Boolean
    ): ExportResult {
        val payload = JSONObject().apply {
            put("schema", THEME_SCHEMA)
            put("version", THEME_VERSION)
            put("themeId", themeId)
            put("coloredLogoEnabled", coloredLogoEnabled)
        }

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.writer(Charsets.UTF_8).use { writer ->
                writer.write(payload.toString(2))
            }
        } ?: return ExportResult(
            themeId = themeId,
            warnings = listOf("Unable to write selected file.")
        )

        return ExportResult(
            themeId = themeId,
            warnings = emptyList()
        )
    }

    fun importFromUri(context: Context, uri: Uri): ImportResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val rawJson = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } ?: return ImportResult(
            themeId = null,
            coloredLogoEnabled = true,
            warnings = emptyList(),
            errors = listOf("Unable to read selected file.")
        )

        val payload = runCatching { JSONObject(rawJson) }.getOrElse {
            return ImportResult(
                themeId = null,
                coloredLogoEnabled = true,
                warnings = emptyList(),
                errors = listOf("Theme file is invalid JSON")
            )
        }

        if (payload.optString("schema") != THEME_SCHEMA) {
            errors += "Unknown theme bundle schema."
        }

        val version = payload.optInt("version", -1)
        if (version < 1) {
            errors += "Unsupported theme bundle version: $version"
        } else if (version > THEME_VERSION) {
            warnings += "Theme bundle version $version is newer than supported version $THEME_VERSION. Attempting compatible import."
        }

        val themeId = payload.optString("themeId").takeIf { it.isNotBlank() }
        if (themeId == null) {
            errors += "Theme bundle does not specify a theme."
        }

        return ImportResult(
            themeId = themeId,
            coloredLogoEnabled = payload.optBoolean("coloredLogoEnabled", true),
            warnings = warnings,
            errors = errors
        )
    }
}