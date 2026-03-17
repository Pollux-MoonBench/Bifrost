package com.moonbench.bifrost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class PresetVisualSpec(
    val builtInIcon: PresetIcon,
    val customEmoji: String? = null,
    val customImageFileName: String? = null,
    val appIconPackageName: String? = null
)

object PresetVisuals {

    fun fromPreset(preset: LedPreset): PresetVisualSpec {
        return PresetVisualSpec(
            builtInIcon = preset.icon,
            customEmoji = preset.customEmoji,
            customImageFileName = preset.customImageFileName,
            appIconPackageName = preset.appIconPackageName
        )
    }

    fun fromBuiltIn(icon: PresetIcon): PresetVisualSpec {
        return PresetVisualSpec(builtInIcon = icon)
    }

    fun labelForPreset(preset: LedPreset): String {
        return when {
            !preset.customImageFileName.isNullOrBlank() -> "Uploaded image"
            !preset.customEmoji.isNullOrBlank() -> "Custom emoji"
            !preset.appIconPackageName.isNullOrBlank() -> "Assigned app icon"
            else -> preset.icon.label
        }
    }

    fun bind(
        context: Context,
        spec: PresetVisualSpec,
        iconView: ImageView,
        emojiView: TextView,
        targetSizePx: Int
    ) {
        val loadedImage = spec.customImageFileName?.takeIf { it.isNotBlank() }?.let {
            PresetImageStorage.loadBitmap(context, it, targetSizePx)
        }

        if (loadedImage != null) {
            emojiView.text = null
            emojiView.visibility = android.view.View.GONE
            iconView.visibility = android.view.View.VISIBLE
            iconView.scaleType = ImageView.ScaleType.CENTER_CROP
            iconView.setImageBitmap(loadedImage)
            return
        }

        val emojiValue = spec.customEmoji?.takeIf { it.isNotBlank() } ?: spec.builtInIcon.emoji
        if (!emojiValue.isNullOrBlank()) {
            iconView.setImageDrawable(null)
            iconView.visibility = android.view.View.GONE
            emojiView.text = emojiValue
            emojiView.visibility = android.view.View.VISIBLE
            return
        }

        val appIconDrawable = spec.appIconPackageName
            ?.takeIf { it.isNotBlank() }
            ?.let { packageName ->
                runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            }
        if (appIconDrawable != null) {
            emojiView.text = null
            emojiView.visibility = android.view.View.GONE
            iconView.visibility = android.view.View.VISIBLE
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            iconView.setImageDrawable(appIconDrawable)
            return
        }

        emojiView.text = null
        emojiView.visibility = android.view.View.GONE
        iconView.visibility = android.view.View.VISIBLE
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        val drawable = spec.builtInIcon.drawableRes
            ?.let { AppCompatResources.getDrawable(context, it) }
            ?.mutate()
        drawable?.setTint(ContextCompat.getColor(context, R.color.bifrost_icon))
        iconView.setImageDrawable(drawable)
    }
}

object PresetImageStorage {
    private const val DIRECTORY_NAME = "preset_icons"
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
    private val SAFE_FILE_NAME_REGEX = Regex("^[A-Za-z0-9._-]{1,96}$")

    fun copyPickedImage(context: Context, sourceUri: Uri): String? {
        val mimeType = context.contentResolver.getType(sourceUri)?.lowercase() ?: return null
        if (!mimeType.startsWith("image/")) return null

        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase()
            ?.ifBlank { null }
            ?: "img"

        val safeExtension = extension.filter { it.isLetterOrDigit() }
            .ifBlank { "img" }
            .take(8)

        val fileName = "preset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
        val targetFile = resolveFile(context, fileName.replaceAfterLast('.', safeExtension))

        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        var totalBytes = 0L
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    totalBytes += read
                    if (totalBytes > MAX_IMAGE_BYTES) {
                        output.flush()
                        output.close()
                        targetFile.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        }

        return targetFile.name
    }

    fun deleteIfExists(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        resolveFile(context, fileName).takeIf { it.exists() }?.delete()
    }

    fun openIconInputStream(context: Context, fileName: String): InputStream? {
        val file = runCatching { resolveFile(context, fileName) }.getOrNull() ?: return null
        if (!file.exists()) return null
        return runCatching { file.inputStream() }.getOrNull()
    }

    fun importIconFromBytes(context: Context, sourceName: String, bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null

        val safeExtension = sourceName
            .substringAfterLast('.', "img")
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .ifBlank { "img" }
            .take(8)

        val generatedName =
            "preset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$safeExtension"
        val targetFile = resolveFile(context, generatedName)

        return runCatching {
            FileOutputStream(targetFile).use { it.write(bytes) }
            targetFile.name
        }.getOrNull()
    }

    fun loadBitmap(context: Context, fileName: String, targetSizePx: Int): Bitmap? {
        val file = runCatching { resolveFile(context, fileName) }.getOrNull() ?: return null
        if (!file.exists()) return null

        val normalizedTargetSize = targetSizePx.coerceAtLeast(48)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        val bitmapOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateInSampleSize(
                width = boundsOptions.outWidth,
                height = boundsOptions.outHeight,
                targetSizePx = normalizedTargetSize
            )
        }
        return BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetSizePx: Int): Int {
        if (width <= 0 || height <= 0) return 1

        var sampleSize = 1
        while ((width / sampleSize) > targetSizePx * 2 || (height / sampleSize) > targetSizePx * 2) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun resolveDirectory(context: Context): File {
        return File(context.filesDir, DIRECTORY_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun resolveFile(context: Context, fileName: String): File {
        val sanitized = fileName.trim()
        require(sanitized.matches(SAFE_FILE_NAME_REGEX))

        val dir = resolveDirectory(context)
        val file = File(dir, sanitized)
        val dirPath = dir.canonicalPath
        val filePath = file.canonicalPath
        require(filePath.startsWith("$dirPath/"))
        return file
    }
}