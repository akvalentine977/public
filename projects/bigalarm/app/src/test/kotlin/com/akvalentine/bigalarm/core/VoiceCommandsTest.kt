package com.akvalentine.bigalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {
    @Test
    fun dismissPhrases() {
        assertEquals(VoiceCommands.Command.DISMISS, VoiceCommands.classify("stop"))
        assertEquals(VoiceCommands.Command.DISMISS, VoiceCommands.classify("Stop the alarm"))
        assertEquals(VoiceCommands.Command.DISMISS, VoiceCommands.classify("turn it off"))
        assertEquals(VoiceCommands.Command.DISMISS, VoiceCommands.classify("okay I'm up"))
        assertEquals(VoiceCommands.Command.DISMISS, VoiceCommands.classify("dismiss"))
    }

    @Test
    fun snoozePhrases() {
        assertEquals(VoiceCommands.Command.SNOOZE, VoiceCommands.classify("snooze"))
        assertEquals(VoiceCommands.Command.SNOOZE, VoiceCommands.classify("five more minutes"))
        assertEquals(VoiceCommands.Command.SNOOZE, VoiceCommands.classify("not yet"))
        // Snooze wins when both appear.
        assertEquals(VoiceCommands.Command.SNOOZE, VoiceCommands.classify("stop, snooze it"))
    }

    @Test
    fun unrelatedSpeechIsIgnored() {
        assertNull(VoiceCommands.classify("coffee"))
        assertNull(VoiceCommands.classify("the office is closed"))
        assertNull(VoiceCommands.classify(""))
        assertEquals(VoiceCommands.Command.DISMISS, VoiceCommands.classify(listOf("coffee", "stop it")))
        assertNull(VoiceCommands.classify(emptyList()))
    }
}
