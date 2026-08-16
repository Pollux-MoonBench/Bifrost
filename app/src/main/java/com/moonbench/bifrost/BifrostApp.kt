package com.moonbench.bifrost

import android.app.Application
import com.moonbench.bifrost.tools.CrashReporter

class BifrostApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
