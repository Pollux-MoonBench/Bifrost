package com.moonbench.bifrost.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the dip-to-black crossfade curve: full → black over the out phase,
 * black → full over the in phase, swap at the black midpoint, clamped to [0,1].
 * An off-by-one in the phase boundaries here would mean a visible flash or a
 * stuck-dim revert on-device.
 */
class CrossfadeTest {

    private val out = 170L
    private val inn = 230L

    @Test fun startsAtFullScale() {
        assertEquals(1f, Crossfade.scaleAt(0, out, inn), 1e-4f)
    }

    @Test fun dimsToBlackAcrossOutPhase() {
        assertEquals(0.5f, Crossfade.scaleAt(85, out, inn), 1e-3f)   // halfway out
        // Just before the midpoint it is nearly black; AT the midpoint exactly 0.
        assertTrue(Crossfade.scaleAt(169, out, inn) < 0.01f)
        assertEquals(0f, Crossfade.scaleAt(out, out, inn), 1e-4f)    // swap happens here
    }

    @Test fun risesFromBlackAcrossInPhase() {
        assertEquals(0.5f, Crossfade.scaleAt(out + inn / 2, out, inn), 1e-3f)  // halfway in
        assertTrue(Crossfade.scaleAt(out + inn - 1, out, inn) > 0.99f)
    }

    @Test fun settlesAtFullScaleAfter() {
        assertEquals(1f, Crossfade.scaleAt(out + inn, out, inn), 1e-4f)
        assertEquals(1f, Crossfade.scaleAt(out + inn + 500, out, inn), 1e-4f)
    }

    @Test fun negativeElapsedIsFullScale() {
        assertEquals(1f, Crossfade.scaleAt(-5, out, inn), 1e-4f)
    }

    @Test fun staysWithinUnitRangeThroughout() {
        // Sample the whole fade at a fixed step (bounded loop).
        var t = -20L
        while (t <= out + inn + 40) {
            val s = Crossfade.scaleAt(t, out, inn)
            assertTrue("scale $s out of range at t=$t", s in 0f..1f)
            t += 5
        }
    }

    @Test fun outPhaseMonotonicDownInPhaseMonotonicUp() {
        // Out phase strictly non-increasing.
        var prev = Crossfade.scaleAt(0, out, inn)
        var t = 5L
        while (t < out) {
            val s = Crossfade.scaleAt(t, out, inn)
            assertTrue("out phase not descending at t=$t", s <= prev + 1e-4f)
            prev = s; t += 5
        }
        // In phase strictly non-decreasing.
        prev = Crossfade.scaleAt(out, out, inn)
        t = out + 5
        while (t < out + inn) {
            val s = Crossfade.scaleAt(t, out, inn)
            assertTrue("in phase not ascending at t=$t", s >= prev - 1e-4f)
            prev = s; t += 5
        }
    }

    @Test fun isCompleteOnlyAfterBothPhases() {
        assertFalse(Crossfade.isComplete(out + inn - 1, out, inn))
        assertTrue(Crossfade.isComplete(out + inn, out, inn))
        assertTrue(Crossfade.isComplete(out + inn + 100, out, inn))
    }
}
