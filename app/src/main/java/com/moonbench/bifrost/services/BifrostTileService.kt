package com.moonbench.bifrost.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.MainActivity
import com.moonbench.bifrost.animations.LedAnimationType

class BifrostTileService : TileService() {

    companion object {
        fun refreshFrom(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, BifrostTileService::class.java)
                )
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        renderState()
    }

    override fun onClick() {
        super.onClick()
        if (LEDService.isRunning) {
            stopService(Intent(this, LEDService::class.java))
            renderState(forcedRunning = false)
            return
        }

        val prefs = getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        val serviceIntent = HeimdallStartupManager.buildStartupDecision(this, prefs).serviceIntent

        if (serviceIntent == null) {
            openApp(startOnArrival = false)
            return
        }

        if (needsForegroundConsent(serviceIntent, prefs)) {
            openApp()
            return
        }

        ContextCompat.startForegroundService(this, serviceIntent)
        renderState(forcedRunning = true)
    }

    private fun needsForegroundConsent(
        serviceIntent: Intent,
        prefs: android.content.SharedPreferences
    ): Boolean {
        val type = serviceIntent.getStringExtra("animationType")
            ?.let { name -> LedAnimationType.fromStoredName(name) }
            ?: return false

        if (type.needsMediaProjection) return true

        return type == LedAnimationType.AMBIENT &&
            prefs.getBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, LEDService.DEFAULT_AMBILIGHT_USE_MEDIA_PROJECTION)
    }

    private fun openApp(startOnArrival: Boolean = true) {
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (startOnArrival) putExtra(MainActivity.EXTRA_START_FROM_TILE, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }

    private fun renderState(forcedRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val running = forcedRunning ?: LEDService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
