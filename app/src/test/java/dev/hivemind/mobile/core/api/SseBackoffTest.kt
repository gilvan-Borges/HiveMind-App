package dev.hivemind.mobile.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseBackoffTest {

    @Test
    fun doublesFromOneSecondAndRespectsJitterBounds() {
        assertEquals(1_000L, SseStreamManager.backoffDelayMs(0, 0.5))
        assertEquals(2_000L, SseStreamManager.backoffDelayMs(1, 0.5))
        assertEquals(4_000L, SseStreamManager.backoffDelayMs(2, 0.5))
        // Jitter ±40%: fator ∈ [0.6, 1.4].
        assertEquals(600L, SseStreamManager.backoffDelayMs(0, 0.0))
        assertEquals(1_400L, SseStreamManager.backoffDelayMs(0, 1.0))
    }

    @Test
    fun capsAtThirtySecondsAndNeverDropsBelowFloor() {
        assertEquals(30_000L, SseStreamManager.backoffDelayMs(5, 1.0))
        assertEquals(30_000L, SseStreamManager.backoffDelayMs(12, 1.0))
        val slowFloor = SseStreamManager.backoffDelayMs(12, 0.0)
        assertTrue("esperado >= 18s no piso com jitter mínimo, veio $slowFloor", slowFloor >= 18_000L)
        val tiny = SseStreamManager.backoffDelayMs(-3, 0.0)
        assertTrue("piso de 250ms, veio $tiny", tiny >= 250L)
    }
}
