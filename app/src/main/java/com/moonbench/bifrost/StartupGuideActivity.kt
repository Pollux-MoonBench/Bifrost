package com.moonbench.bifrost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.services.AppProfileManager
import com.moonbench.bifrost.services.BifrostAccessibilityService

class StartupGuideActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BIBI"
        private const val PREF_STARTUP_GUIDE_DONE = "startup_guide_done"
    }

    private lateinit var pageNotification: View
    private lateinit var pageAccessibility: View
    private lateinit var pageUsage: View
    private lateinit var pageDone: View
    private lateinit var buttonSkip: TextView
    private lateinit var buttonCompleteSetup: MaterialButton
    private lateinit var appProfileManager: AppProfileManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPages()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup_guide)
        appProfileManager = AppProfileManager(getSharedPreferences("bifrost_prefs", MODE_PRIVATE))

        pageNotification = findViewById(R.id.pageNotification)
        pageAccessibility = findViewById(R.id.pageAccessibility)
        pageUsage = findViewById(R.id.pageUsage)
        pageDone = findViewById(R.id.pageDone)
        buttonSkip = findViewById(R.id.buttonContinue)
        buttonCompleteSetup = findViewById(R.id.buttonCompleteSetup)

        findViewById<Button>(R.id.buttonGrantNotifications).setOnClickListener {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.buttonOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.buttonOpenUsageAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        buttonSkip.setOnClickListener {
            markGuideDone()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        buttonCompleteSetup.setOnClickListener {
            markGuideDone()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        refreshPages()
    }

    override fun onResume() {
        super.onResume()
        refreshPages()
    }

    private fun refreshPages() {
        val notifGranted = isNotificationGranted()
        val accessibilityEnabled = BifrostAccessibilityService.isEnabled(this)
        val usageGranted = appProfileManager.hasUsageStatsPermission(this)

        pageNotification.visibility = View.GONE
        pageAccessibility.visibility = View.GONE
        pageUsage.visibility = View.GONE
        pageDone.visibility = View.GONE

        val allGranted = notifGranted && accessibilityEnabled && usageGranted

        Log.i(
            TAG,
            "Startup refresh: notif=$notifGranted accessibility=$accessibilityEnabled usage=$usageGranted allGranted=$allGranted"
        )

        val progressText = when {
            !notifGranted -> {
                pageNotification.visibility = View.VISIBLE
                "Step 1/3 - Enable notifications"
            }
            !accessibilityEnabled -> {
                pageAccessibility.visibility = View.VISIBLE
                "Step 2/3 - Enable accessibility capture"
            }
            !usageGranted -> {
                pageUsage.visibility = View.VISIBLE
                "Step 3/3 - Enable usage access"
            }
            else -> {
                pageDone.visibility = View.VISIBLE
                "Setup complete"
            }
        }

        if (allGranted) {
            buttonSkip.visibility = View.GONE
            buttonCompleteSetup.visibility = View.VISIBLE
        } else {
            buttonSkip.visibility = View.VISIBLE
            buttonCompleteSetup.visibility = View.GONE
        }

        findViewById<TextView>(R.id.guideProgressText).text = progressText
    }

    private fun isNotificationGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    private fun markGuideDone() {
        getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_STARTUP_GUIDE_DONE, true)
            .apply()
    }
}


