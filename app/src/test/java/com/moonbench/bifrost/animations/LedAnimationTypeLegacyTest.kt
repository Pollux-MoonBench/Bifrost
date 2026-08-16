package com.moonbench.bifrost.animations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedAnimationTypeLegacyTest {

    @Test fun ambilightFromOlderVersionsStillResolves() {
        assertEquals(LedAnimationType.AMBIENT, LedAnimationType.fromStoredName("AMBILIGHT"))
    }

    @Test fun currentNamesResolve() {
        LedAnimationType.values().forEach { type ->
            assertEquals(type, LedAnimationType.fromStoredName(type.name))
        }
    }

    @Test fun unknownAndEmptyNamesGiveNull() {
        assertNull(LedAnimationType.fromStoredName("NOPE"))
        assertNull(LedAnimationType.fromStoredName(""))
        assertNull(LedAnimationType.fromStoredName(null))
    }
}
