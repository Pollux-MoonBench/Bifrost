package com.moonbench.bifrost.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.MonthDay

/**
 * The schedule decides what the LEDs do while nobody is watching, so every
 * awkward case it can meet on a device is pinned here instead: windows that
 * wrap midnight, windows that wrap the new year, and a holiday rule that has to
 * beat the everyday rule it overlaps.
 */
class ScheduleEvaluatorTest {

    private fun rule(
        id: String,
        startHour: Int,
        endHour: Int,
        window: DateWindow? = null,
        action: ScheduleAction = ScheduleAction.PlayPreset(id),
        enabled: Boolean = true
    ) = ScheduleRule(
        id = id,
        label = id,
        enabled = enabled,
        startMinuteOfDay = startHour * 60,
        endMinuteOfDay = endHour * 60,
        dateWindow = window,
        action = action
    )

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, month, day, hour, minute)

    // ---- time windows ----------------------------------------------------

    @Test fun plainWindowCoversItsOwnHours() {
        val night = rule("night", 20, 23)
        assertEquals(night, ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 21)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 19)))
    }

    @Test fun windowEndIsExclusiveSoNeighboursDoNotOverlap() {
        val day = rule("day", 7, 20)
        assertTrue(day.coversTime(19 * 60 + 59))
        assertTrue(!day.coversTime(20 * 60))
    }

    @Test fun windowWrappingMidnightCoversBothSidesOfIt() {
        // The case the issue actually asks for: blue from 20:00 to 07:00.
        val night = rule("night", 20, 7)
        assertEquals(night, ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 23)))
        assertEquals(night, ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 2)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 12)))
    }

    @Test fun disabledRuleNeverApplies() {
        val night = rule("night", 20, 7, enabled = false)
        assertNull(ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 23)))
    }

    // ---- date windows ----------------------------------------------------

    @Test fun datedRuleAppliesOnlyInsideItsSeason() {
        val christmas = rule(
            "christmas", 0, 0,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        assertEquals(christmas, ScheduleEvaluator.ruleInForce(listOf(christmas), at(12, 24, 18)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(christmas), at(11, 30, 18)))
    }

    @Test fun dateWindowWrappingTheNewYearHoldsAcrossIt() {
        val newYear = DateWindow(MonthDay.of(12, 20), MonthDay.of(1, 5))
        assertTrue(newYear.contains(at(12, 31, 12).toLocalDate()))
        assertTrue(newYear.contains(at(1, 2, 12).toLocalDate()))
        assertTrue(!newYear.contains(at(6, 15, 12).toLocalDate()))
    }

    @Test fun nightRuleStartedBeforeMidnightKeepsItsStartingDaysSeason() {
        // 01:00 on January 1: the rule in force began at 20:00 on December 31,
        // so a December-only window must still hold.
        val december = rule(
            "december", 20, 7,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        assertEquals(december, ScheduleEvaluator.ruleInForce(listOf(december), at(1, 1, 1)))
    }

    // ---- precedence ------------------------------------------------------

    @Test fun datedRuleOutranksTheEverydayRuleItOverlaps() {
        val everyNight = rule("blue-night", 20, 7)
        val christmas = rule(
            "christmas", 20, 7,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        val rules = listOf(everyNight, christmas)
        assertEquals(christmas, ScheduleEvaluator.ruleInForce(rules, at(12, 24, 22)))
        assertEquals(everyNight, ScheduleEvaluator.ruleInForce(rules, at(6, 24, 22)))
    }

    @Test fun amongEqualsTheFirstRuleWins() {
        val first = rule("first", 20, 23)
        val second = rule("second", 21, 23)
        assertEquals(first, ScheduleEvaluator.ruleInForce(listOf(first, second), at(6, 15, 22)))
    }

    @Test fun turnOffIsAnActionThatCanBeatAPreset() {
        val daylightOff = rule("off", 7, 20, action = ScheduleAction.TurnOff)
        val inForce = ScheduleEvaluator.ruleInForce(listOf(daylightOff), at(6, 15, 12))
        assertEquals(ScheduleAction.TurnOff, inForce?.action)
    }

    // ---- boundaries ------------------------------------------------------

    @Test fun nextBoundaryIsTheNearestEdgeOfAnyRule() {
        val night = rule("night", 20, 7)
        assertEquals(at(6, 15, 20), ScheduleEvaluator.nextBoundary(listOf(night), at(6, 15, 18)))
        assertEquals(at(6, 16, 7), ScheduleEvaluator.nextBoundary(listOf(night), at(6, 15, 21)))
    }

    @Test fun edgesOfRulesNotInForceStillCount() {
        val night = rule("night", 20, 23)
        val morning = rule("morning", 8, 9)
        assertEquals(
            at(6, 15, 8),
            ScheduleEvaluator.nextBoundary(listOf(night, morning), at(6, 15, 6))
        )
    }

    @Test fun midnightIsABoundaryWhenASeasonalRuleExists() {
        // Nothing else changes on November 30 at 23:00, but the season does.
        val christmas = rule(
            "christmas", 10, 10,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        assertEquals(
            at(12, 1, 0),
            ScheduleEvaluator.nextBoundary(listOf(christmas), at(11, 30, 23))
        )
    }

    @Test fun emptyOrFullyDisabledScheduleHasNoBoundary() {
        assertNull(ScheduleEvaluator.nextBoundary(emptyList(), at(6, 15, 12)))
        assertNull(
            ScheduleEvaluator.nextBoundary(
                listOf(rule("off", 20, 7, enabled = false)),
                at(6, 15, 12)
            )
        )
    }

    // ---- persistence -----------------------------------------------------

    @Test fun rulesSurviveARoundTripThroughJson() {
        val original = rule(
            "christmas", 20, 7,
            window = DateWindow(MonthDay.of(12, 20), MonthDay.of(1, 5)),
            action = ScheduleAction.PlayPreset("Red & Green")
        )
        assertEquals(original, ScheduleRule.parse(original.serialise()))
    }

    @Test fun turnOffSurvivesARoundTripThroughJson() {
        val original = rule("off", 7, 20, action = ScheduleAction.TurnOff)
        assertEquals(original, ScheduleRule.parse(original.serialise()))
    }

    @Test fun malformedRuleIsDroppedRatherThanCrashing() {
        assertNull(ScheduleRule.parse(org.json.JSONObject("""{"id":"x"}""")))
    }
}
