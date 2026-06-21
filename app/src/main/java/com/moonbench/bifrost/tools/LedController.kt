package com.moonbench.bifrost.tools

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

class LedController {
    companion object {
        private const val TAG = "LedController"
    }

    private val pServerBinder: IBinder?
    private val lock = ReentrantLock()

    private var lastCommand: String? = null
    private var lastExecuteTime = 0L
    private val minExecuteInterval = 16L

    // Master brightness scale (0f..1f) multiplied into every RGB write. The LED
    // kernel ignores the 4th wire field, so "brightness" IS RGB magnitude —
    // scaling R/G/B here dims uniformly. Used for crossfades (see setMasterScale);
    // 1f in normal operation, so setLedColor is unaffected outside a fade.
    @Volatile private var masterScale: Float = 1f

    // Last unscaled colour + zone mask, so a fade can re-emit the current frame
    // even when the running animation writes slowly (or only once).
    private var lastR = 0
    private var lastG = 0
    private var lastB = 0
    private var lastLeftTop = false
    private var lastLeftBottom = false
    private var lastRightTop = false
    private var lastRightBottom = false

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PServerBinder", e)
            null
        }
    }

    fun setLedColor(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        val br = brightness.coerceIn(0, 255)
        if (pServerBinder == null) return

        lock.withLock {
            lastR = r; lastG = g; lastB = b
            lastLeftTop = leftTop; lastLeftBottom = leftBottom
            lastRightTop = rightTop; lastRightBottom = rightBottom
        }
        emit(r, g, b, br, leftTop, leftBottom, rightTop, rightBottom)
    }

    /** Apply masterScale to (r,g,b) and write the selected zones. */
    private fun emit(
        r: Int, g: Int, b: Int, br: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        val s = masterScale
        val sr = (r * s).roundToInt().coerceIn(0, 255)
        val sg = (g * s).roundToInt().coerceIn(0, 255)
        val sb = (b * s).roundToInt().coerceIn(0, 255)

        val commandBuilder = StringBuilder(220)
        if (leftTop) {
            commandBuilder.append("echo 1-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (leftBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (rightTop) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 1-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }
        if (rightBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }

        if (commandBuilder.isNotEmpty()) {
            executeCommandDirect(commandBuilder.toString())
        }
    }

    /**
     * Set the master brightness scale (0f..1f) and immediately re-emit the last
     * colour at the new scale. Driving this from 1→0→1 around an animation swap
     * produces a dip-to-black crossfade that masks the hard cut, independent of
     * how fast the underlying animation renders.
     */
    fun setMasterScale(scale: Float) {
        masterScale = scale.coerceIn(0f, 1f)
        val r: Int; val g: Int; val b: Int
        val lt: Boolean; val lb: Boolean; val rt: Boolean; val rb: Boolean
        lock.withLock {
            r = lastR; g = lastG; b = lastB
            lt = lastLeftTop; lb = lastLeftBottom; rt = lastRightTop; rb = lastRightBottom
        }
        if (lt || lb || rt || rb) emit(r, g, b, 255, lt, lb, rt, rb)
    }

    /**
     * Drop the re-emit baseline to black (keeping zone selection). Called at the
     * crossfade midpoint so the fade-in spins up from black instead of briefly
     * re-showing the outgoing colour before the incoming animation's first frame.
     */
    fun resetFadeBaseline() {
        lock.withLock { lastR = 0; lastG = 0; lastB = 0 }
    }

    fun setBrightness(brightness: Int) {
        val b = brightness.coerceIn(0, 255)
        val commands = listOf(
            "echo 1-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 1-0:0:0:$b > /sys/class/sn3112r/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112r/led/brightness"
        )
        val command = commands.joinToString(" && ")
        executeCommandDirect(command)
    }

    private fun executeCommandDirect(command: String) {
        lock.withLock {
            val now = System.currentTimeMillis()

            if (command == lastCommand && now - lastExecuteTime < minExecuteInterval) {
                return
            }

            lastCommand = command
            lastExecuteTime = now

            pServerBinder?.let { binder ->
                val data = Parcel.obtain()
                val reply = Parcel.obtain()

                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    Log.w(TAG, "LED transact failed", e)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }

    fun shutdown() {
    }
}