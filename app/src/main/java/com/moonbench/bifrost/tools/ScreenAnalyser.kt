package com.moonbench.bifrost.tools

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import com.moonbench.bifrost.services.BifrostAccessibilityService
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.math.pow
import kotlin.math.sqrt

private const val DEFAULT_CAPTURE_WIDTH = 2
private const val DEFAULT_CAPTURE_HEIGHT = 1
private const val SINGLE_COLOR_CAPTURE_SIZE = 1
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

private const val SCREENSHOT_MIN_INTERVAL_MS = 100L

data class ScreenColors(
    val leftColor: Int = Color.BLACK,
    val rightColor: Int = Color.BLACK
)

class ScreenAnalyzer(
    private val mediaProjection: MediaProjection? = null,
    private val displayMetrics: DisplayMetrics,
    var performanceProfile: PerformanceProfile = PerformanceProfile.HIGH,
    var useCustomSampling: Boolean = false,
    var useSingleColor: Boolean = false,
    var saturationBoost: Float = 0.0f,
    initialTopPixelPercentage: Float = 0.3f,
    private val displayId: Int = Display.DEFAULT_DISPLAY,
    private val onColorsAnalyzed: (ScreenColors) -> Unit
) {
    var topPixelPercentage: Float = initialTopPixelPercentage
        set(value) {
            field = value.coerceIn(0.05f, 1f)
        }

    private var captureWidth = DEFAULT_CAPTURE_WIDTH
    private var captureHeight = DEFAULT_CAPTURE_HEIGHT
    private var lastProcessedTime = 0L
    private var lastEmittedColors: ScreenColors? = null

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var projectionCallback: MediaProjection.Callback? = null
    @Volatile private var isRunning: Boolean = false

    // Accessibility path only
    private var captureInFlight: Boolean = false
    private var screenshotCapabilityBlocked: Boolean = false
    private var blockedUntilElapsedRealtime: Long = 0L
    private var screenshotFailureCount: Int = 0

    fun start() {
        if (isRunning) return
        isRunning = true

        if (useSingleColor) {
            captureWidth = SINGLE_COLOR_CAPTURE_SIZE
            captureHeight = SINGLE_COLOR_CAPTURE_SIZE
        } else if (useCustomSampling) {
            captureWidth = CUSTOM_SAMPLING_WIDTH
            val aspectRatio = displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat()
            captureHeight = (captureWidth * aspectRatio).toInt()
                .coerceAtLeast(DEFAULT_CAPTURE_HEIGHT)
                .coerceAtMost(CUSTOM_SAMPLING_WIDTH)
        } else {
            captureWidth = DEFAULT_CAPTURE_WIDTH
            captureHeight = DEFAULT_CAPTURE_HEIGHT
        }

        handlerThread = HandlerThread("ScreenCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)

        if (mediaProjection != null) {
            startVirtualDisplayCapture()
        } else {
            screenshotCapabilityBlocked = false
            blockedUntilElapsedRealtime = 0L
            screenshotFailureCount = 0
            scheduleNextCapture(0L)
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        // Detach the frame listener before releasing the reader so an in-flight
        // capture callback on the handler thread can't touch a closed reader.
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        projectionCallback = null
        captureInFlight = false
        lastEmittedColors = null
    }

    // ── VirtualDisplay path (MediaProjection, ~60 fps) ────────────────────────

    private fun startVirtualDisplayCapture() {
        val ir = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        ir.setOnImageAvailableListener({ reader ->
            // stop() can close the reader / stop the projection on another thread
            // between callbacks; acquireLatestImage() or buffer access then throws
            // on this capture thread. Swallow during teardown instead of crashing.
            try {
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (!isRunning) return@setOnImageAvailableListener
                    val now = SystemClock.elapsedRealtime()
                    val minInterval = if (performanceProfile == PerformanceProfile.RAGNAROK) 16L
                                      else performanceProfile.intervalMs.coerceAtLeast(16L)
                    if (now - lastProcessedTime < minInterval) return@setOnImageAvailableListener
                    lastProcessedTime = now
                    processImage(image)
                } finally {
                    image.close()
                }
            } catch (t: Throwable) {
                if (isRunning) android.util.Log.w("ScreenAnalyser", "capture frame dropped: ${t.message}")
            }
        }, handler)
        imageReader = ir
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }
        mediaProjection!!.registerCallback(projectionCallback!!, handler)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "AmbilightCapture",
            captureWidth, captureHeight,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir.surface, null, null
        )
    }

    private fun processImage(image: Image) {
        if (!isRunning) return

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val colors = if (useSingleColor) {
            val singleColor = if (useCustomSampling) {
                averageRegionTopWeighted(buffer, 0, captureWidth - 1, 0, captureHeight - 1, rowStride, pixelStride)
            } else {
                getPixelColor(buffer, 0, 0, rowStride, pixelStride)
            }
            ScreenColors(leftColor = singleColor, rightColor = singleColor)
        } else if (useCustomSampling) {
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

    // ── Accessibility path (takeScreenshot, ~10 fps max) ──────────────────────

    private fun scheduleNextCapture(delayMs: Long) {
        handler?.postDelayed({ captureFrame() }, delayMs)
    }

    private fun captureFrame() {
        if (!isRunning || captureInFlight) return

        val now = SystemClock.elapsedRealtime()
        val minInterval = performanceProfile.intervalMs.coerceAtLeast(SCREENSHOT_MIN_INTERVAL_MS)
        if (now - lastProcessedTime < minInterval) {
            scheduleNextCapture(minInterval - (now - lastProcessedTime))
            return
        }

        val service = BifrostAccessibilityService.instance ?: run {
            scheduleNextCapture(500L); return
        }
        if (!BifrostAccessibilityService.isEnabled(service)) {
            scheduleNextCapture(2000L); return
        }
        if (screenshotCapabilityBlocked) {
            if (now < blockedUntilElapsedRealtime) {
                scheduleNextCapture((blockedUntilElapsedRealtime - now).coerceAtLeast(250L)); return
            }
            screenshotCapabilityBlocked = false
        }

        captureInFlight = true
        try {
            service.takeScreenshot(
                displayId,
                Executor { cmd -> handler?.post(cmd) },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hwBitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val scaledHw = hwBitmap?.let { Bitmap.createScaledBitmap(it, captureWidth, captureHeight, true) }
                        hwBitmap?.recycle()
                        screenshot.hardwareBuffer.close()

                        val bitmap = when {
                            scaledHw == null -> null
                            scaledHw.config == Bitmap.Config.HARDWARE -> {
                                val soft = scaledHw.copy(Bitmap.Config.ARGB_8888, false)
                                scaledHw.recycle()
                                soft
                            }
                            else -> scaledHw
                        }

                        screenshotFailureCount = 0
                        lastProcessedTime = SystemClock.elapsedRealtime()
                        captureInFlight = false

                        if (bitmap != null && isRunning) {
                            processBitmap(bitmap)
                            bitmap.recycle()
                        }
                        if (isRunning) scheduleNextCapture(0L)
                    }

                    override fun onFailure(errorCode: Int) {
                        captureInFlight = false
                        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                            scheduleNextCapture(SCREENSHOT_MIN_INTERVAL_MS); return
                        }
                        screenshotFailureCount = (screenshotFailureCount + 1).coerceAtMost(10)
                        val retryDelay = (250L * screenshotFailureCount).coerceAtMost(2000L)
                        screenshotCapabilityBlocked = true
                        blockedUntilElapsedRealtime = SystemClock.elapsedRealtime() + retryDelay
                        scheduleNextCapture(retryDelay)
                    }
                }
            )
        } catch (_: SecurityException) {
            captureInFlight = false; scheduleNextCapture(2000L)
        } catch (_: Throwable) {
            captureInFlight = false; scheduleNextCapture(500L)
        }
    }

    private fun processBitmap(bitmap: Bitmap) {
        if (!isRunning) return
        val colors = if (useSingleColor) {
            val c = if (useCustomSampling)
                averageRegionTopWeighted(bitmap, 0, captureWidth - 1, 0, captureHeight - 1)
            else
                getPixelColor(bitmap, 0, 0)
            ScreenColors(c, c)
        } else if (useCustomSampling) {
            val mid = captureWidth / 2
            ScreenColors(
                leftColor  = averageRegionTopWeighted(bitmap, 0, mid - 1, 0, captureHeight - 1),
                rightColor = averageRegionTopWeighted(bitmap, mid, captureWidth - 1, 0, captureHeight - 1)
            )
        } else {
            ScreenColors(leftColor = getPixelColor(bitmap, 0, 0), rightColor = getPixelColor(bitmap, 1, 0))
        }
        val boostedColors = ScreenColors(
            leftColor  = applySaturationBoost(colors.leftColor),
            rightColor = applySaturationBoost(colors.rightColor)
        )
        if (boostedColors != lastEmittedColors) {
            lastEmittedColors = boostedColors
            onColorsAnalyzed(boostedColors)
        }
    }

    private fun getPixelColor(bitmap: Bitmap, x: Int, y: Int): Int {
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return Color.BLACK
        return bitmap.getPixel(x, y)
    }

    private fun averageRegionTopWeighted(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): Int {
        val pixelCount = ((endX - startX + 1) * (endY - startY + 1)).coerceAtLeast(1)
        val topCount = (pixelCount * topPixelPercentage).toInt().coerceAtLeast(1)
        val topWeights = DoubleArray(topCount)
        val topR = IntArray(topCount); val topG = IntArray(topCount); val topB = IntArray(topCount)
        var selectedCount = 0
        for (y in startY..endY) {
            for (x in startX..endX) {
                if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
                val weight = calculatePixelWeight(r, g, b)
                if (selectedCount < topCount) {
                    topWeights[selectedCount] = weight; topR[selectedCount] = r
                    topG[selectedCount] = g; topB[selectedCount] = b; selectedCount++; continue
                }
                var minIdx = 0; var minW = topWeights[0]
                for (i in 1 until topCount) { if (topWeights[i] < minW) { minW = topWeights[i]; minIdx = i } }
                if (weight > minW) { topWeights[minIdx] = weight; topR[minIdx] = r; topG[minIdx] = g; topB[minIdx] = b }
            }
        }
        if (selectedCount == 0) return Color.BLACK
        var rA = 0.0; var gA = 0.0; var bA = 0.0; var tw = 0.0
        for (i in 0 until selectedCount) { rA += topR[i]*topWeights[i]; gA += topG[i]*topWeights[i]; bA += topB[i]*topWeights[i]; tw += topWeights[i] }
        if (tw == 0.0) return Color.BLACK
        return Color.rgb((rA/tw).toInt().coerceIn(0, RGB_MAX), (gA/tw).toInt().coerceIn(0, RGB_MAX), (bA/tw).toInt().coerceIn(0, RGB_MAX))
    }

    private fun getPixelColor(buffer: ByteBuffer, x: Int, y: Int, rowStride: Int, pixelStride: Int): Int {
        val offset = y * rowStride + x * pixelStride
        if (offset < 0 || offset + 2 >= buffer.limit()) {
            return Color.BLACK
        }
        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        return Color.rgb(r, g, b)
    }

    private fun averageRegionTopWeighted(
        buffer: ByteBuffer,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
        rowStride: Int,
        pixelStride: Int
    ): Int {
        val pixelCount = ((endX - startX + 1) * (endY - startY + 1)).coerceAtLeast(1)
        val topCount = (pixelCount * topPixelPercentage).toInt().coerceAtLeast(1)

        val topWeights = DoubleArray(topCount)
        val topR = IntArray(topCount)
        val topG = IntArray(topCount)
        val topB = IntArray(topCount)
        var selectedCount = 0

        for (y in startY..endY) {
            for (x in startX..endX) {
                val offset = y * rowStride + x * pixelStride
                if (offset < 0 || offset + 2 >= buffer.limit()) continue

                val r = buffer.get(offset).toInt() and 0xFF
                val g = buffer.get(offset + 1).toInt() and 0xFF
                val b = buffer.get(offset + 2).toInt() and 0xFF

                val weight = calculatePixelWeight(r, g, b)
                if (selectedCount < topCount) {
                    topWeights[selectedCount] = weight
                    topR[selectedCount] = r
                    topG[selectedCount] = g
                    topB[selectedCount] = b
                    selectedCount++
                    continue
                }

                var minIndex = 0
                var minWeight = topWeights[0]
                var i = 1
                while (i < topCount) {
                    if (topWeights[i] < minWeight) {
                        minWeight = topWeights[i]
                        minIndex = i
                    }
                    i++
                }

                if (weight > minWeight) {
                    topWeights[minIndex] = weight
                    topR[minIndex] = r
                    topG[minIndex] = g
                    topB[minIndex] = b
                }
            }
        }

        if (selectedCount == 0) return Color.BLACK

        var rAcc = 0.0
        var gAcc = 0.0
        var bAcc = 0.0
        var totalWeight = 0.0

        var i = 0
        while (i < selectedCount) {
            val weight = topWeights[i]
            rAcc += topR[i] * weight
            gAcc += topG[i] * weight
            bAcc += topB[i] * weight
            totalWeight += weight
            i++
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