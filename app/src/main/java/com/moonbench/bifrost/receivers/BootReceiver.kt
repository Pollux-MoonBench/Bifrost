package com.moonbench.bifrost.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.MainActivity
import com.moonbench.bifrost.R
import com.moonbench.bifrost.services.HeimdallStartupManager

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val STARTUP_CHANNEL_ID = "bifrost_startup_channel"
        private const val STARTUP_NOTIFICATION_ID = 4243
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isStartupSignal = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isStartupSignal) return

        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        if (!HeimdallStartupManager.isAutoStartEnabled(prefs)) return

        val startupDecision = HeimdallStartupManager.buildStartupDecision(context, prefs)
        val serviceIntent = startupDecision.serviceIntent

        if (serviceIntent != null) {
            ContextCompat.startForegroundService(context, serviceIntent)
            return
        }

        if (startupDecision.skipReason == HeimdallStartupManager.StartupSkipReason.MEDIA_PROJECTION_REQUIRES_USER_ACTION) {
            showMediaProjectionRequiredNotification(context)
        }
    }

    private fun showMediaProjectionRequiredNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createStartupNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, STARTUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground))
            .setContentTitle("Heimdall auto-start skipped")
            .setContentText("Last preset needs screen capture permission. Open Bifrost to start it.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(STARTUP_NOTIFICATION_ID, notification)
    }

    private fun createStartupNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            STARTUP_CHANNEL_ID,
            "Bifrost startup",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }
}
