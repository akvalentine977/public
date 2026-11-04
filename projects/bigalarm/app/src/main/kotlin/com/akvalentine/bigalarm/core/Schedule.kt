package com.akvalentine.bigalarm.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Computes when an alarm should fire next. Pure Kotlin, covered by unit tests. */
object Schedule {

    /**
     * The next moment strictly after [now] at which [alarm] should fire, or null if it never will
     * (disabled, expired one-off, or a weekly alarm with no days selected).
     */
    fun nextTrigger(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        alarm.snoozedUntil?.let { until ->
            val snoozeAt = Instant.ofEpochMilli(until).atZone(zone)
            if (snoozeAt.isAfter(now)) return snoozeAt
        }
        if (!alarm.enabled) return null

        val time = alarm.time
        val today = now.toLocalDate()
        // ZonedDateTime.of() resolves DST gaps by shifting forward and picks the earlier offset in overlaps.
        fun at(date: LocalDate): ZonedDateTime = ZonedDateTime.of(date.atTime(time), zone)

        val repeat = alarm.repeat
        return when (repeat.type) {
            RepeatType.ONCE -> {
                val specific = repeat.anchorEpochDay?.let(LocalDate::ofEpochDay)
                if (specific != null) {
                    at(specific).takeIf { it.isAfter(now) }
                } else {
                    at(today).takeIf { it.isAfter(now) } ?: at(today.plusDays(1))
                }
            }

            RepeatType.DAILY -> at(today).takeIf { it.isAfter(now) } ?: at(today.plusDays(1))

            RepeatType.WEEKLY -> {
                if (repeat.daysMask and Repeat.ALL_DAYS == 0) return null
                (0L..7L)
                    .map { today.plusDays(it) }
                    .filter { repeat.has(it.dayOfWeek) }
                    .map(::at)
                    .firstOrNull { it.isAfter(now) }
            }

            RepeatType.EVERY_N_DAYS -> {
                val n = repeat.intervalDays.coerceAtLeast(1).toLong()
                val anchor = repeat.anchorEpochDay?.let(LocalDate::ofEpochDay) ?: today
                var date = if (today.isBefore(anchor)) {
                    anchor
                } else {
                    val elapsed = ChronoUnit.DAYS.between(anchor, today)
                    anchor.plusDays(elapsed - elapsed % n)
                }
                var candidate = at(date)
                while (!candidate.isAfter(now)) {
                    date = date.plusDays(n)
                    candidate = at(date)
                }
                candidate
            }
        }
    }

    /** The persisted state of an alarm right after it has fired. */
    fun afterFiring(alarm: Alarm): Alarm = alarm.copy(
        snoozedUntil = null,
        enabled = if (alarm.repeat.type == RepeatType.ONCE) false else alarm.enabled,
    )

    /** Epoch millis for [nextTrigger], for AlarmManager. */
    fun nextTriggerMillis(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long? =
        nextTrigger(alarm, now)?.toInstant()?.toEpochMilli()
}
