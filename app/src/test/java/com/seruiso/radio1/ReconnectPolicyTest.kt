package com.seruiso.radio1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun fastWindow_growsWithAttempt() {
        val d0 = ReconnectPolicy.nextDelayMs(0L, 0)
        val d3 = ReconnectPolicy.nextDelayMs(0L, 3)
        assertTrue(d0 >= 700L)
        assertTrue(d3 >= d0)
        assertTrue(d3 <= 3000L)
    }

    @Test
    fun afterFastWindow_usesTenSeconds() {
        assertEquals(10_000L, ReconnectPolicy.nextDelayMs(60_000L, 0))
        assertEquals(10_000L, ReconnectPolicy.nextDelayMs(120_000L, 5))
    }

    @Test
    fun windowExpired() {
        assertFalse(ReconnectPolicy.windowExpired(0L))
        assertFalse(ReconnectPolicy.windowExpired(5 * 60_000L))
        assertTrue(ReconnectPolicy.windowExpired(5 * 60_000L + 1L))
    }
}
