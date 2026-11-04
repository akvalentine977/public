package com.akvalentine.bigalarm.data

import android.content.Context
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

    fun setTextScale(scale: Float) {
        _textScale.value = scale
        prefs.edit().putFloat(KEY_TEXT_SCALE, scale).apply()
    }

    fun setSnoozeMinutes(minutes: Int) {
        _snoozeMinutes.value = minutes
        prefs.edit().putInt(KEY_SNOOZE, minutes).apply()
    }

    fun setAutoSilenceMinutes(minutes: Int) {
        _autoSilenceMinutes.value = minutes
        prefs.edit().putInt(KEY_AUTO_SILENCE, minutes).apply()
    }

    fun setDefaultVibrate(value: Boolean) {
        _defaultVibrate.value = value
        prefs.edit().putBoolean(KEY_DEFAULT_VIBRATE, value).apply()
    }

    fun setDefaultSound(value: Boolean) {
        _defaultSound.value = value
        prefs.edit().putBoolean(KEY_DEFAULT_SOUND, value).apply()
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
    }
}
