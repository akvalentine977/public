package com.akvalentine.bigalarm.ui

import com.akvalentine.bigalarm.core.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The alarm currently being edited. Sub-screens (time, repeat, name) modify this draft and pop back;
 * nothing is written to the database until the user taps Save.
 */
object EditDraft {
    private val _alarm = MutableStateFlow<Alarm?>(null)
    val alarm: StateFlow<Alarm?> = _alarm

    fun start(alarm: Alarm) {
        _alarm.value = alarm
    }

    fun update(transform: (Alarm) -> Alarm) {
        _alarm.value = _alarm.value?.let(transform)
    }

    fun clear() {
        _alarm.value = null
    }
}
