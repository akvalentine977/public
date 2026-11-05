package com.akvalentine.bigalarm.core

import java.util.Locale

/** How alarm names are shown: abbreviations first, then optional capitals. Pure Kotlin, unit-tested. */
object NameFormat {

    /** Whole-word, case-insensitive replacements applied before display. Longer forms first. */
    val abbreviations: List<Pair<String, String>> = listOf(
        "appointments" to "appts",
        "appointment" to "appt",
    )

    private val patterns: List<Pair<Regex, String>> =
        abbreviations.map { (word, short) -> Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE) to short }

    fun display(raw: String, uppercase: Boolean, locale: Locale = Locale.getDefault()): String {
        var text = raw.trim().ifEmpty { "Alarm" }
        for ((regex, short) in patterns) {
            text = regex.replace(text) { match ->
                // Keep a leading capital ("Appointment" -> "Appt") when not forcing capitals anyway.
                if (match.value.first().isUpperCase()) short.replaceFirstChar { it.titlecase(locale) } else short
            }
        }
        return if (uppercase) text.uppercase(locale) else text
    }
}
