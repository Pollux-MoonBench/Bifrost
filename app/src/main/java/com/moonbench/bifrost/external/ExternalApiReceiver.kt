package com.moonbench.bifrost.external

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log
import com.moonbench.bifrost.services.LEDService

class ExternalApiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BIBI/ExtApi"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            handleSafely(context, intent)
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive: unhandled error", t)
            setResult(ExternalApi.RESULT_REJECTED_VALIDATION, null)
        }
    }

    private fun handleSafely(context: Context, intent: Intent) {
        val callerUid = Binder.getCallingUid()
        val callerPackage = resolveCallerPackage(context, callerUid)
        if (callerPackage == null) {
            setResult(ExternalApi.RESULT_REJECTED_UNAUTHORIZED, null)
            return
        }

        if (!ExternalApiGate.isAllowed(context, callerPackage)) {
            setResult(ExternalApi.RESULT_REJECTED_DISABLED, null)
            return
        }

        if (!ExternalApiRateLimiter.allow(callerUid)) {
            setResult(ExternalApi.RESULT_REJECTED_RATE_LIMITED, null)
            return
        }

        val validated = ExternalCommandValidator.validate(intent, callerPackage)
        val cmd = validated.getOrElse { err ->
            val code = when ((err as? ExternalCommandValidator.ValidationException)?.kind) {
                ExternalCommandValidator.ErrorKind.UnsupportedVersion -> ExternalApi.RESULT_REJECTED_VERSION
                ExternalCommandValidator.ErrorKind.UnknownAction -> ExternalApi.RESULT_REJECTED_UNKNOWN_ACTION
                else -> ExternalApi.RESULT_REJECTED_VALIDATION
            }
            Log.d(TAG, "handle: rejected $callerPackage: ${err.message}")
            setResult(code, null)
            return
        }

        dispatch(context, cmd)
        setResult(ExternalApi.RESULT_ACCEPTED, cmd.requestId)
    }

    private fun dispatch(context: Context, cmd: ExternalApiCommand) {
        when (cmd) {
            is ExternalApiCommand.Display -> forwardToService(context, LEDService.ACTION_EXTERNAL_DISPLAY) {
                putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
                putExtra(LEDService.EXTRA_EXTERNAL_EFFECT, cmd.effect.name)
                putExtra(LEDService.EXTRA_EXTERNAL_COLOR, cmd.color)
                putExtra(LEDService.EXTRA_EXTERNAL_COLOR_RIGHT, cmd.colorRight)
                putExtra(LEDService.EXTRA_EXTERNAL_INTENSITY, cmd.intensity)
                putExtra(LEDService.EXTRA_EXTERNAL_SPEED, cmd.speed)
                putExtra(LEDService.EXTRA_EXTERNAL_SMOOTHNESS, cmd.smoothness)
                putExtra(LEDService.EXTRA_EXTERNAL_SENSITIVITY, cmd.sensitivity)
                putExtra(LEDService.EXTRA_EXTERNAL_PRIORITY, cmd.priority)
                putExtra(LEDService.EXTRA_EXTERNAL_PHASE_SECONDS, cmd.phaseSeconds)
                when (val t = cmd.terminator) {
                    is Terminator.Duration -> {
                        putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_DURATION)
                        putExtra(LEDService.EXTRA_EXTERNAL_DURATION_MS, t.millis)
                    }
                    Terminator.UntilNextCommand ->
                        putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_NEXT_COMMAND)
                    Terminator.UntilExplicitClear ->
                        putExtra(LEDService.EXTRA_EXTERNAL_TERMINATOR, LEDService.TERMINATOR_EXPLICIT_CLEAR)
                }
            }
            is ExternalApiCommand.Clear -> forwardToService(context, LEDService.ACTION_EXTERNAL_CLEAR) {
                putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, cmd.callerPackage)
            }
            is ExternalApiCommand.InstallProfile -> {
                val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
                ExternalProfileStore.installManagedPreset(prefs, cmd)
            }
            is ExternalApiCommand.UninstallProfile -> {
                val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
                ExternalProfileStore.uninstallManagedPreset(prefs, cmd.callerPackage, cmd.profileName)
            }
        }
    }

    /**
     * Only forward Display/Clear intents if the service is already live. External
     * callers can't start a foreground service from the background on Android 12+,
     * and silently dropping is less surprising than throwing SecurityException
     * back to the caller's process (they didn't cause it).
     */
    private fun forwardToService(
        context: Context,
        action: String,
        fill: Intent.() -> Unit,
    ) {
        if (!LEDService.isRunning) {
            Log.d(TAG, "forwardToService: service not running, dropping $action")
            return
        }
        val svc = Intent(context, LEDService::class.java).apply {
            this.action = action
            fill()
        }
        runCatching { context.startService(svc) }
            .onFailure { Log.w(TAG, "forwardToService: startService failed", it) }
    }

    private fun resolveCallerPackage(context: Context, uid: Int): String? {
        if (uid <= 0) return null
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    private fun setResult(code: Int, data: String?) {
        if (isOrderedBroadcast) {
            resultCode = code
            resultData = data
        }
    }
}

/**
 * Per-UID token bucket — 8 burst, 4/sec sustained. Lives in-process for the
 * life of Bifrost; cleared with process death. Memory use is negligible (one
 * entry per caller UID ever seen).
 */
private object ExternalApiRateLimiter {

    private const val BURST = 8
    private const val SUSTAINED_PER_SECOND = 4f

    private data class Bucket(var tokens: Float, var lastRefillMs: Long)

    private val buckets = HashMap<Int, Bucket>()

    @Synchronized
    fun allow(uid: Int): Boolean {
        val now = System.currentTimeMillis()
        val bucket = buckets.getOrPut(uid) { Bucket(BURST.toFloat(), now) }
        val elapsedSec = (now - bucket.lastRefillMs) / 1000f
        bucket.tokens = (bucket.tokens + elapsedSec * SUSTAINED_PER_SECOND)
            .coerceAtMost(BURST.toFloat())
        bucket.lastRefillMs = now
        return if (bucket.tokens >= 1f) {
            bucket.tokens -= 1f
            true
        } else {
            false
        }
    }
}
