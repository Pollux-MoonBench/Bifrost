package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.moonbench.bifrost.tools.LedController
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
        const val FLICKER_FREQUENCY = 22.0    // fFlickerFrequency
        const val FLICKER_MIN_DELAY = 5.0     // fFlickerMinDelay
        const val FLICKER_MAX_DELAY = 15.0    // fFlickerMaxDelay
        const val FLICKER_MIN_DURATION = 0.1  // fFlickerMinDuration
        const val FLICKER_MAX_DURATION = 0.6  // fFlickerMaxDuration
        const val FLICKER_DEPTH = 0.55        // max downward dim during a stutter
        const val FLICKER_INITIAL_DELAY = 5.0 // fFlickerDelay field default on screen

        // Burst — the screen's TriggerBurst(0.25, 2) fired on a 15% roll after
        // each flicker. Pure brightness (no spatial component) → a clean LED
        // pop. The 15% roll is already in our seeded draw sequence.
        const val BURST_CHANCE = 0.15
        const val BURST_TRIGGER = 0.25        // initial fBurstState
        const val BURST_FADE = 2.0            // fBurstFadeRate, units/sec
        const val BURST_GAIN = 2.0            // how hard the pop brightens the LEDs

        // Shared flicker RNG — MUST stay bit-identical with the screen side
        // (Strip-Boy's FlickerSeed Cecil patch). Same LCG, same seed, same
        // draw order ⇒ the sticks reproduce the screen's exact flicker
        // sequence with no runtime signalling. Numerical-Recipes LCG.
        const val FLICKER_SEED = 0x50B0FFu    // "PIPBOY" seed; mirrored screen-side
        const val LCG_MUL = 1664525u
        const val LCG_ADD = 1013904223u

        // Vertical-scan (the CRT rolling bar). Independent seeded RNG so its
        // draws never perturb the flicker sequence. Mirrors the screen's
        // PipboyPostEffect vscan: a bar sweeps fVScanState -0.9→1.5 over
        // ~1.2s, scheduled Random(1..5)s apart. Rendered spatially across the
        // four stick LEDs: as the bar descends it lights leftTop → leftBottom
        // → (mid-screen gap) → rightTop → rightBottom. Left stick = screen's
        // top third, right stick = bottom third; top LED leads bottom per stick.
        const val VSCAN_SEED = 0xC0FFEEu      // mirrored screen-side
        const val VSCAN_RATE = 2.0            // fVScanRate
        const val VSCAN_INITIAL_DELAY = 1.0   // fVScanDelay field default on screen
        const val VSCAN_MIN_DELAY = 1.0       // fVScanDelayMin
        const val VSCAN_MAX_DELAY = 5.0       // fVScanDelayMax
        const val VSCAN_START = -0.9          // bar entry position
        const val VSCAN_END = 1.5             // bar exit position
        const val VSCAN_IDLE = -1.0           // not rolling
        const val VSCAN_WIDTH = 0.38          // zone catchment half-width (in u)
        const val VSCAN_GAIN = 0.4            // brighten depth as the bar passes
        // Zone centres along the normalised bar travel u∈[0,1] (top→bottom).
        // rightTop pulled up to 0.5 so the right stick's start overlaps the
        // left stick's end (no dead mid-screen gap); wide WIDTH blends the handoff.
        const val U_LEFT_TOP = 0.0
        const val U_LEFT_BOTTOM = 0.33
        const val U_RIGHT_TOP = 0.5
        const val U_RIGHT_BOTTOM = 0.83

        // Pip-Boy phosphor green — the default when no real colour has been
        // supplied yet (e.g. before the in-game HUD EffectColor arrives).
        const val PIPBOY_GREEN = 0xFF00FF00.toInt()
        const val DARK_SUM_THRESHOLD = 24     // r+g+b below this ⇒ treat as unset

        // Transient nav reactions, triggered out-of-band via ACTION_PULSE
        // (Strip-Boy menu navigation). These modulate brightness on top of the
        // running animation WITHOUT a restart, so they never fight the
        // heartbeat re-anchor or the external-API rate limiter.
        const val PULSE_MS = 180L
        const val PULSE_MUL = 3.0             // brightness multiplier at the pop's peak
        const val STATIC_MS = 380L
        const val STATIC_MUL = 3.0            // brightness multiplier during the scramble
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var targetColor: Int = greenIfUnset(initialColor)
    private var currentColor: Int = greenIfUnset(initialColor)
    private var targetRightColor: Int = greenIfUnset(initialRightColor)
    private var currentRightColor: Int = greenIfUnset(initialRightColor)
    private var targetBrightness: Int = 255
    private var loggedColor = false
    private var loggedRgb = false

    /** Default to Pip-Boy green when the supplied colour is black/near-black. */
    private fun greenIfUnset(c: Int): Int =
        if (Color.red(c) + Color.green(c) + Color.blue(c) < DARK_SUM_THRESHOLD) PIPBOY_GREEN else c

    // Phase origin. Defaults to the moment start() is called; can be re-aligned
    // to the screen's clock via setPhaseOrigin so the pulse tracks in phase.
    private var originMs: Long = 0L

    // Flicker scheduler state — a deterministic mirror of the screen's
    // countdown loop, driven by the shared seeded LCG. Re-seeded and
    // fast-forwarded to the screen's clock in setPhaseOrigin().
    private var flickering = false
    private var fFlickerDelay = FLICKER_INITIAL_DELAY
    private var flickerRng = FLICKER_SEED
    private var lastTickT = 0.0
    private var pipBurst = 0.0    // decaying burst-flash envelope (0..BURST_TRIGGER)

    // Vertical-scan sim (independent seeded RNG; mirrors the screen).
    private var vscanRng = VSCAN_SEED
    private var fVScanDelay = VSCAN_INITIAL_DELAY
    private var fVScanState = VSCAN_IDLE

    // Transient nav reactions (set by triggerPulse/triggerStatic, read each
    // tick). elapsedRealtime deadlines; volatile since the triggers fire from
    // the service/broadcast path while the runnable reads on the LED looper.
    @Volatile private var pulseUntilMs = 0L
    @Volatile private var staticUntilMs = 0L
    private var brightnessMul = 1.0   // per-tick brightness scale (1.0 = resting)

    /** One LCG draw mapped to [lo, hi). Identical maths screen-side. */
    private fun seededRange(lo: Double, hi: Double): Double {
        flickerRng = flickerRng * LCG_MUL + LCG_ADD     // UInt overflow == mod 2^32
        val frac = flickerRng.toDouble() / 4294967296.0  // 2^32
        return lo + frac * (hi - lo)
    }

    /** vscan's independent LCG draw. Same maths, separate state + seed. */
    private fun vscanRange(lo: Double, hi: Double): Double {
        vscanRng = vscanRng * LCG_MUL + LCG_ADD
        val frac = vscanRng.toDouble() / 4294967296.0
        return lo + frac * (hi - lo)
    }

    /**
     * Advance the vscan sim by dt, mirroring PipboyPostEffect.Update's vscan
     * block exactly (schedule → sweep → idle-countdown), drawing from the
     * vscan RNG in the same order as the screen.
     */
    private fun advanceVScan(dt: Double) {
        if (fVScanDelay <= 0.0) {
            fVScanDelay = vscanRange(VSCAN_MIN_DELAY, VSCAN_MAX_DELAY)
            fVScanState = VSCAN_START
        }
        if (fVScanState >= VSCAN_START) {
            fVScanState += VSCAN_RATE * dt
            if (fVScanState > VSCAN_END) fVScanState = VSCAN_IDLE
        } else {
            fVScanDelay -= dt
        }
    }

    /** Per-zone vscan brighten boost for a zone centred at u-position [center]. */
    private fun vscanBoost(center: Double): Double {
        if (fVScanState < VSCAN_START) return 0.0   // not rolling
        val u = (fVScanState - VSCAN_START) / (VSCAN_END - VSCAN_START)  // 0..1, top→bottom
        val d = kotlin.math.abs(u - center)
        if (d >= VSCAN_WIDTH) return 0.0
        return (1.0 - d / VSCAN_WIDTH) * VSCAN_GAIN
    }

    /** Scale [color] by (baseFactor + boost) × intensity and write to the
     *  selected LED zone(s). Brightness is in the RGB magnitude. */
    private fun emitZone(
        color: Int, baseFactor: Double, boost: Double,
        lt: Boolean, lb: Boolean, rt: Boolean, rb: Boolean
    ) {
        val f = ((baseFactor + boost) * (targetBrightness / 255.0) * brightnessMul).coerceIn(0.0, 1.0)
        val r = (Color.red(color) * f).roundToInt().coerceIn(0, 255)
        val g = (Color.green(color) * f).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(color) * f).roundToInt().coerceIn(0, 255)
        ledController.setLedColor(r, g, b,
            leftTop = lt, leftBottom = lb, rightTop = rt, rightBottom = rb)
    }

    /**
     * Advance the flicker countdown by dt seconds, mirroring Bethesda's
     * PipboyPostEffect.Update exactly — including draw order, so the LCG
     * state stays in lockstep with the screen:
     *   on each expiry: toggle; if now ON draw 1 (duration); if now OFF
     *   draw 2 (delay, then the 15%-burst-chance roll).
     */
    private fun advanceFlicker(dt: Double) {
        fFlickerDelay -= dt
        var guard = 0
        while (fFlickerDelay <= 0.0 && guard < 64) {
            guard++
            flickering = !flickering
            if (flickering) {
                fFlickerDelay += seededRange(FLICKER_MIN_DURATION, FLICKER_MAX_DURATION)
            } else {
                fFlickerDelay += seededRange(FLICKER_MIN_DELAY, FLICKER_MAX_DELAY)
                // burst-chance roll (15%) — mirrors the screen's TriggerBurst.
                if (seededRange(0.0, 1.0) < BURST_CHANCE) pipBurst = BURST_TRIGGER
            }
        }
    }

    private fun resetFlicker() {
        flickering = false
        fFlickerDelay = FLICKER_INITIAL_DELAY
        flickerRng = FLICKER_SEED
        lastTickT = 0.0
        pipBurst = 0.0
        vscanRng = VSCAN_SEED
        fVScanDelay = VSCAN_INITIAL_DELAY
        fVScanState = VSCAN_IDLE
    }

    /** Brief brightness pop for a menu-item switch. Modulates the running
     *  animation in place — no restart, so it can't be clobbered by the
     *  heartbeat re-anchor and never trips the rate limiter. */
    fun triggerPulse() { pulseUntilMs = SystemClock.elapsedRealtime() + PULSE_MS }

    /** TV-static scramble for the dramatic vertical-hold "channel swap" roll. */
    fun triggerStatic() { staticUntilMs = SystemClock.elapsedRealtime() + STATIC_MS }

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
        originMs = SystemClock.elapsedRealtime() -
            (afSecondsSinceTheirOrigin * 1000.0).toLong()
        // Re-seed and fast-forward both sims to the screen's clock so the
        // sticks land on the screen's exact current flicker + vscan state,
        // then continue in lockstep. vscan integrates position, so step it in
        // small fixed increments to avoid overshoot (flicker is countdown-based
        // and step-size-invariant, but stepping it together keeps draw order).
        resetFlicker()
        var acc = 0.0
        val step = 1.0 / 120.0
        var guard = 0
        while (acc < afSecondsSinceTheirOrigin && guard < 1_000_000) {
            guard++
            val d = kotlin.math.min(step, afSecondsSinceTheirOrigin - acc)
            advanceFlicker(d)
            advanceVScan(d)
            acc += d
        }
        lastTickT = afSecondsSinceTheirOrigin
    }

    /**
     * Lightweight drift correction (called on heartbeats): nudge the clock so
     * the plugin's t tracks the screen's fTime again, WITHOUT re-seeding. The
     * sims keep their state; the next tick's dt absorbs the (small) correction
     * — negative/oversized dt is guarded + capped in the tick loop.
     */
    fun reanchor(afSecondsSinceTheirOrigin: Double) {
        if (afSecondsSinceTheirOrigin <= 0.0) return
        // DIAGNOSTIC (measure-first): drift = how far the plugin clock has
        // wandered from the screen's fTime since the last anchor. Small+stable
        // ⇒ synced; growing between heartbeats ⇒ game-time≠wall-clock.
        val curT = (SystemClock.elapsedRealtime() - originMs) / 1000.0
        android.util.Log.i("BIBI", "PipBoy reanchor: pluginT=%.2f screenT=%.2f drift=%dms"
            .format(curT, afSecondsSinceTheirOrigin,
                ((curT - afSecondsSinceTheirOrigin) * 1000).toLong()))
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

            if (!loggedColor) {
                loggedColor = true
                android.util.Log.i("BIBI",
                    "PipBoy: target=#%08X bright=%d".format(targetColor, targetBrightness))
            }

            val t = (SystemClock.elapsedRealtime() - originMs) / 1000.0
            // Guard + cap dt: a heartbeat reanchor can nudge t; never advance
            // backward, never fast-forward more than half a second in one tick.
            val dt = (t - lastTickT).coerceIn(0.0, 0.5)
            lastTickT = t

            // Flicker + burst + vscan schedulers — deterministic, seeded.
            if (pipBurst > 0.0) pipBurst = (pipBurst - dt * BURST_FADE).coerceAtLeast(0.0)
            advanceFlicker(dt)
            val vscanWasActive = fVScanState >= VSCAN_START
            advanceVScan(dt)
            // DIAGNOSTIC: log each vscan roll start with the (locked) screen clock.
            if (!vscanWasActive && fVScanState >= VSCAN_START) {
                android.util.Log.i("BIBI", "VSCAN_ROLL pluginT=%.2f".format(t))
            }

            // Base brightness factor common to all zones: pulse + flicker + burst.
            val pulse = 1.0 + perlin1d(t * PULSE_RATE) * PULSE_INTENSITY
            var baseFactor = 0.5 + (pulse - 1.0)   // 0.5..1.0
            if (flickering) {
                val shimmer = perlin1d(t * FLICKER_FREQUENCY)
                baseFactor *= (1.0 - shimmer * FLICKER_DEPTH)   // downward stutter
            }
            if (pipBurst > 0.0) baseFactor += pipBurst * BURST_GAIN   // upward flash

            // Transient nav reactions (menu pulse / channel-swap static),
            // applied on top of the base — no restart, set out-of-band by
            // triggerPulse()/triggerStatic().
            val nowMs = SystemClock.elapsedRealtime()
            when {
                nowMs < staticUntilMs -> {
                    baseFactor = Math.random()        // bright random scramble
                    brightnessMul = STATIC_MUL
                }
                nowMs < pulseUntilMs -> {
                    val k = (pulseUntilMs - nowMs).toDouble() / PULSE_MS   // 1→0
                    brightnessMul = 1.0 + k * (PULSE_MUL - 1.0)            // pop → settle
                }
                else -> brightnessMul = 1.0
            }

            // Vertical-scan: while a bar is rolling, brighten each zone as the
            // bar passes its u-position (leftTop→leftBottom→rightTop→rightBottom).
            // Idle → uniform 2-write path (cheaper, no per-zone divergence).
            if (fVScanState >= VSCAN_START) {
                emitZone(currentColor, baseFactor, vscanBoost(U_LEFT_TOP),
                    lt = true, lb = false, rt = false, rb = false)
                emitZone(currentColor, baseFactor, vscanBoost(U_LEFT_BOTTOM),
                    lt = false, lb = true, rt = false, rb = false)
                emitZone(currentRightColor, baseFactor, vscanBoost(U_RIGHT_TOP),
                    lt = false, lb = false, rt = true, rb = false)
                emitZone(currentRightColor, baseFactor, vscanBoost(U_RIGHT_BOTTOM),
                    lt = false, lb = false, rt = false, rb = true)
            } else {
                emitZone(currentColor, baseFactor, 0.0,
                    lt = true, lb = true, rt = false, rb = false)
                emitZone(currentRightColor, baseFactor, 0.0,
                    lt = false, lb = false, rt = true, rb = true)
            }

            handler.postDelayed(this, adjustedAnimationDelay(TICK_MS, targetBrightness))
        }
    }

    override fun start() {
        if (running) return
        running = true
        if (originMs == 0L) originMs = SystemClock.elapsedRealtime()
        // If setPhaseOrigin already ran it has seeded+fast-forwarded the sim;
        // only reset from scratch when starting without an alignment.
        if (lastTickT == 0.0) resetFlicker()
        handler.post(runnable)
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        ledController.setLedColor(
            0, 0, 0,
            leftTop = true, leftBottom = true,
            rightTop = true, rightBottom = true
        )
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
