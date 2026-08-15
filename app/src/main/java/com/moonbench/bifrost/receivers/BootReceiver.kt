package com.moonbench.bifrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.schedule.ScheduleApplier
import com.moonbench.bifrost.services.HeimdallStartupManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isStartupSignal = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isStartupSignal) return

        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)

        // Alarms do not survive a reboot, so the schedule re-arms itself here —
        // and it does so whether or not auto-start is on, since a rule may be the
        // very thing meant to light the sticks after boot.
        ScheduleApplier.apply(context)

        if (!HeimdallStartupManager.isAutoStartEnabled(prefs)) return

        val serviceIntent = HeimdallStartupManager.buildStartupDecision(context, prefs).serviceIntent ?: return
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
