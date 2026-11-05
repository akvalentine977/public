package com.akvalentine.bigalarm.data

import android.content.Context
import androidx.core.content.edit
import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.core.NameFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App settings backed by SharedPreferences, exposed as StateFlows for Compose. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _textScale = MutableStateFlow(prefs.getFloat(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE))
    /** Multiplier applied to alarm-name text on top of the system font size. */
    val textScale: StateFlow<Float> = _textScale

    private val _snoozeMinutes = MutableStateFlow(prefs.getInt(KEY_SNOOZE, DEFAULT_SNOOZE_MINUTES))
    val snoozeMinutes: StateFlow<Int> = _snoozeMinutes

    private val _autoSilenceMinutes = MutableStateFlow(prefs.getInt(KEY_AUTO_SILENCE, DEFAULT_AUTO_SILENCE_MINUTES))
    /** How long an alarm rings before it gives up and is reported as missed. */
    val autoSilenceMinutes: StateFlow<Int> = _autoSilenceMinutes

    private val _defaultVibrate = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_VIBRATE, true))
    val defaultVibrate: StateFlow<Boolean> = _defaultVibrate

    private val _defaultSound = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_SOUND, false))
    val defaultSound: StateFlow<Boolean> = _defaultSound

    private val _defaultRingUntilDismissed = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_RING_UNTIL_DISMISSED, false))
    val defaultRingUntilDismissed: StateFlow<Boolean> = _defaultRingUntilDismissed

    private val _uppercaseNames = MutableStateFlow(prefs.getBoolean(KEY_UPPERCASE_NAMES, true))
    /** Show alarm names in capital letters. */
    val uppercaseNames: StateFlow<Boolean> = _uppercaseNames

    private val _voiceControl = MutableStateFlow(prefs.getBoolean(KEY_VOICE_CONTROL, false))
    /** Listen for "stop" / "snooze" while the ringing screen is showing. Needs the microphone permission. */
    val voiceControl: StateFlow<Boolean> = _voiceControl

    /** The alarm's own snooze length, or the app default. */
    fun effectiveSnoozeMinutes(alarm: Alarm): Int = alarm.snoozeMinutes ?: snoozeMinutes.value

    /** The name as it should be shown right now, honouring abbreviations and the capitals setting. */
    fun displayName(alarm: Alarm): String = NameFormat.display(alarm.name, uppercaseNames.value)

    fun setTextScale(scale: Float) {
        _textScale.value = scale
        prefs.edit { putFloat(KEY_TEXT_SCALE, scale) }
    }

    fun setSnoozeMinutes(minutes: Int) {
        _snoozeMinutes.value = minutes
        prefs.edit { putInt(KEY_SNOOZE, minutes) }
    }

    fun setAutoSilenceMinutes(minutes: Int) {
        _autoSilenceMinutes.value = minutes
        prefs.edit { putInt(KEY_AUTO_SILENCE, minutes) }
    }

    fun setDefaultVibrate(value: Boolean) {
        _defaultVibrate.value = value
        prefs.edit { putBoolean(KEY_DEFAULT_VIBRATE, value) }
    }

    fun setDefaultSound(value: Boolean) {
        _defaultSound.value = value
        prefs.edit { putBoolean(KEY_DEFAULT_SOUND, value) }
    }

    fun setDefaultRingUntilDismissed(value: Boolean) {
        _defaultRingUntilDismissed.value = value
        prefs.edit { putBoolean(KEY_DEFAULT_RING_UNTIL_DISMISSED, value) }
    }

    fun setUppercaseNames(value: Boolean) {
        _uppercaseNames.value = value
        prefs.edit { putBoolean(KEY_UPPERCASE_NAMES, value) }
    }

    fun setVoiceControl(value: Boolean) {
        _voiceControl.value = value
        prefs.edit { putBoolean(KEY_VOICE_CONTROL, value) }
    }

    companion object {
        /** Available text-size steps; index into this with the settings stepper. */
        val TEXT_SCALES: List<Float> = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        const val DEFAULT_TEXT_SCALE = 1.5f
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_AUTO_SILENCE_MINUTES = 5

        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_SNOOZE = "snooze_minutes"
        private const val KEY_AUTO_SILENCE = "auto_silence_minutes"
        private const val KEY_DEFAULT_VIBRATE = "default_vibrate"
        private const val KEY_DEFAULT_SOUND = "default_sound"
        private const val KEY_DEFAULT_RING_UNTIL_DISMISSED = "default_ring_until_dismissed"
        private const val KEY_UPPERCASE_NAMES = "uppercase_names"
        private const val KEY_VOICE_CONTROL = "voice_control"
    }
}
