package com.nahuanotoca.superapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun formatsZero() {
        assertEquals("\$0.00", Money.format(0L))
    }

    @Test
    fun formatsCents() {
        assertEquals("\$0.05", Money.format(5L))
        assertEquals("\$1.00", Money.format(100L))
        assertEquals("\$85.50", Money.format(8_550L))
    }

    @Test
    fun groupsThousands() {
        assertEquals("\$1,234.56", Money.format(123_456L))
        assertEquals("\$1,000,000.00", Money.format(100_000_000L))
    }

    @Test
    fun formatsNegativeAmounts() {
        assertEquals("-\$85.00", Money.format(-8_500L))
    }

    @Test
    fun formatsSignedAmounts() {
        assertEquals("+\$50.00", Money.formatSigned(5_000L))
        assertEquals("-\$50.00", Money.formatSigned(-5_000L))
        assertEquals("\$0.00", Money.formatSigned(0L))
    }
}
