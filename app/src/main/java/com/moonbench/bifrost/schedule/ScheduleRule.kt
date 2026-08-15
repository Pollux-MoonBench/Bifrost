package com.moonbench.bifrost.schedule

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay

/**
 * What a rule does while it is in force.
 *
 * Turning the LEDs off is an action rather than the absence of a rule: "no
 * lights between 7am and 8pm" has to beat the preset a broader rule would
 * otherwise play, and only an explicit action can express that.
 */
sealed class ScheduleAction {
    data class PlayPreset(val presetName: String) : ScheduleAction()
    object TurnOff : ScheduleAction()

    fun serialise(): JSONObject = JSONObject().apply {
        when (this@ScheduleAction) {
            is PlayPreset -> {
                put("kind", KIND_PRESET)
                put("preset", presetName)
            }
            TurnOff -> put("kind", KIND_OFF)
        }
    }

    companion object {
        private const val KIND_PRESET = "preset"
        private const val KIND_OFF = "off"

        fun parse(obj: JSONObject?): ScheduleAction? {
            if (obj == null) return null
            return when (obj.optString("kind")) {
                KIND_PRESET -> obj.optString("preset")
                    .takeIf { it.isNotBlank() }
                    ?.let { PlayPreset(it) }
                KIND_OFF -> TurnOff
                else -> null
            }
        }
    }
}

/**
 * A calendar window, given as month/day pairs so it repeats every year without
 * being re-entered. Both ends are inclusive. A window may wrap the new year
 * (December 20 → January 5), which is why containment is not a plain range
 * comparison.
 */
data class DateWindow(val start: MonthDay, val end: MonthDay) {

    fun contains(date: LocalDate): Boolean {
        val today = MonthDay.of(date.monthValue, date.dayOfMonth)
        // February 29 in a non-leap year: MonthDay handles the comparison, the
        // date simply never occurs, so no special case is needed here.
        return if (start <= end) {
            today >= start && today <= end
        } else {
            today >= start || today <= end
        }
    }

    fun serialise(): JSONObject = JSONObject().apply {
        put("startMonth", start.monthValue)
        put("startDay", start.dayOfMonth)
        put("endMonth", end.monthValue)
        put("endDay", end.dayOfMonth)
    }

    companion object {
        fun parse(obj: JSONObject?): DateWindow? {
            if (obj == null) return null
            val startMonth = obj.optInt("startMonth", 0)
            val startDay = obj.optInt("startDay", 0)
            val endMonth = obj.optInt("endMonth", 0)
            val endDay = obj.optInt("endDay", 0)
            return runCatching {
                DateWindow(MonthDay.of(startMonth, startDay), MonthDay.of(endMonth, endDay))
            }.getOrNull()
        }
    }
}

/**
 * One line of the schedule: "between these hours, on these days of the year, do
 * this".
 *
 * [startMinuteOfDay] is inclusive and [endMinuteOfDay] exclusive, so two rules
 * meeting at 20:00 don't both claim that minute. A window whose end is at or
 * before its start wraps midnight (20:00 → 07:00), which is the common case for
 * night lighting and would otherwise need two rules.
 */
data class ScheduleRule(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val dateWindow: DateWindow?,
    val action: ScheduleAction
) {

    fun coversTime(minuteOfDay: Int): Boolean {
        if (startMinuteOfDay == endMinuteOfDay) return true // whole day
        return if (startMinuteOfDay < endMinuteOfDay) {
            minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
        } else {
            minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }
    }

    fun covers(moment: LocalDateTime): Boolean {
        if (!enabled) return false
        val window = dateWindow
        // A wrapping night rule that starts before midnight belongs to the day it
        // started on: at 01:00 on January 1, the rule that began at 20:00 on
        // December 31 is the one in force, so its window is tested against that
        // earlier date.
        val effectiveDate = if (
            window != null &&
            startMinuteOfDay > endMinuteOfDay &&
            moment.hour * 60 + moment.minute < endMinuteOfDay
        ) {
            moment.toLocalDate().minusDays(1)
        } else {
            moment.toLocalDate()
        }
        if (window != null && !window.contains(effectiveDate)) return false
        return coversTime(moment.hour * 60 + moment.minute)
    }

    fun serialise(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("enabled", enabled)
        put("startMinuteOfDay", startMinuteOfDay)
        put("endMinuteOfDay", endMinuteOfDay)
        dateWindow?.let { put("dateWindow", it.serialise()) }
        put("action", action.serialise())
    }

    companion object {
        fun parse(obj: JSONObject?): ScheduleRule? {
            if (obj == null) return null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val action = ScheduleAction.parse(obj.optJSONObject("action")) ?: return null
            val start = obj.optInt("startMinuteOfDay", -1)
            val end = obj.optInt("endMinuteOfDay", -1)
            if (start !in 0..1439 || end !in 0..1439) return null
            return ScheduleRule(
                id = id,
                label = obj.optString("label", ""),
                enabled = obj.optBoolean("enabled", true),
                startMinuteOfDay = start,
                endMinuteOfDay = end,
                dateWindow = DateWindow.parse(obj.optJSONObject("dateWindow")),
                action = action
            )
        }
    }
}
