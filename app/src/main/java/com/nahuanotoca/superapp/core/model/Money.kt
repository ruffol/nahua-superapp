package com.nahuanotoca.superapp.core.model

import kotlin.math.absoluteValue

/**
 * Mexican peso formatting without depending on [java.text.NumberFormat], so the behaviour is
 * identical in unit tests, previews and on device.
 */
object Money {

    fun format(cents: Long): String {
        val negative = cents < 0L
        val absolute = cents.absoluteValue
        val units = absolute / 100L
        val decimals = absolute % 100L
        val grouped = units.toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        val sign = if (negative) "-" else ""
        return sign + "$" + grouped + "." + decimals.toString().padStart(2, '0')
    }

    fun formatSigned(cents: Long): String = when {
        cents > 0L -> "+" + format(cents)
        else -> format(cents)
    }
}
