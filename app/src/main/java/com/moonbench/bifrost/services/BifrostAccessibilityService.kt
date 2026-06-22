package com.moonbench.bifrost.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BifrostAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BifrostAccessibilityService? = null
            private set

        const val FALLOUT_PKG = "com.bethsoft.falloutcompanionapp"

        // Which display the Fallout Pip-Boy companion is currently on. The Thor
        // has two internal displays; "mirror mode" captures whichever one shows
        // the Pip-Boy. Updated from window events; defaults to the main display
        // until the companion is observed.
        @Volatile
        var falloutDisplayId: Int = android.view.Display.DEFAULT_DISPLAY
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!enabled) return false

            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return services.split(':').any { component ->
                component.equals("${context.packageName}/${BifrostAccessibilityService::class.java.name}", ignoreCase = true)
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        Log.d("BifrostA11yService", "onServiceConnected called")
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            Log.d("BifrostA11yService", "Set serviceInfo flags: $flags")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Remember which display the Fallout companion is on (window state/changed
        // events carry the display id on API 30+). Cheap — no window-content
        // retrieval — so mirror mode can follow the Pip-Boy across the Thor's
        // two screens.
        val e = event ?: return
        if (e.packageName?.toString() == FALLOUT_PKG) {
            falloutDisplayId = e.displayId
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}

