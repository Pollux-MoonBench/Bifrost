package com.moonbench.bifrost.schedule

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object ScheduleEvaluator {

    fun ruleInForce(rules: List<ScheduleRule>, moment: LocalDateTime): ScheduleRule? {
        val matching = rules.filter { it.covers(moment) }
        if (matching.isEmpty()) return null
        return matching.firstOrNull { it.dateWindow != null } ?: matching.first()
    }

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
