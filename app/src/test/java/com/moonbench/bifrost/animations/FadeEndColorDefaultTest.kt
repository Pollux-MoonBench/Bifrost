package com.moonbench.bifrost.animations

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FadeEndColorDefaultTest {

    @Test fun defaultEndColorIsTheHistoricalCyan() {
        assertEquals(0xFF00FFFF.toInt(), FadeTransitionAnimation.DEFAULT_END_COLOR)
    }

    @Test fun presetJsonWithoutTheKeyFallsBackToTheDefault() {
        val legacy = JSONObject("""{"name":"Old","color":-1,"rightColor":-1}""")

        val parsed = legacy.optInt("fadeEndColor", FadeTransitionAnimation.DEFAULT_END_COLOR)

        assertEquals(FadeTransitionAnimation.DEFAULT_END_COLOR, parsed)
    }

    @Test fun rightTargetFallsBackToTheLeftOneWhenAbsent() {
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
