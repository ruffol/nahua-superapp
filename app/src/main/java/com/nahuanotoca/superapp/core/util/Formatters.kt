package com.nahuanotoca.superapp.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH_MX: Locale = Locale.forLanguageTag("es-MX")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SPANISH_MX)
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", SPANISH_MX)

/**
 * Compact relative labels used across the wallet and inbox ("Ahora", "Hace 12 min", "Ayer").
 * Deterministic when [now] is passed in, which keeps it unit-testable.
 */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour

    return when {
        delta < minute -> "Ahora"
        delta < hour -> "Hace " + (delta / minute) + " min"
        delta < day -> "Hace " + (delta / hour) + " h"
        delta < 2 * day -> "Ayer"
        delta < 7 * day -> "Hace " + (delta / day) + " días"
        else -> DateTimeFormatter.ofPattern("d MMM", SPANISH_MX)
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }
}

fun formatClock(timestamp: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun formatShortDate(timestamp: Long): String =
    DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
