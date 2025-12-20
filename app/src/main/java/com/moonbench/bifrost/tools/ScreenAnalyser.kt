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

private const val DEFAULT_CAPTURE_WIDTH = 2
private const val DEFAULT_CAPTURE_HEIGHT = 1
private const val CUSTOM_SAMPLING_WIDTH = 32
private const val IMAGE_READER_MAX_IMAGES = 2

private const val SATURATION_BOOST_MULTIPLIER = 2.5f
private const val SATURATION_BOOST_BASE = 1.0f

private const val BRIGHTNESS_FACTOR = 10.0
private const val BRIGHTNESS_POWER = 2.0
private const val BRIGHTNESS_WEIGHT_RATIO = 0.15

private const val SATURATION_POWER = 0.3
private const val SATURATION_MULTIPLIER = 4.0
private const val SATURATION_WEIGHT_RATIO = 0.55

private const val COLORFULNESS_MULTIPLIER = 5.0
private const val COLORFULNESS_WEIGHT_RATIO = 0.3

private const val MIN_WEIGHT = 0.01

private const val RGB_NORMALIZE = 255.0
private const val RGB_MAX = 255

private const val BRIGHTNESS_RED_COEFF = 0.299
private const val BRIGHTNESS_GREEN_COEFF = 0.587
private const val BRIGHTNESS_BLUE_COEFF = 0.114

private const val HUE_CYCLE = 6f
private const val HUE_STEP = 60f

data class ScreenColors(
    val leftColor: Int = Color.BLACK,
    val rightColor: Int = Color.BLACK
)

class ScreenAnalyzer(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    var performanceProfile: PerformanceProfile = PerformanceProfile.HIGH,
    var useCustomSampling: Boolean = false,
    var saturationBoost: Float = 0.0f,
    var topPixelPercentage: Float = 0.3f,
    private val onColorsAnalyzed: (ScreenColors) -> Unit
) {
    private var captureWidth = DEFAULT_CAPTURE_WIDTH
    private var captureHeight = DEFAULT_CAPTURE_HEIGHT
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

        if (useCustomSampling) {
            captureWidth = CUSTOM_SAMPLING_WIDTH
            val aspectRatio = displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat()
            captureHeight = (captureWidth * aspectRatio).toInt().coerceAtLeast(DEFAULT_CAPTURE_HEIGHT)
        } else {
            captureWidth = DEFAULT_CAPTURE_WIDTH
            captureHeight = DEFAULT_CAPTURE_HEIGHT
        }

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
            IMAGE_READER_MAX_IMAGES
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
            val leftColor = averageRegionTopWeighted(buffer, 0, midPoint - 1, 0, captureHeight - 1, rowStride, pixelStride)
            val rightColor = averageRegionTopWeighted(buffer, midPoint, captureWidth - 1, 0, captureHeight - 1, rowStride, pixelStride)
            ScreenColors(leftColor = leftColor, rightColor = rightColor)
        } else {
            val leftColor = getPixelColor(buffer, 0, 0, rowStride, pixelStride)
            val rightColor = getPixelColor(buffer, 1, 0, rowStride, pixelStride)
            ScreenColors(leftColor = leftColor, rightColor = rightColor)
        }

        val boostedColors = ScreenColors(
            leftColor = applySaturationBoost(colors.leftColor),
            rightColor = applySaturationBoost(colors.rightColor)
        )

        if (boostedColors != lastEmittedColors) {
            lastEmittedColors = boostedColors
            onColorsAnalyzed(boostedColors)
        }
    }

    private fun getPixelColor(buffer: ByteBuffer, x: Int, y: Int, rowStride: Int, pixelStride: Int): Int {
        val offset = y * rowStride + x * pixelStride
        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        return Color.rgb(r, g, b)
    }

    private data class WeightedPixel(val r: Int, val g: Int, val b: Int, val weight: Double)

    private fun averageRegionTopWeighted(
        buffer: ByteBuffer,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
        rowStride: Int,
        pixelStride: Int
    ): Int {
        val pixels = mutableListOf<WeightedPixel>()

        for (y in startY..endY) {
            for (x in startX..endX) {
                val offset = y * rowStride + x * pixelStride
                val r = buffer.get(offset).toInt() and 0xFF
                val g = buffer.get(offset + 1).toInt() and 0xFF
                val b = buffer.get(offset + 2).toInt() and 0xFF

                val weight = calculatePixelWeight(r, g, b)
                pixels.add(WeightedPixel(r, g, b, weight))
            }
        }

        if (pixels.isEmpty()) return Color.BLACK

        pixels.sortByDescending { it.weight }
        val topCount = (pixels.size * topPixelPercentage).toInt().coerceAtLeast(1)
        val topPixels = pixels.take(topCount)

        var rAcc = 0.0
        var gAcc = 0.0
        var bAcc = 0.0
        var totalWeight = 0.0

        for (pixel in topPixels) {
            rAcc += pixel.r * pixel.weight
            gAcc += pixel.g * pixel.weight
            bAcc += pixel.b * pixel.weight
            totalWeight += pixel.weight
        }

        if (totalWeight == 0.0) return Color.BLACK

        val rAvg = (rAcc / totalWeight).toInt().coerceIn(0, RGB_MAX)
        val gAvg = (gAcc / totalWeight).toInt().coerceIn(0, RGB_MAX)
        val bAvg = (bAcc / totalWeight).toInt().coerceIn(0, RGB_MAX)

        return Color.rgb(rAvg, gAvg, bAvg)
    }

    private fun calculatePixelWeight(r: Int, g: Int, b: Int): Double {
        val rNorm = r / RGB_NORMALIZE
        val gNorm = g / RGB_NORMALIZE
        val bNorm = b / RGB_NORMALIZE

        val brightness = BRIGHTNESS_RED_COEFF * rNorm + BRIGHTNESS_GREEN_COEFF * gNorm + BRIGHTNESS_BLUE_COEFF * bNorm

        val max = maxOf(rNorm, gNorm, bNorm)
        val min = minOf(rNorm, gNorm, bNorm)
        val saturation = if (max == 0.0) 0.0 else (max - min) / max

        val avg = (rNorm + gNorm + bNorm) / 3.0
        val colorfulness = sqrt((rNorm - avg).pow(2) + (gNorm - avg).pow(2) + (bNorm - avg).pow(2))

        val brightnessWeight = 1.0 - (1.0 / (1.0 + (brightness * BRIGHTNESS_FACTOR).pow(BRIGHTNESS_POWER)))
        val saturationWeight = saturation.pow(SATURATION_POWER) * SATURATION_MULTIPLIER
        val colorfulnessWeight = colorfulness * COLORFULNESS_MULTIPLIER

        val weight = (brightnessWeight * BRIGHTNESS_WEIGHT_RATIO + saturationWeight * SATURATION_WEIGHT_RATIO + colorfulnessWeight * COLORFULNESS_WEIGHT_RATIO).coerceAtLeast(MIN_WEIGHT)

        return weight
    }

    private fun applySaturationBoost(color: Int): Int {
        val mappedBoost = SATURATION_BOOST_BASE + (saturationBoost * SATURATION_BOOST_MULTIPLIER)

        if (mappedBoost == SATURATION_BOOST_BASE) return color

        val r = Color.red(color) / RGB_NORMALIZE.toFloat()
        val g = Color.green(color) / RGB_NORMALIZE.toFloat()
        val b = Color.blue(color) / RGB_NORMALIZE.toFloat()

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val v = max
        val s = if (max == 0f) 0f else delta / max

        val sBoosted = (s * mappedBoost).coerceIn(0f, 1f)

        val h = when {
            delta == 0f -> 0f
            max == r -> HUE_STEP * (((g - b) / delta) % HUE_CYCLE)
            max == g -> HUE_STEP * (((b - r) / delta) + 2f)
            else -> HUE_STEP * (((r - g) / delta) + 4f)
        }

        val c = v * sBoosted
        val x = c * (1f - kotlin.math.abs((h / HUE_STEP) % 2f - 1f))
        val m = v - c

        val (rPrime, gPrime, bPrime) = when {
            h < HUE_STEP -> Triple(c, x, 0f)
            h < HUE_STEP * 2 -> Triple(x, c, 0f)
            h < HUE_STEP * 3 -> Triple(0f, c, x)
            h < HUE_STEP * 4 -> Triple(0f, x, c)
            h < HUE_STEP * 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val rFinal = ((rPrime + m) * RGB_NORMALIZE).toInt().coerceIn(0, RGB_MAX)
        val gFinal = ((gPrime + m) * RGB_NORMALIZE).toInt().coerceIn(0, RGB_MAX)
        val bFinal = ((bPrime + m) * RGB_NORMALIZE).toInt().coerceIn(0, RGB_MAX)

        return Color.rgb(rFinal, gFinal, bFinal)
    }
}