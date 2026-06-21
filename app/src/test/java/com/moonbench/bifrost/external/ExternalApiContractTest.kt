package com.moonbench.bifrost.external

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the external IPC wire contract.
 *
 * Strip-Boy (the Fallout 4 Pip-Boy patch) is a separate APK that hardcodes
 * these exact action/extra strings in smali to drive the LEDs. Renaming a
 * constant here compiles fine but SILENTLY breaks the integration (the menu
 * pulse / channel-swap static, the colour/flicker dispatch). Pin the values.
 */
class ExternalApiContractTest {

    @Test
    fun action_pulse_contract_is_stable() {
        assertEquals("com.moonbench.bifrost.api.ACTION_PULSE", ExternalApi.ACTION_PULSE)
        assertEquals("pulseKind", ExternalApi.EXTRA_PULSE_KIND)
        assertEquals("PULSE", ExternalApi.PULSE_KIND_PULSE)
        assertEquals("STATIC", ExternalApi.PULSE_KIND_STATIC)
    }

    @Test
    fun display_contract_is_stable() {
        assertEquals("com.moonbench.bifrost.api.ACTION_DISPLAY", ExternalApi.ACTION_DISPLAY)
        assertEquals("phaseSeconds", ExternalApi.EXTRA_PHASE_SECONDS)
        assertEquals("com.moonbench.bifrost.permission.CONTROL_LEDS", ExternalApi.PERMISSION)
    }
}
