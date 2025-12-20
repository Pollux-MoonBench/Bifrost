package com.moonbench.bifrost.tools

import android.graphics.Color
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.sqrt

data class ScreenColors(
    val leftColor: Int = Color.BLACK,
    val rightColor: Int = Color.BLACK
)

class ScreenAnalyzer(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    var performanceProfile: PerformanceProfile = PerformanceProfile.HIGH,
    var useCustomSampling: Boolean = false,
    private val onColorsAnalyzed: (ScreenColors) -> Unit
) {
    private var captureWidth = 2
    private var captureHeight = 1
    private var lastProcessedTime = 0L
    private var lastEmittedColors: ScreenColors? = null

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var isRunning: Boolean = false

    fun start() {
        if (isRunning) return
        isRunning = true

        captureWidth = if (useCustomSampling) 32 else 2
        captureHeight = 1

        handlerThread = HandlerThread("ScreenCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }
        mediaProjection.registerCallback(projectionCallback!!, handler)

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            if (!isRunning) {
                val img = reader.acquireLatestImage()
                img?.close()
                return@setOnImageAvailableListener
            }

            val now = System.currentTimeMillis()
            val image = reader.acquireLatestImage()

            if (image != null) {
                if (performanceProfile == PerformanceProfile.RAGNAROK || now - lastProcessedTime >= performanceProfile.intervalMs) {
                    processImage(image)
                    lastProcessedTime = now
                }
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            captureWidth,
            captureHeight,
            displayMetrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        virtualDisplay?.release()
        imageReader?.close()
        handlerThread?.quitSafely()

        projectionCallback?.let {
            mediaProjection.unregisterCallback(it)
        }

        virtualDisplay = null
        imageReader = null
        handlerThread = null
        handler = null
        projectionCallback = null
        lastEmittedColors = null
    }

    private fun processImage(image: Image) {
        if (!isRunning) return

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val colors = if (useCustomSampling) {
            val midPoint = captureWidth / 2
            val leftColor = averageRegionWeighted(buffer, 0, midPoint - 1, rowStride, pixelStride)
            val rightColor = averageRegionWeighted(buffer, midPoint, captureWidth - 1, rowStride, pixelStride)
            ScreenColors(leftColor = leftColor, rightColor = rightColor)
        } else {
            val leftColor = getPixelColor(buffer, 0, 0, rowStride, pixelStride)
            val rightColor = getPixelColor(buffer, 1, 0, rowStride, pixelStride)
            ScreenColors(leftColor = leftColor, rightColor = rightColor)
        }

        if (colors != lastEmittedColors) {
            lastEmittedColors = colors
            onColorsAnalyzed(colors)
        }
    }

    private fun getPixelColor(buffer: ByteBuffer, x: Int, y: Int, rowStride: Int, pixelStride: Int): Int {
        val offset = y * rowStride + x * pixelStride
        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        return Color.rgb(r, g, b)
    }

    private fun averageRegionWeighted(buffer: ByteBuffer, startX: Int, endX: Int, rowStride: Int, pixelStride: Int): Int {
        var rAcc = 0.0
        var gAcc = 0.0
        var bAcc = 0.0
        var totalWeight = 0.0

        for (x in startX..endX) {
            val offset = x * pixelStride
            val r = buffer.get(offset).toInt() and 0xFF
            val g = buffer.get(offset + 1).toInt() and 0xFF
            val b = buffer.get(offset + 2).toInt() and 0xFF

            val weight = calculatePixelWeight(r, g, b)

            rAcc += r * weight
            gAcc += g * weight
            bAcc += b * weight
            totalWeight += weight
        }

        if (totalWeight == 0.0) return Color.BLACK

        val rAvg = (rAcc / totalWeight).toInt().coerceIn(0, 255)
        val gAvg = (gAcc / totalWeight).toInt().coerceIn(0, 255)
        val bAvg = (bAcc / totalWeight).toInt().coerceIn(0, 255)

        return Color.rgb(rAvg, gAvg, bAvg)
    }

    private fun calculatePixelWeight(r: Int, g: Int, b: Int): Double {
        val rNorm = r / 255.0
        val gNorm = g / 255.0
        val bNorm = b / 255.0

        val brightness = 0.299 * rNorm + 0.587 * gNorm + 0.114 * bNorm

        val max = maxOf(rNorm, gNorm, bNorm)
        val min = minOf(rNorm, gNorm, bNorm)
        val saturation = if (max == 0.0) 0.0 else (max - min) / max

        val avg = (rNorm + gNorm + bNorm) / 3.0
        val colorfulness = sqrt((rNorm - avg).pow(2) + (gNorm - avg).pow(2) + (bNorm - avg).pow(2))

        val brightnessWeight = 1.0 - (1.0 / (1.0 + (brightness * 10.0).pow(2)))
        val saturationWeight = saturation.pow(0.5) * 2.5
        val colorfulnessWeight = colorfulness * 3.0

        val weight = (brightnessWeight * 0.2 + saturationWeight * 0.5 + colorfulnessWeight * 0.3).coerceAtLeast(0.01)

        return weight
    }
}