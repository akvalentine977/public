package com.akvalentine.bigalarm.alarm

import com.akvalentine.bigalarm.core.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-process view of what is ringing right now, shared between the service and the UI. */
object RingingState {
    private val _current = MutableStateFlow<Alarm?>(null)
    val current: StateFlow<Alarm?> = _current

    private val _queuedCount = MutableStateFlow(0)
    /** Alarms that fired while another one was already ringing and are waiting their turn. */
    val queuedCount: StateFlow<Int> = _queuedCount

    internal fun set(alarm: Alarm?, queued: Int) {
        _current.value = alarm
        _queuedCount.value = queued
    }
}
