package com.moonbench.bifrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.services.HeimdallStartupManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isStartupSignal = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isStartupSignal) return

        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        if (!HeimdallStartupManager.isAutoStartEnabled(prefs)) return

        val serviceIntent = HeimdallStartupManager.buildStartupDecision(context, prefs).serviceIntent ?: return
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
