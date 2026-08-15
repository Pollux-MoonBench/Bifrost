package com.moonbench.bifrost.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.moonbench.bifrost.receivers.ScheduleReceiver
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Arms a single alarm for the next rule edge, re-armed each time it fires.
 *
 * One alarm rather than one per rule: the schedule only ever needs to know its
 * next boundary, and a single pending intent can't leak duplicates when rules
 * are edited.
 *
 * The alarm is inexact ([AlarmManager.setAndAllowWhileIdle]) on purpose. Exact
 * alarms need SCHEDULE_EXACT_ALARM, which Google restricts to alarm-clock-like
 * apps and users can revoke; a stick light arriving a minute or two late is not
 * worth a permission the app can lose.
 */
object ScheduleAlarms {

    private const val REQUEST_CODE = 8421

    fun scheduleNext(
        context: Context,
        rules: List<ScheduleRule>,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val next = ScheduleEvaluator.nextBoundary(rules, now)
        if (next == null) {
            cancel(context)
            return
        }

        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager(context).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(pendingIntent(context))
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_SCHEDULE_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
