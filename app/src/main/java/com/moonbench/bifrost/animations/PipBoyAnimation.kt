package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.moonbench.bifrost.tools.LedController
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Fallout 4 Pip-Boy CRT effect.
 *
 * Reproduces the brightness behaviour of Bethesda's PipboyPostEffect shader
 * (the green phosphor screen) natively on the stick LEDs, so they breathe and
 * stutter in the same character as the in-game screen — without any per-frame
 * IPC from the companion app (which provably disturbs the Unity renderer).
 *
 * Formula (extracted from Assembly-CSharp PipboyPostEffect.Update):
 *
 *   fBrightness = 1.0 + perlin(t * 0.5) * 0.5            // slow phosphor pulse
 *   if flickering:
 *       fBrightness += perlin(t * 22) - 0.1             // fast CRT shimmer
 *   flicker scheduling: idle Random(5..15)s, burst Random(0.1..0.6)s
 *
 * Two faithful adaptations for the LEDs:
 *   1. The pulse (1.0..1.5 on screen, a multiplier) is normalised to a
 *      0.5..1.0 LED envelope so the breath never goes fully dark.
 *   2. The flicker is applied as a DOWNWARD stutter only (per the device
 *      owner's directive: "make flicker step down rather than up"). On screen
 *      the fast perlin term can brighten; on the sticks a momentary dimming
 *      reads as the authentic CRT stutter and never overshoots the baseline.
 *
 * The pulse is deterministic in t, so feeding a shared phase origin
 * (setPhaseOrigin) aligns the sticks with the screen. The flicker is random
 * by design and is reproduced statistically, not phase-locked.
 */
class PipBoyAnimation(
    ledController: LedController,
    initialColor: Int,
    initialRightColor: Int = initialColor
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.PIPBOY
    override val needsColorSelection: Boolean = true

    private companion object {
        const val TICK_MS = 30L
        const val PULSE_RATE = 0.5            // fPulseRate
        const val PULSE_INTENSITY = 0.5       // fPulseIntensity
        const val FLICKER_FREQUENCY = 22.0    // fFlickerFrequency (shimmer rate while flickering)
        const val FLICKER_DEPTH = 0.55        // max downward dim during a stutter

        // Burst — FED from the screen's TriggerBurst (onBurst → setBurst), a brief
        // brightness pop (no spatial component). The plugin runs the envelope from
        // the fed anchor; BURST_DURATION_MS ≈ the screen's fBurstState fade time.
        const val BURST_DURATION_MS = 150.0   // flash length
        const val BURST_GAIN = 0.5            // how deep the burst DIMS the LEDs

        // Pip-Boy phosphor green — the default when no real colour has been
        // supplied yet (e.g. before the in-game HUD EffectColor arrives).
        const val PIPBOY_GREEN = 0xFF00FF00.toInt()
        const val DARK_SUM_THRESHOLD = 24     // r+g+b below this ⇒ treat as unset

        // Transient nav reactions, triggered out-of-band via ACTION_PULSE
        // (Strip-Boy menu navigation). These modulate brightness on top of the
        // running animation WITHOUT a restart, so they never fight the
        // heartbeat re-anchor or the external-API rate limiter.
        const val PULSE_MS = 180L
        const val PULSE_DIM = 0.4             // menu-nav DIP floor (dims to this, then settles)
        const val STATIC_MS = 380L
        const val STATIC_MUL = 3.0            // scramble brightness — the ONE effect that brightens

        // CPU-affinity pin (see pinRenderThreadToBigCores). The render thread is
        // light (~8ms CPU/s) but latency-sensitive at 33Hz. Under heavy load the
        // energy-aware scheduler packs it onto the little cores (measured 92-93%
        // busy under a Box64 game) where it starves — 3.4× wait/run, ~29
        // involuntary preempts/s = visible LED stutter — while the big cores sit
        // ~50% idle and the prime core ~98%. Pinning to the non-little cores fixes
        // it. Placement, not priority: it already outranks the nice-0 game at -4.
        const val TAG = "PipBoyAnimation"
        const val MAX_CORES = 16              // upper bound for the core-probe loop
        const val AFFINITY_TIMEOUT_MS = 500L  // taskset must return within this
    }

    // Dedicated render thread: the ~33Hz tick and its blocking sysfs/binder LED
    // writes run here, NOT on the main looper, so they don't jitter when the main
    // thread is busy (e.g. Fallout 4 running). THREAD_PRIORITY_DISPLAY (-4) is
    // Android's render-tier nice value — promptly scheduled under contention,
    // without the starvation risk of a real-time priority. Created in start(),
    // torn down in stop(). NB: a core cannot be reserved exclusively without root
    // (this is a `user` build); a high-priority dedicated thread is the ceiling.
    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    // running / originMs / target{Color,RightColor,Brightness} are written on the
    // service/broadcast path and read on the render looper, so they MUST be
    // @Volatile (the external*/pulse*/static* fields below already were — the tick
    // was always meant to run off the main thread; these were simply missed).
    @Volatile private var running = false

    @Volatile private var targetColor: Int = greenIfUnset(initialColor)
    private var currentColor: Int = greenIfUnset(initialColor)
    @Volatile private var targetRightColor: Int = greenIfUnset(initialRightColor)
    private var currentRightColor: Int = greenIfUnset(initialRightColor)
    @Volatile private var targetBrightness: Int = 255

    // Last RGB actually written to each stick (packed; 0 = nothing yet) so an
    // unchanged frame is skipped. Sentinel is 0, not -1: Color.rgb() always sets
    // alpha 0xFF so it never returns 0, but 0xFFFFFFFF (-1) IS Color.rgb(255,255,
    // 255) — a -1 sentinel would wrongly skip a first white frame. Render-thread
    // only ⇒ no @Volatile needed.
    private var lastEmitLeft = 0
    private var lastEmitRight = 0

    /** Default to Pip-Boy green when the supplied colour is black/near-black. */
    private fun greenIfUnset(c: Int): Int =
        if (Color.red(c) + Color.green(c) + Color.blue(c) < DARK_SUM_THRESHOLD) PIPBOY_GREEN else c

    // Phase origin. Defaults to the moment start() is called; can be re-aligned
    // to the screen's clock via setPhaseOrigin so the pulse tracks in phase.
    @Volatile private var originMs: Long = 0L

    // Flicker + burst are FED from the screen (event-driven), not replicated: the
    // flicker schedule depends on game triggers + multiple instances drawing the
    // shared RNG, so it can't be reproduced from a seed (proven on-device — the
    // RNG sequences diverge). @Volatile: written from the service/broadcast path,
    // read on the LED looper.
    @Volatile private var externalFlickering = false
    @Volatile private var externalBurstWallMs = 0L

    // Transient nav reactions (set by triggerPulse/triggerStatic, read each
    // tick). elapsedRealtime deadlines; volatile since the triggers fire from
    // the service/broadcast path while the runnable reads on the LED looper.
    @Volatile private var pulseUntilMs = 0L
    @Volatile private var staticUntilMs = 0L
    private var brightnessMul = 1.0   // per-tick brightness scale (1.0 = resting)

    /** Burst flash envelope from the fed anchor: decays 1→0 over BURST_DURATION_MS
     *  on the shared clock, so a delayed feed still yields the correct level. */
    private fun burstAmount(nowMs: Long): Double {
        val anchor = externalBurstWallMs
        if (anchor <= 0L) return 0.0
        val age = (nowMs - anchor).toDouble()
        if (age < 0.0 || age >= BURST_DURATION_MS) return 0.0
        return 1.0 - age / BURST_DURATION_MS
    }

    /** Scale [color]'s channels by f∈[0,1], returned packed (alpha forced 0xFF so
     *  two equal colours compare equal for the per-frame skip). The single dual
     *  write + skip live in the tick; this is just the per-channel scale. */
    private fun scaleColor(color: Int, f: Double): Int {
        val r = (Color.red(color) * f).roundToInt().coerceIn(0, 255)
        val g = (Color.green(color) * f).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(color) * f).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    /** Brief brightness pop for a menu-item switch. Modulates the running
     *  animation in place — no restart, so it can't be clobbered by the
     *  heartbeat re-anchor and never trips the rate limiter. */
    fun triggerPulse() { pulseUntilMs = SystemClock.elapsedRealtime() + PULSE_MS }

    /** TV-static scramble for the dramatic vertical-hold "channel swap" roll. */
    fun triggerStatic() { staticUntilMs = SystemClock.elapsedRealtime() + STATIC_MS }

    /** Feed the screen's current flicker state (on/off). The tick dims the LEDs
     *  with a shimmer while true. Set out-of-band from the service/broadcast path. */
    fun setFlickering(flickering: Boolean) { externalFlickering = flickering }

    /** Feed the screen's latest burst flash (elapsedRealtime ms, shared clock).
     *  The tick runs a brief flash envelope from it — see burstAmount. */
    fun setBurst(burstWallMs: Long) { externalBurstWallMs = burstWallMs }

    override fun setTargetColor(color: Int) { targetColor = greenIfUnset(color) }
    override fun setTargetRightColor(color: Int) { targetRightColor = greenIfUnset(color) }
    override fun setTargetBrightness(brightness: Int) {
        targetBrightness = brightness.coerceIn(0, 255)
    }

    /**
     * Align the effect clock to a shared origin (e.g. the companion app's
     * fTime at the instant it issued the command), so the deterministic pulse
     * runs in phase with the on-screen pulse. afSecondsSinceTheirOrigin is how
     * many seconds of their clock have already elapsed.
     */
    fun setPhaseOrigin(afSecondsSinceTheirOrigin: Double) {
        // Flicker/vscan/burst are fed live, so there's nothing to replay — just
        // align the clock so the deterministic pulse runs in phase.
        originMs = SystemClock.elapsedRealtime() -
            (afSecondsSinceTheirOrigin * 1000.0).toLong()
    }

    /**
     * Re-align the clock to the screen's fTime (called on heartbeats) so the
     * pulse stays in phase. Flicker/vscan/burst are fed, not simulated, so this
     * only re-anchors the clock — no state to preserve or replay. Handles a
     * screen restart (fTime jumps back to ~0) the same way: re-anchor.
     */
    fun reanchor(afSecondsSinceTheirOrigin: Double) {
        if (afSecondsSinceTheirOrigin <= 0.0) return
        originMs = SystemClock.elapsedRealtime() -
            (afSecondsSinceTheirOrigin * 1000.0).toLong()
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            // Smoothly chase the target colour, same idiom as BreathAnimation.
            val colorFactor = 0.2f
            currentColor = lerpColor(currentColor, targetColor, colorFactor)
            currentRightColor = lerpColor(currentRightColor, targetRightColor, colorFactor)

            val t = (SystemClock.elapsedRealtime() - originMs) / 1000.0

            // Effect state is FED from the screen (event-driven), no seed
            // replication. nowMs is reused by the transient-reaction block below.
            val nowMs = SystemClock.elapsedRealtime()
            val flickering = externalFlickering
            val burst = burstAmount(nowMs)

            // Base brightness factor: rest at full, DROP to deliver each effect
            // (per Sat — every effect dims rather than brightens; the static
            // scramble below is the sole exception). The breath dips the LEDs,
            // the flicker stutters them down, the burst is a brief dark flash.
            var baseFactor = 1.0 - perlin1d(t * PULSE_RATE) * PULSE_INTENSITY   // 1.0 → 0.5 breath dip
            if (flickering) {
                val shimmer = perlin1d(t * FLICKER_FREQUENCY)
                baseFactor *= (1.0 - shimmer * FLICKER_DEPTH)   // downward stutter
            }
            if (burst > 0.0) baseFactor -= burst * BURST_GAIN   // downward flash (dip)

            // Transient nav reactions (menu pulse / channel-swap static),
            // applied on top of the base — no restart, set out-of-band by
            // triggerPulse()/triggerStatic().
            when {
                nowMs < staticUntilMs -> {
                    // The 5%-on-nav channel-swap scramble — the ONE effect that
                    // brightens (a deliberate dramatic flash). Sat's exception.
                    baseFactor = Math.random()        // bright random scramble
                    brightnessMul = STATIC_MUL
                }
                nowMs < pulseUntilMs -> {
                    val k = (pulseUntilMs - nowMs).toDouble() / PULSE_MS   // 1→0
                    brightnessMul = PULSE_DIM + (1.0 - k) * (1.0 - PULSE_DIM)   // dip → settle
                }
                else -> brightnessMul = 1.0
            }

            // Both sticks (left = currentColor, right = currentRightColor) in ONE
            // transact, and skipped entirely when neither changed since last frame
            // — both cut load on the little-core-bound pservice writer (the
            // residual stutter source under load). Flicker/burst/scramble change
            // the RGB ⇒ they still write every frame. (Vscan bar was dropped: a 2D
            // screen effect doesn't map to point LEDs.)
            val f = (baseFactor * (targetBrightness / 255.0) * brightnessMul).coerceIn(0.0, 1.0)
            val leftRgb = scaleColor(currentColor, f)
            val rightRgb = scaleColor(currentRightColor, f)
            if (leftRgb != lastEmitLeft || rightRgb != lastEmitRight) {
                lastEmitLeft = leftRgb
                lastEmitRight = rightRgb
                ledController.setLedColorDual(
                    Color.red(leftRgb), Color.green(leftRgb), Color.blue(leftRgb),
                    Color.red(rightRgb), Color.green(rightRgb), Color.blue(rightRgb),
                    leftTop = true, leftBottom = true, rightTop = true, rightBottom = true
                )
            }

            renderHandler?.postDelayed(this, adjustedAnimationDelay(TICK_MS, targetBrightness))
        }
    }

    override fun start() {
        if (running) return
        running = true
        // setPhaseOrigin may have already aligned the clock; default to now.
        if (originMs == 0L) originMs = SystemClock.elapsedRealtime()

        // Spin the dedicated render looper at display priority, then kick the tick.
        val thread = HandlerThread("BifrostPipBoyRender", Process.THREAD_PRIORITY_DISPLAY)
        thread.start()
        renderThread = thread
        renderHandler = Handler(thread.looper).also { it.post(runnable) }
        // Escape the energy-aware scheduler's little-core packing (else it starves
        // under load). threadId is the kernel tid, valid once the looper is ready.
        pinRenderThreadToBigCores(thread.threadId)
    }

    override fun stop() {
        running = false
        renderHandler?.removeCallbacksAndMessages(null)
        renderThread?.quitSafely()
        runCatching { renderThread?.join(250) }   // bounded: never blocks forever
        renderThread = null
        renderHandler = null
        // The render thread has fully stopped, so this black write is the last
        // LED op (no concurrent writer) — the sticks reliably go dark.
        ledController.setLedColor(
            0, 0, 0,
            leftTop = true, leftBottom = true,
            rightTop = true, rightBottom = true
        )
    }

    /**
     * Pin the render thread to the big/prime CPU cluster so the energy-aware
     * scheduler can't park it on the little cores, where it starves under load
     * (see companion note). Best-effort via toybox `taskset` — Android exposes no
     * sched_setaffinity API, and same-uid so no CAP_SYS_NICE is needed. Any
     * failure is non-fatal: the LED runs unpinned, exactly as before this change.
     */
    private fun pinRenderThreadToBigCores(tid: Int) {
        if (tid <= 0) return
        val mask = bigCoreMask()
        if (mask == 0L) return
        val hexMask = java.lang.Long.toHexString(mask)
        runCatching {
            val proc = ProcessBuilder("/system/bin/taskset", "-p", hexMask, tid.toString())
                .redirectErrorStream(true)
                .start()
            if (!proc.waitFor(AFFINITY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) proc.destroy()
        }.onFailure { Log.w(TAG, "render-thread affinity pin failed; running unpinned", it) }
    }

    /**
     * Affinity mask of the non-little cores: every core whose max frequency is
     * above the slowest cluster's. Returns 0 when topology is uniform or can't be
     * read (caller then skips pinning). Loop bounded by MAX_CORES.
     */
    private fun bigCoreMask(): Long {
        val freqs = ArrayList<Long>(MAX_CORES)
        var core = 0
        while (core < MAX_CORES) {
            val f = runCatching {
                File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
                    .readText().trim().toLong()
            }.getOrNull() ?: break
            freqs.add(f)
            core++
        }
        if (freqs.size < 2) return 0L
        val slowest = freqs.minOrNull() ?: return 0L
        var mask = 0L
        for (i in freqs.indices) {
            if (freqs[i] > slowest) mask = mask or (1L shl i)
        }
        return mask
    }

    /**
     * Smooth 1D value noise in [0,1], smoothstep-interpolated between integer
     * lattice points. Not Unity's exact PerlinNoise, but the same statistical
     * character (smooth, mean ~0.5) — which is all the eye needs for the LEDs
     * to read as "the same kind of flicker."
     */
    private fun perlin1d(x: Double): Double {
        val xi = floor(x).toInt()
        val xf = x - floor(x)
        val u = xf * xf * (3.0 - 2.0 * xf)     // smoothstep
        val a = lattice(xi)
        val b = lattice(xi + 1)
        return a + u * (b - a)
    }

    /** Deterministic hash of an integer lattice point to [0,1]. */
    private fun lattice(i: Int): Double {
        var h = i * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff).toDouble() / 0x7fffffff.toDouble()
    }
}
