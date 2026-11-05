package com.akvalentine.bigalarm.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NameFormatTest {
    private val us = Locale.US

    @Test
    fun abbreviatesAppointmentAsWholeWord() {
        assertEquals("Dentist appt", NameFormat.display("Dentist appointment", uppercase = false, locale = us))
        assertEquals("Appt with Dr Lee", NameFormat.display("Appointment with Dr Lee", uppercase = false, locale = us))
        assertEquals("Two appts", NameFormat.display("Two appointments", uppercase = false, locale = us))
        // Not a whole word: left alone.
        assertEquals("disappointment", NameFormat.display("disappointment", uppercase = false, locale = us))
    }

    @Test
    fun uppercasesWhenAsked() {
        assertEquals("DENTIST APPT", NameFormat.display("Dentist appointment", uppercase = true, locale = us))
        assertEquals("TAKE MEDS", NameFormat.display("take meds", uppercase = true, locale = us))
    }

    @Test
    fun blankFallsBackToAlarm() {
        assertEquals("Alarm", NameFormat.display("   ", uppercase = false, locale = us))
        assertEquals("ALARM", NameFormat.display("", uppercase = true, locale = us))
    }
}
