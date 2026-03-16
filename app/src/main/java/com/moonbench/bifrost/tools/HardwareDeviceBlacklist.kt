package com.moonbench.bifrost.tools

import android.media.AudioDeviceInfo

object HardwareDeviceBlacklist {

    private val blockedMicrophoneTypes = setOf(
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_TELEPHONY,
        AudioDeviceInfo.TYPE_BLE_HEADSET
    )

    private val blockedMicrophoneIdentifierFragments = listOf(
        "mic",
        "microphone",
        "camcorder",
        "handset",
        "voice",
        "top",
        "bottom",
        "front",
        "rear",
        "back"
    )

    fun isBlockedMicrophoneDevice(device: AudioDeviceInfo?): Boolean {
        if (device == null) return false

        if (device.type in blockedMicrophoneTypes) {
            return true
        }

        val address = device.address?.lowercase().orEmpty()
        val productName = device.productName?.toString()?.lowercase().orEmpty()
        return blockedMicrophoneIdentifierFragments.any {
            address.contains(it) || productName.contains(it)
        }
    }

    fun isBlockedCameraIdentifier(identifier: String?): Boolean {
        return true
    }
}
