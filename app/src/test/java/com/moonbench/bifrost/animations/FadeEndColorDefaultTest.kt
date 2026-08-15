package com.moonbench.bifrost.animations

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fade target became user-selectable in 1.3.0. Presets written before that
 * carry no "fadeEndColor" key, and every parser in the app falls back to the
 * colour the effect used to hardcode. If that default ever drifts, those presets
 * silently change appearance with no build error — hence this pin.
 */
class FadeEndColorDefaultTest {

    @Test fun defaultEndColorIsTheHistoricalCyan() {
        assertEquals(0xFF00FFFF.toInt(), FadeTransitionAnimation.DEFAULT_END_COLOR)
    }

    @Test fun presetJsonWithoutTheKeyFallsBackToTheDefault() {
        // Shape written by 1.2.x and earlier: colours present, fade target absent.
        val legacy = JSONObject("""{"name":"Old","color":-1,"rightColor":-1}""")

        val parsed = legacy.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)

        assertEquals(FadeTransitionAnimation.DEFAULT_END_COLOR, parsed)
    }

    @Test fun rightTargetFallsBackToTheLeftOneWhenAbsent() {
        // Presets written between the single-target and per-stick versions carry
        // "fadeEndColor" alone. Falling back to it — not to the historical cyan —
        // keeps those presets looking exactly as they did.
        val singleTarget = JSONObject("""{"name":"Mid","fadeEndColor":-65536}""")

        val left = singleTarget.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)
        val right = singleTarget.optInt("fadeEndRightColor", left)

        assertEquals(-65536, right)
    }

    @Test fun presetJsonWithTheKeyKeepsTheStoredColour() {
        val stored = JSONObject("""{"name":"New","color":-1,"fadeEndColor":-65536}""")

        val parsed = stored.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)

        assertEquals(-65536, parsed)
    }
}
