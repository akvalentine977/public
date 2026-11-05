package com.akvalentine.bigalarm.core

/** Maps what the speech recogniser heard to an alarm action. Pure Kotlin, unit-tested. */
object VoiceCommands {

    enum class Command { DISMISS, SNOOZE }

    /** Snooze phrases are checked first so "stop, snooze it" snoozes. Whole words only. */
    val snoozePhrases: List<String> = listOf("snooze", "later", "not yet", "more minutes", "five more", "ten more")

    val dismissPhrases: List<String> = listOf(
        "stop", "dismiss", "turn off", "turn it off", "shut up", "cancel", "off", "i'm up", "i am up", "enough",
    )

    private fun compile(phrases: List<String>) =
        phrases.map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    private val snooze = compile(snoozePhrases)
    private val dismiss = compile(dismissPhrases)

    fun classify(utterance: String): Command? {
        val text = utterance.trim()
        if (text.isEmpty()) return null
        if (snooze.any { it.containsMatchIn(text) }) return Command.SNOOZE
        if (dismiss.any { it.containsMatchIn(text) }) return Command.DISMISS
        return null
    }

    /** First candidate (most likely first) that maps to a command. */
    fun classify(candidates: List<String>): Command? = candidates.firstNotNullOfOrNull { classify(it) }
}
