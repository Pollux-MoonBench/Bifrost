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
import java.util.UUID

data class PresetVisualSpec(
    val builtInIcon: PresetIcon,
    val customEmoji: String? = null,
    val customImageFileName: String? = null
)

object PresetVisuals {

    fun fromPreset(preset: LedPreset): PresetVisualSpec {
        return PresetVisualSpec(
            builtInIcon = preset.icon,
            customEmoji = preset.customEmoji,
            customImageFileName = preset.customImageFileName
        )
    }

    fun fromBuiltIn(icon: PresetIcon): PresetVisualSpec {
        return PresetVisualSpec(builtInIcon = icon)
    }

    fun labelForPreset(preset: LedPreset): String {
        return when {
            !preset.customImageFileName.isNullOrBlank() -> "Uploaded image"
            !preset.customEmoji.isNullOrBlank() -> "Custom emoji"
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

    fun copyPickedImage(context: Context, sourceUri: Uri): String? {
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(sourceUri))
            ?.lowercase()
            ?.ifBlank { null }
            ?: "img"
        val fileName = "preset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
        val targetFile = File(resolveDirectory(context), fileName)

        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        return fileName
    }

    fun deleteIfExists(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        resolveFile(context, fileName).takeIf { it.exists() }?.delete()
    }

    fun loadBitmap(context: Context, fileName: String, targetSizePx: Int): Bitmap? {
        val file = resolveFile(context, fileName)
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
        return File(resolveDirectory(context), fileName)
    }
}