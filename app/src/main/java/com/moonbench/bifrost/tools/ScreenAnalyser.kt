package com.moonbench.bifrost.tools

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import com.moonbench.bifrost.services.BifrostAccessibilityService
import java.util.concurrent.Executor
import kotlin.math.pow
import kotlin.math.sqrt

private const val DEFAULT_CAPTURE_WIDTH = 2
private const val DEFAULT_CAPTURE_HEIGHT = 1
private const val SINGLE_COLOR_CAPTURE_SIZE = 1
private const val CUSTOM_SAMPLING_WIDTH = 32

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
    private val displayId: Int,
    private val displayMetrics: DisplayMetrics,
    var performanceProfile: PerformanceProfile = PerformanceProfile.HIGH,
    var useCustomSampling: Boolean = false,
    var useSingleColor: Boolean = false,
    var saturationBoost: Float = 0.0f,
    initialTopPixelPercentage: Float = 0.3f,
    private val mediaProjection: MediaProjection? = null,
    private val onColorsAnalyzed: (ScreenColors) -> Unit
) {
    var topPixelPercentage: Float = initialTopPixelPercentage
        set(value) { field = value.coerceIn(0.05f, 1f) }

    private var captureWidth = DEFAULT_CAPTURE_WIDTH
    private var captureHeight = DEFAULT_CAPTURE_HEIGHT
    private var lastProcessedTime = 0L
    private var lastEmittedColors: ScreenColors? = null

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var isRunning: Boolean = false

    // Accessibility path only
    private var captureInFlight: Boolean = false
    private var screenshotCapabilityBlocked: Boolean = false
    private var blockedUntilElapsedRealtime: Long = 0L
    private var screenshotFailureCount: Int = 0

    // VirtualDisplay path only
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        captureWidth = when {
            useSingleColor -> SINGLE_COLOR_CAPTURE_SIZE.also { captureHeight = SINGLE_COLOR_CAPTURE_SIZE }
            useCustomSampling -> {
                val ratio = displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat()
                captureHeight = (CUSTOM_SAMPLING_WIDTH * ratio).toInt()
                    .coerceAtLeast(DEFAULT_CAPTURE_HEIGHT)
                    .coerceAtMost(CUSTOM_SAMPLING_WIDTH)
                CUSTOM_SAMPLING_WIDTH
            }
            else -> DEFAULT_CAPTURE_WIDTH.also { captureHeight = DEFAULT_CAPTURE_HEIGHT }
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
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        captureInFlight = false
        lastEmittedColors = null
    }

    // ── VirtualDisplay path (MediaProjection, ~60 fps) ────────────────────────

    private fun startVirtualDisplayCapture() {
        val ir = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        ir.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (!isRunning) return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                val minInterval = if (performanceProfile == PerformanceProfile.RAGNAROK) 16L
                                  else performanceProfile.intervalMs.coerceAtLeast(16L)
                if (now - lastProcessedTime < minInterval) return@setOnImageAvailableListener
                lastProcessedTime = now
                processImageBuffer(image)
            } finally {
                image.close()
            }
        }, handler)
        imageReader = ir
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "AmbilightCapture",
            captureWidth, captureHeight,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir.surface, null, null
        )
    }

    private fun processImageBuffer(image: android.media.Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val ps = plane.pixelStride
        val rs = plane.rowStride

        val colors = when {
            useSingleColor -> {
                val avg = if (useCustomSampling)
                    averageBufferRegion(buffer, ps, rs, 0, captureWidth - 1, 0, captureHeight - 1)
                else
                    bufferPixelAt(buffer, 0)
                val c = applySaturationBoost(avg)
                ScreenColors(c, c)
            }
            useCustomSampling -> {
                val mid = captureWidth / 2
                ScreenColors(
                    leftColor  = applySaturationBoost(averageBufferRegion(buffer, ps, rs, 0, mid - 1, 0, captureHeight - 1)),
                    rightColor = applySaturationBoost(averageBufferRegion(buffer, ps, rs, mid, captureWidth - 1, 0, captureHeight - 1))
                )
            }
            else -> ScreenColors(
                leftColor  = applySaturationBoost(bufferPixelAt(buffer, 0)),
                rightColor = applySaturationBoost(bufferPixelAt(buffer, ps))
            )
        }

        if (colors != lastEmittedColors) {
            lastEmittedColors = colors
            onColorsAnalyzed(colors)
        }
    }

    private fun bufferPixelAt(buffer: java.nio.ByteBuffer, offset: Int): Int {
        val r = buffer.get(offset).toInt() and 0xFF
        val g = buffer.get(offset + 1).toInt() and 0xFF
        val b = buffer.get(offset + 2).toInt() and 0xFF
        return Color.rgb(r, g, b)
    }

    private fun averageBufferRegion(
        buffer: java.nio.ByteBuffer, ps: Int, rs: Int,
        startX: Int, endX: Int, startY: Int, endY: Int
    ): Int {
        var rAcc = 0; var gAcc = 0; var bAcc = 0; var count = 0
        for (y in startY..endY) {
            for (x in startX..endX) {
                val off = y * rs + x * ps
                rAcc += buffer.get(off).toInt() and 0xFF
                gAcc += buffer.get(off + 1).toInt() and 0xFF
                bAcc += buffer.get(off + 2).toInt() and 0xFF
                count++
            }
        }
        if (count == 0) return Color.BLACK
        return Color.rgb(rAcc / count, gAcc / count, bAcc / count)
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

    private fun calculatePixelWeight(r: Int, g: Int, b: Int): Double {
        val rN = r / RGB_NORMALIZE; val gN = g / RGB_NORMALIZE; val bN = b / RGB_NORMALIZE
        val brightness = BRIGHTNESS_RED_COEFF * rN + BRIGHTNESS_GREEN_COEFF * gN + BRIGHTNESS_BLUE_COEFF * bN
        val max = maxOf(rN, gN, bN); val min = minOf(rN, gN, bN)
        val saturation = if (max == 0.0) 0.0 else (max - min) / max
        val avg = (rN + gN + bN) / 3.0
        val colorfulness = sqrt((rN - avg).pow(2) + (gN - avg).pow(2) + (bN - avg).pow(2))
        val bw = 1.0 - (1.0 / (1.0 + (brightness * BRIGHTNESS_FACTOR).pow(BRIGHTNESS_POWER)))
        val sw = saturation.pow(SATURATION_POWER) * SATURATION_MULTIPLIER
        val cw = colorfulness * COLORFULNESS_MULTIPLIER
        return (bw * BRIGHTNESS_WEIGHT_RATIO + sw * SATURATION_WEIGHT_RATIO + cw * COLORFULNESS_WEIGHT_RATIO).coerceAtLeast(MIN_WEIGHT)
    }

    private fun applySaturationBoost(color: Int): Int {
        val boost = SATURATION_BOOST_BASE + (saturationBoost * SATURATION_BOOST_MULTIPLIER)
        if (boost == SATURATION_BOOST_BASE) return color
        val r = Color.red(color) / RGB_NORMALIZE.toFloat()
        val g = Color.green(color) / RGB_NORMALIZE.toFloat()
        val b = Color.blue(color) / RGB_NORMALIZE.toFloat()
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
        val v = max; val s = if (max == 0f) 0f else delta / max
        val sBoosted = (s * boost).coerceIn(0f, 1f)
        val h = when {
            delta == 0f -> 0f
            max == r -> HUE_STEP * (((g - b) / delta) % HUE_CYCLE)
            max == g -> HUE_STEP * (((b - r) / delta) + 2f)
            else      -> HUE_STEP * (((r - g) / delta) + 4f)
        }
        val c = v * sBoosted
        val x = c * (1f - kotlin.math.abs((h / HUE_STEP) % 2f - 1f))
        val m = v - c
        val (rP, gP, bP) = when {
            h < HUE_STEP     -> Triple(c, x, 0f)
            h < HUE_STEP * 2 -> Triple(x, c, 0f)
            h < HUE_STEP * 3 -> Triple(0f, c, x)
            h < HUE_STEP * 4 -> Triple(0f, x, c)
            h < HUE_STEP * 5 -> Triple(x, 0f, c)
            else              -> Triple(c, 0f, x)
        }
        return Color.rgb(
            ((rP + m) * RGB_NORMALIZE).toInt().coerceIn(0, RGB_MAX),
            ((gP + m) * RGB_NORMALIZE).toInt().coerceIn(0, RGB_MAX),
            ((bP + m) * RGB_NORMALIZE).toInt().coerceIn(0, RGB_MAX)
        )
    }
}
