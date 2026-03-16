package com.moonbench.bifrost.tools

import android.os.Build

object DeviceInfo {
    /**
     * True when running on an AYN Thor device (any generation).
     * Detection is based on manufacturer "ayn" and model name containing "thor".
     */
    val isAynThor: Boolean by lazy {
        Build.MANUFACTURER.lowercase().contains("ayn") &&
            Build.MODEL.lowercase().contains("thor")
    }
}
