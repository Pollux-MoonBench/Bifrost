package com.moonbench.bifrost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.moonbench.bifrost.services.BifrostAccessibilityService

class StartupGuideActivity : AppCompatActivity() {

    companion object {
        private const val PREF_STARTUP_GUIDE_DONE = "startup_guide_done"
    }

    private lateinit var pageNotification: View
    private lateinit var pageAccessibility: View
    private lateinit var pageAudio: View
    private lateinit var pageDone: View

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPages()
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPages()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup_guide)

        pageNotification = findViewById(R.id.pageNotification)
        pageAccessibility = findViewById(R.id.pageAccessibility)
        pageAudio = findViewById(R.id.pageAudio)
        pageDone = findViewById(R.id.pageDone)

        findViewById<Button>(R.id.buttonGrantNotifications).setOnClickListener {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.buttonOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.buttonGrantAudio).setOnClickListener {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        findViewById<Button>(R.id.buttonContinue).setOnClickListener {
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
        val audioGranted = isAudioGranted()

        pageNotification.visibility = if (!notifGranted) View.VISIBLE else View.GONE
        pageAccessibility.visibility = if (notifGranted && !accessibilityEnabled) View.VISIBLE else View.GONE
        pageAudio.visibility = if (notifGranted && accessibilityEnabled && !audioGranted) View.VISIBLE else View.GONE

        val allGranted = notifGranted && accessibilityEnabled && audioGranted
        pageDone.visibility = if (allGranted) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.guideProgressText).text = when {
            !notifGranted -> "Step 1/3 - Enable notifications"
            !accessibilityEnabled -> "Step 2/3 - Enable accessibility capture"
            !audioGranted -> "Step 3/3 - Enable internal audio access"
            else -> "Setup complete"
        }
    }

    private fun isNotificationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAudioGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun markGuideDone() {
        getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_STARTUP_GUIDE_DONE, true)
            .apply()
    }
}


