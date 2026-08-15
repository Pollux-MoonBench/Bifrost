package com.moonbench.bifrost.schedule

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.services.HeimdallStartupManager
import com.moonbench.bifrost.services.LEDService
import java.time.LocalDateTime

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
        when (val action = ScheduleEvaluator.ruleInForce(rules, now)?.action ?: ScheduleAction.TurnOff) {
            is ScheduleAction.PlayPreset -> {
                val intent = HeimdallStartupManager.buildServiceIntentForPreset(
                    context,
                    prefs,
                    action.presetName
                )
                if (intent == null) {
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
        }

        ScheduleAlarms.scheduleNext(context, rules, now)
    }
}
