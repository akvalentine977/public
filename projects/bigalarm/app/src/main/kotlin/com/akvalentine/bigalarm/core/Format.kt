package com.akvalentine.bigalarm.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Human-readable text for times and repeat rules. Pure Kotlin. */
object Format {

    fun time(hour: Int, minute: Int, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val pattern = if (is24h) "HH:mm" else "h:mm a"
        return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun date(epochDay: Long, locale: Locale = Locale.getDefault()): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale))

    fun repeat(repeat: Repeat, locale: Locale = Locale.getDefault()): String = when (repeat.type) {
        RepeatType.ONCE ->
            repeat.anchorEpochDay?.let { "Once on " + date(it, locale) } ?: "Once"

        RepeatType.DAILY -> "Every day"

        RepeatType.WEEKLY -> when (repeat.daysMask and Repeat.ALL_DAYS) {
            0 -> "No days selected"
            Repeat.ALL_DAYS -> "Every day"
            Repeat.MON_TO_FRI -> "Weekdays"
            Repeat.WEEKEND -> "Weekends"
            else -> DayOfWeek.entries
                .filter { repeat.has(it) }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        }

        RepeatType.EVERY_N_DAYS -> {
            val n = repeat.intervalDays.coerceAtLeast(1)
            if (n == 1) "Every day" else "Every $n days"
        }
    }

    /** "in 3 h 12 min", "in 2 days", etc. Used for the "next ring" hint. */
    fun untilText(next: ZonedDateTime, now: ZonedDateTime): String {
        val minutes = ChronoUnit.MINUTES.between(now, next).coerceAtLeast(0)
        val days = minutes / (60 * 24)
        val hours = (minutes % (60 * 24)) / 60
        val mins = minutes % 60
        return when {
            days >= 2 -> "in $days days"
            days == 1L -> "in 1 day ${hours}h"
            hours > 0 -> "in ${hours}h ${mins}min"
            else -> "in ${mins}min"
        }
    }
}
