package com.akvalentine.bigalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    // Wednesday 4 Nov 2026, 10:00 local time.
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, zone)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test
    fun onceLaterToday() {
        val alarm = Alarm(hour = 15, minute = 30)
        assertEquals(at(2026, 11, 4, 15, 30), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun onceEarlierTodayRollsToTomorrow() {
        val alarm = Alarm(hour = 9, minute = 0)
        assertEquals(at(2026, 11, 5, 9, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun onceExactlyNowRollsToTomorrow() {
        val alarm = Alarm(hour = 10, minute = 0)
        assertEquals(at(2026, 11, 5, 10, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun onceOnSpecificFutureDate() {
        val date = LocalDate.of(2026, 11, 20).toEpochDay()
        val alarm = Alarm(hour = 8, minute = 15, repeat = Repeat(RepeatType.ONCE, anchorEpochDay = date))
        assertEquals(at(2026, 11, 20, 8, 15), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun onceOnPastDateNeverFires() {
        val date = LocalDate.of(2026, 11, 1).toEpochDay()
        val alarm = Alarm(hour = 8, minute = 15, repeat = Repeat(RepeatType.ONCE, anchorEpochDay = date))
        assertNull(Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun disabledNeverFires() {
        val alarm = Alarm(hour = 15, minute = 0, enabled = false, repeat = Repeat(RepeatType.DAILY))
        assertNull(Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun daily() {
        val alarm = Alarm(hour = 6, minute = 45, repeat = Repeat(RepeatType.DAILY))
        assertEquals(at(2026, 11, 5, 6, 45), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun weeklyPicksNextSelectedDay() {
        // Now is Wednesday. Selected: Monday and Friday -> Friday 6 Nov.
        val mask = Repeat.bit(DayOfWeek.MONDAY) or Repeat.bit(DayOfWeek.FRIDAY)
        val alarm = Alarm(hour = 7, minute = 0, repeat = Repeat(RepeatType.WEEKLY, daysMask = mask))
        assertEquals(at(2026, 11, 6, 7, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun weeklySameDayLaterToday() {
        val mask = Repeat.bit(DayOfWeek.WEDNESDAY)
        val alarm = Alarm(hour = 23, minute = 0, repeat = Repeat(RepeatType.WEEKLY, daysMask = mask))
        assertEquals(at(2026, 11, 4, 23, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun weeklySameDayAlreadyPassedWaitsAWeek() {
        val mask = Repeat.bit(DayOfWeek.WEDNESDAY)
        val alarm = Alarm(hour = 9, minute = 0, repeat = Repeat(RepeatType.WEEKLY, daysMask = mask))
        assertEquals(at(2026, 11, 11, 9, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun weeklyWithNoDaysNeverFires() {
        val alarm = Alarm(hour = 9, minute = 0, repeat = Repeat(RepeatType.WEEKLY, daysMask = 0))
        assertNull(Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun everyNDaysFromAnchor() {
        // Anchor Sunday 1 Nov, every 3 days: 1, 4, 7, 10 Nov. Today is the 4th, 10:00; alarm at 09:00 -> 7 Nov.
        val anchor = LocalDate.of(2026, 11, 1).toEpochDay()
        val alarm = Alarm(hour = 9, minute = 0, repeat = Repeat(RepeatType.EVERY_N_DAYS, intervalDays = 3, anchorEpochDay = anchor))
        assertEquals(at(2026, 11, 7, 9, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun everyNDaysTodayLater() {
        val anchor = LocalDate.of(2026, 11, 1).toEpochDay()
        val alarm = Alarm(hour = 18, minute = 0, repeat = Repeat(RepeatType.EVERY_N_DAYS, intervalDays = 3, anchorEpochDay = anchor))
        assertEquals(at(2026, 11, 4, 18, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun everyNDaysFutureAnchor() {
        val anchor = LocalDate.of(2026, 12, 1).toEpochDay()
        val alarm = Alarm(hour = 9, minute = 0, repeat = Repeat(RepeatType.EVERY_N_DAYS, intervalDays = 5, anchorEpochDay = anchor))
        assertEquals(at(2026, 12, 1, 9, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun snoozeOverridesSchedule() {
        val until = at(2026, 11, 4, 10, 10).toInstant().toEpochMilli()
        val alarm = Alarm(hour = 7, minute = 0, repeat = Repeat(RepeatType.DAILY), snoozedUntil = until)
        assertEquals(at(2026, 11, 4, 10, 10), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun expiredSnoozeIsIgnored() {
        val until = at(2026, 11, 4, 9, 50).toInstant().toEpochMilli()
        val alarm = Alarm(hour = 7, minute = 0, repeat = Repeat(RepeatType.DAILY), snoozedUntil = until)
        assertEquals(at(2026, 11, 5, 7, 0), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun snoozeWorksEvenWhenOneOffAlreadyDisabled() {
        val until = at(2026, 11, 4, 10, 10).toInstant().toEpochMilli()
        val alarm = Alarm(hour = 7, minute = 0, enabled = false, snoozedUntil = until)
        assertEquals(at(2026, 11, 4, 10, 10), Schedule.nextTrigger(alarm, now))
    }

    @Test
    fun dstGapShiftsForward() {
        // US DST starts 8 Mar 2026: 02:30 does not exist; expect 03:30 EDT.
        val before = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, zone)
        val alarm = Alarm(hour = 2, minute = 30, repeat = Repeat(RepeatType.DAILY))
        val next = Schedule.nextTrigger(alarm, before)!!
        assertEquals(LocalDate.of(2026, 3, 8), next.toLocalDate())
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun afterFiringDisablesOneOffAndClearsSnooze() {
        val once = Alarm(hour = 7, minute = 0, snoozedUntil = 123L)
        val fired = Schedule.afterFiring(once)
        assertFalse(fired.enabled)
        assertNull(fired.snoozedUntil)

        val daily = Alarm(hour = 7, minute = 0, repeat = Repeat(RepeatType.DAILY), snoozedUntil = 123L)
        val firedDaily = Schedule.afterFiring(daily)
        assertEquals(true, firedDaily.enabled)
        assertNull(firedDaily.snoozedUntil)
    }

    @Test
    fun repeatSummaries() {
        assertEquals("Once", Format.repeat(Repeat()))
        assertEquals("Every day", Format.repeat(Repeat(RepeatType.DAILY)))
        assertEquals("Weekdays", Format.repeat(Repeat(RepeatType.WEEKLY, daysMask = Repeat.MON_TO_FRI)))
        assertEquals("Weekends", Format.repeat(Repeat(RepeatType.WEEKLY, daysMask = Repeat.WEEKEND)))
        assertEquals("Every 3 days", Format.repeat(Repeat(RepeatType.EVERY_N_DAYS, intervalDays = 3)))
        assertEquals(
            "Mon, Wed",
            Format.repeat(Repeat(RepeatType.WEEKLY, daysMask = Repeat.bit(DayOfWeek.MONDAY) or Repeat.bit(DayOfWeek.WEDNESDAY)), java.util.Locale.US),
        )
        assertEquals("7:05 AM", Format.time(7, 5, is24h = false, locale = java.util.Locale.US))
        assertEquals("19:05", Format.time(19, 5, is24h = true, locale = java.util.Locale.US))
    }
}
