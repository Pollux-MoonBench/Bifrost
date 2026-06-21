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

        // Shared flicker RNG — MUST stay bit-identical with the screen side
        // (Strip-Boy's FlickerSeed Cecil patch). Same LCG, same seed, same
        // draw order ⇒ the sticks reproduce the screen's exact flicker
        // sequence with no runtime signalling. Numerical-Recipes LCG.
        const val FLICKER_SEED = 0x50B0FFu    // "PIPBOY" seed; mirrored screen-side
        const val LCG_MUL = 1664525u
        const val LCG_ADD = 1013904223u

        // Pip-Boy phosphor green — the default when no real colour has been
        // supplied yet (e.g. before the in-game HUD EffectColor arrives).
        const val PIPBOY_GREEN = 0xFF00FF00.toInt()
        const val DARK_SUM_THRESHOLD = 24     // r+g+b below this ⇒ treat as unset
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

    /** One LCG draw mapped to [lo, hi). Identical maths screen-side. */
    private fun seededRange(lo: Double, hi: Double): Double {
        flickerRng = flickerRng * LCG_MUL + LCG_ADD     // UInt overflow == mod 2^32
        val frac = flickerRng.toDouble() / 4294967296.0  // 2^32
        return lo + frac * (hi - lo)
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
                seededRange(0.0, 1.0)   // burst-chance roll — drawn to keep RNG aligned
            }
        }
    }

    private fun resetFlicker() {
        flickering = false
        fFlickerDelay = FLICKER_INITIAL_DELAY
        flickerRng = FLICKER_SEED
        lastTickT = 0.0
    }

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
        // Re-seed and fast-forward the flicker sim to the screen's clock so
        // the sticks land on the screen's exact current flicker state, then
        // continue in lockstep.
        resetFlicker()
        if (afSecondsSinceTheirOrigin > 0.0) {
            advanceFlicker(afSecondsSinceTheirOrigin)
        }
        lastTickT = afSecondsSinceTheirOrigin
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

            // Flicker scheduler — deterministic countdown driven by the shared
            // seeded LCG, advanced by real elapsed time. Mirrors the screen.
            advanceFlicker(t - lastTickT)
            lastTickT = t

            // Pulse: 1.0..1.5 → normalise to a 0.5..1.0 LED envelope.
            val pulse = 1.0 + perlin1d(t * PULSE_RATE) * PULSE_INTENSITY
            var factor = 0.5 + (pulse - 1.0)   // pulse-1.0 ∈ [0,0.5] → factor ∈ [0.5,1.0]

            // Flicker: downward stutter only.
            if (flickering) {
                val shimmer = perlin1d(t * FLICKER_FREQUENCY)   // 0..1, fast
                factor *= (1.0 - shimmer * FLICKER_DEPTH)
            }

            val globalScale = targetBrightness / 255.0
            val ledFactor = (factor * globalScale).coerceIn(0.0, 1.0)

            val lr = (Color.red(currentColor) * ledFactor).roundToInt().coerceIn(0, 255)
            val lg = (Color.green(currentColor) * ledFactor).roundToInt().coerceIn(0, 255)
            val lb = (Color.blue(currentColor) * ledFactor).roundToInt().coerceIn(0, 255)

            val rr = (Color.red(currentRightColor) * ledFactor).roundToInt().coerceIn(0, 255)
            val rg = (Color.green(currentRightColor) * ledFactor).roundToInt().coerceIn(0, 255)
            val rb = (Color.blue(currentRightColor) * ledFactor).roundToInt().coerceIn(0, 255)

            if (!loggedRgb) {
                loggedRgb = true
                android.util.Log.i("BIBI",
                    "PipBoy RGB out: (%d,%d,%d) ledFactor=%.3f targetBright=%d (brightness via RGB; 4th field=255 ignored)"
                        .format(lr, lg, lb, ledFactor, targetBrightness))
            }

            ledController.setLedColor(lr, lg, lb,
                leftTop = true, leftBottom = true,
                rightTop = false, rightBottom = false)
            ledController.setLedColor(rr, rg, rb,
                leftTop = false, leftBottom = false,
                rightTop = true, rightBottom = true)

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
