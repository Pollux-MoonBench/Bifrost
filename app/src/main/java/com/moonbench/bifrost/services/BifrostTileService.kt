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

/**
 * Quick Settings tile: turn the LEDs on or off without opening the app.
 *
 * Asked for so the effect can be brought back in two swipes after the service
 * dies while a game is in the foreground (issue #12) — the tile is a faster way
 * back, not a fix for whatever killed the service.
 *
 * Starting from the tile reuses the auto-start path, so the tile plays exactly
 * what a reboot would: last used preset, or the app-profile default. Animations
 * that capture the screen can't start from here — MediaProjection consent needs
 * a visible Activity — so for those the tile opens the app instead of failing
 * silently.
 */
class BifrostTileService : TileService() {

    companion object {
        /**
         * Ask the system to re-poll the tile. Called by LEDService when it starts
         * or stops, so the tile doesn't sit on a stale state after a change made
         * from the app, the boot receiver or the external API.
         */
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

        // No preset saved yet, or the preset needs screen capture: both need the
        // app on screen, either to build a preset or to grant projection.
        if (serviceIntent == null || needsForegroundConsent(serviceIntent, prefs)) {
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
            ?.let { name -> runCatching { LedAnimationType.valueOf(name) }.getOrNull() }
            ?: return false

        if (type.needsMediaProjection) return true

        // AMBIENT can run either through the accessibility capture or through
        // MediaProjection, depending on a setting — only the latter needs consent.
        return type == LedAnimationType.AMBIENT &&
            prefs.getBoolean(LEDService.PREF_AMBILIGHT_USE_MEDIA_PROJECTION, false)
    }

    private fun openApp() {
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

    /**
     * [forcedRunning] covers the click path: the service flips its own flag a
     * moment later, so the tile would otherwise redraw with the old state and
     * only correct itself on the next poll.
     */
    private fun renderState(forcedRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val running = forcedRunning ?: LEDService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
