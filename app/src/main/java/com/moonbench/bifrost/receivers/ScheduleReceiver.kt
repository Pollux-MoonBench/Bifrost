package com.moonbench.bifrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moonbench.bifrost.schedule.ScheduleApplier

/**
 * Wakes the schedule up: on its own alarm, and on the system events that can
 * silently invalidate it.
 *
 * A time zone change or a manual clock change moves every boundary, and alarms
 * do not survive either — without these the schedule would keep the old
 * boundaries until the next reboot.
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULE_TICK = "com.moonbench.bifrost.action.SCHEDULE_TICK"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_SCHEDULE_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> ScheduleApplier.apply(context)
        }
    }
}
