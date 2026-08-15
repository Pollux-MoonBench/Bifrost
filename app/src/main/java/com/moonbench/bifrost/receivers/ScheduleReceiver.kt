package com.moonbench.bifrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moonbench.bifrost.schedule.ScheduleApplier

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
