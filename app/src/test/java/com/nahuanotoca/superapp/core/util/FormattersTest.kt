package com.nahuanotoca.superapp.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    private val now = 1_700_000_000_000L

    @Test
    fun recentMovementsUseRelativeLabels() {
        assertEquals("Ahora", formatRelativeTime(now, now))
        assertEquals("Hace 5 min", formatRelativeTime(now - 5 * 60_000L, now))
        assertEquals("Hace 3 h", formatRelativeTime(now - 3 * 60 * 60_000L, now))
        assertEquals("Ayer", formatRelativeTime(now - 30 * 60 * 60_000L, now))
        assertEquals("Hace 3 días", formatRelativeTime(now - 3 * 24 * 60 * 60_000L, now))
    }

    @Test
    fun futureTimestampsAreClamped() {
        assertEquals("Ahora", formatRelativeTime(now + 60_000L, now))
    }
}
