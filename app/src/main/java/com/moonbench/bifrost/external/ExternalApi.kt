package com.moonbench.bifrost.external

/**
 * Public contract for third-party apps that want Bifrost to display LED state
 * on their behalf. Copy these constants into the caller app (or depend on this
 * file directly) — nothing else under `external/` is part of the stable API.
 */
object ExternalApi {

    const val PERMISSION = "com.moonbench.bifrost.permission.CONTROL_LEDS"

    const val ACTION_DISPLAY = "com.moonbench.bifrost.api.ACTION_DISPLAY"
    const val ACTION_CLEAR = "com.moonbench.bifrost.api.ACTION_CLEAR"
    const val ACTION_INSTALL_PROFILE = "com.moonbench.bifrost.api.ACTION_INSTALL_PROFILE"
    const val ACTION_UNINSTALL_PROFILE = "com.moonbench.bifrost.api.ACTION_UNINSTALL_PROFILE"

    const val API_VERSION = 1

    const val EXTRA_API_VERSION = "apiVersion"
    const val EXTRA_EFFECT = "effect"
    const val EXTRA_COLOR = "color"
    const val EXTRA_COLOR_RIGHT = "colorRight"
    const val EXTRA_INTENSITY = "intensity"
    const val EXTRA_INTENSITY_SCALE = "intensityScale"
    const val EXTRA_SPEED = "speed"
    const val EXTRA_SMOOTHNESS = "smoothness"
    const val EXTRA_SENSITIVITY = "sensitivity"
    const val EXTRA_DURATION_MS = "durationMs"
    const val EXTRA_UNTIL = "until"
    const val EXTRA_INDEFINITE = "indefinite"
    const val EXTRA_PRIORITY = "priority"
    const val EXTRA_REQUEST_ID = "requestId"

    const val EXTRA_PROFILE_NAME = "profileName"
    const val EXTRA_PROFILE_REPLACE_IF_EXISTS = "replaceIfExists"
    const val EXTRA_SATURATION_BOOST = "saturationBoost"
    const val EXTRA_USE_CUSTOM_SAMPLING = "useCustomSampling"
    const val EXTRA_USE_SINGLE_COLOR = "useSingleColor"
    const val EXTRA_BREATHE_WHEN_CHARGING = "breatheWhenCharging"
    const val EXTRA_INDICATE_CHARGING_SPEED = "indicateChargingSpeed"
    const val EXTRA_FLASH_WHEN_READY = "flashWhenReady"
    const val EXTRA_BATTERY_LOW_COLOR = "batteryLowColor"
    const val EXTRA_BATTERY_MID_COLOR = "batteryMidColor"
    const val EXTRA_BATTERY_HIGH_COLOR = "batteryHighColor"
    const val EXTRA_CPU_COOL_COLOR = "cpuCoolColor"
    const val EXTRA_CPU_WARM_COLOR = "cpuWarmColor"
    const val EXTRA_CPU_HOT_COLOR = "cpuHotColor"

    const val UNTIL_NEXT_COMMAND = "NEXT_COMMAND"
    const val UNTIL_EXPLICIT_CLEAR = "EXPLICIT_CLEAR"

    const val RESULT_ACCEPTED = 0
    const val RESULT_REJECTED_DISABLED = -1
    const val RESULT_REJECTED_VERSION = -2
    const val RESULT_REJECTED_VALIDATION = -3
    const val RESULT_REJECTED_UNAUTHORIZED = -4
    const val RESULT_REJECTED_RATE_LIMITED = -5
    const val RESULT_REJECTED_UNKNOWN_ACTION = -6

    const val MAX_DURATION_MS = 600_000L
    const val MAX_REQUEST_ID_LENGTH = 64
}
