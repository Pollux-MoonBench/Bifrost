package com.moonbench.bifrost.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.services.HeimdallStartupManager
import com.moonbench.bifrost.services.LEDService
import java.time.LocalDateTime

/**
 * Turns "which rule is in force" into an actual start or stop of the LED
 * service, and arms the next wake-up.
 *
 * Called from the alarm receiver, from boot, and whenever the schedule is
 * edited — the same path every time, so the LEDs can't end up reflecting a rule
 * that was deleted an hour ago.
 */
object ScheduleApplier {

    private const val TAG = "ScheduleApplier"
    private const val PREFS_NAME = "bifrost_prefs"

    fun apply(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!ScheduleStore.isEnabled(prefs)) {
            ScheduleAlarms.cancel(context)
            return
        }

        val rules = ScheduleStore.load(prefs)
        when (val action = ScheduleEvaluator.ruleInForce(rules, now)?.action) {
            is ScheduleAction.PlayPreset -> {
                val intent = HeimdallStartupManager.buildServiceIntentForPreset(
                    context,
                    prefs,
                    action.presetName
                )
                if (intent == null) {
                    // The preset was renamed or deleted after the rule was written.
                    // Doing nothing beats stopping the LEDs on a stale name.
                    Log.w(TAG, "schedule references unknown preset '${action.presetName}'")
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
            }

            ScheduleAction.TurnOff -> {
                if (LEDService.isRunning) {
                    context.stopService(Intent(context, LEDService::class.java))
                }
            }

            // No rule in force: leave whatever is playing alone. The schedule
            // states when it takes over, not when it gives up control.
            null -> Unit
        }

        ScheduleAlarms.scheduleNext(context, rules, now)
    }
}
