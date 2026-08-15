package com.moonbench.bifrost.schedule

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Decides which rule is in force, and when the answer could next change.
 *
 * Pure and clock-free on purpose: every decision is a function of the moment
 * passed in, so the whole schedule is testable without a device, an alarm or a
 * fake clock.
 */
object ScheduleEvaluator {

    /**
     * The rule in force at [moment], or null when the schedule has nothing to
     * say and the LEDs should be left exactly as the user set them.
     *
     * A rule limited to a date window wins over an all-year rule: "red and green
     * in December" is meant as an exception to "blue at night", not a tie. Among
     * rules of equal specificity the first in the list wins, so the order shown
     * in the UI is the order that decides.
     */
    fun ruleInForce(rules: List<ScheduleRule>, moment: LocalDateTime): ScheduleRule? {
        val matching = rules.filter { it.covers(moment) }
        if (matching.isEmpty()) return null
        return matching.firstOrNull { it.dateWindow != null } ?: matching.first()
    }

    /**
     * The next moment the outcome could differ, so the caller knows when to wake
     * up. Every rule edge counts, including edges of rules that are not in force
     * — one of them starting is exactly what changes the answer.
     *
     * Midnight is always a candidate when a dated rule exists: the date changing
     * can add or remove a rule with no time edge of its own.
     */
    fun nextBoundary(rules: List<ScheduleRule>, moment: LocalDateTime): LocalDateTime? {
        val enabled = rules.filter { it.enabled }
        if (enabled.isEmpty()) return null

        val candidates = mutableListOf<LocalDateTime>()
        val startOfDay = moment.truncatedTo(ChronoUnit.DAYS)

        enabled.forEach { rule ->
            listOf(rule.startMinuteOfDay, rule.endMinuteOfDay).forEach { minute ->
                val today = startOfDay.plusMinutes(minute.toLong())
                candidates += if (today.isAfter(moment)) today else today.plusDays(1)
            }
        }

        if (enabled.any { it.dateWindow != null }) {
            candidates += startOfDay.plusDays(1)
        }

        return candidates.minOrNull()
    }
}
