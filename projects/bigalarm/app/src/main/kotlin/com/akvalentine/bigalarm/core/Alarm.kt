package com.akvalentine.bigalarm.core

import java.time.DayOfWeek
import java.time.LocalTime

/** How an alarm repeats. Pure Kotlin so it can be unit-tested on the JVM. */
enum class RepeatType {
    /** Fires once, at the next occurrence of the time (or on [Repeat.anchorEpochDay] if set). */
    ONCE,
    /** Every day. */
    DAILY,
    /** On the days of the week selected in [Repeat.daysMask]. */
    WEEKLY,
    /** Every N days, counted from [Repeat.anchorEpochDay]. */
    EVERY_N_DAYS,
}

data class Repeat(
    val type: RepeatType = RepeatType.ONCE,
    /** Bit 0 = Monday ... bit 6 = Sunday (matches [DayOfWeek.getValue] - 1). */
    val daysMask: Int = 0,
    /** Interval for [RepeatType.EVERY_N_DAYS]; at least 1. */
    val intervalDays: Int = 2,
    /**
     * [java.time.LocalDate.toEpochDay] of the start date for [RepeatType.EVERY_N_DAYS],
     * or the specific date for a [RepeatType.ONCE] alarm. Null means "not set".
     */
    val anchorEpochDay: Long? = null,
) {
    fun has(day: DayOfWeek): Boolean = daysMask and bit(day) != 0

    fun toggled(day: DayOfWeek): Repeat = copy(daysMask = daysMask xor bit(day))

    companion object {
        const val ALL_DAYS = 0b1111111
        const val MON_TO_FRI = 0b0011111
        const val WEEKEND = 0b1100000

        fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

data class Alarm(
    val id: Long = 0,
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val repeat: Repeat = Repeat(),
    val vibrate: Boolean = true,
    val sound: Boolean = false,
    /** Epoch millis of a pending snooze, or null. A snooze overrides the regular schedule. */
    val snoozedUntil: Long? = null,
    val createdAt: Long = 0,
    /** Snooze length for this alarm, or null to use the app-wide default. */
    val snoozeMinutes: Int? = null,
    /** When true the alarm never times out; it rings until dismissed or snoozed. */
    val ringUntilDismissed: Boolean = false,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    /** Name to display; blank names fall back to "Alarm". */
    val displayName: String get() = name.ifBlank { "Alarm" }
}
