package com.moonbench.bifrost.services

import android.content.Intent
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class ServiceController(
    private val activity: AppCompatActivity,
    private val handler: Handler,
    private val debounceDelay: Long,
    private val restartDelay: Long
) {

    var isServiceTransitioning: Boolean = false
        private set

    private var isOperationInProgress = false
    private var lastOperationTime = 0L
    private var pendingServiceOperation: Runnable? = null
    private var operationToken = 0

    var onNeedsMediaProjectionCheck: (() -> Unit)? = null

    fun cancelPendingOperations() {
        pendingServiceOperation?.let { handler.removeCallbacks(it) }
        pendingServiceOperation = null
        isOperationInProgress = false
        isServiceTransitioning = false
        operationToken++
    }

    private fun beginOperationWindow() {
        val now = System.currentTimeMillis()
        if (now - lastOperationTime < debounceDelay) {
            cancelPendingOperations()
        }
        lastOperationTime = now
        isOperationInProgress = true
        isServiceTransitioning = true
        operationToken++
    }

    private fun finishOperationWindowWithGraceDelay() {
        isOperationInProgress = false
        handler.postDelayed({
            isServiceTransitioning = false
        }, 200)
    }

    fun startDebounced(createIntent: () -> Intent) {
        if (isOperationInProgress) return
        beginOperationWindow()
        val token = operationToken

        pendingServiceOperation = Runnable {
            if (token != operationToken) return@Runnable
            try {
                activity.startService(createIntent())
            } finally {
                finishOperationWindowWithGraceDelay()
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }

    fun stopDebounced() {
        if (isOperationInProgress) return
        beginOperationWindow()
        val token = operationToken

        pendingServiceOperation = Runnable {
            if (token != operationToken) return@Runnable
            try {
                activity.stopService(Intent(activity, LEDService::class.java))
            } finally {
                finishOperationWindowWithGraceDelay()
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }

    fun restartDebounced(needsMediaProjectionCheck: Boolean = false, createIntent: () -> Intent) {
        if (isOperationInProgress) return

        if (needsMediaProjectionCheck) {
            onNeedsMediaProjectionCheck?.invoke()
            return
        }
        beginOperationWindow()
        val token = operationToken

        pendingServiceOperation = Runnable {
            if (token != operationToken) return@Runnable
            try {
                activity.stopService(Intent(activity, LEDService::class.java))
                handler.postDelayed({
                    if (token != operationToken) return@postDelayed
                    activity.startService(createIntent())
                    finishOperationWindowWithGraceDelay()
                }, restartDelay)
            } catch (e: Exception) {
                isOperationInProgress = false
                isServiceTransitioning = false
            }
        }

        handler.postDelayed(pendingServiceOperation!!, 100)
    }
}