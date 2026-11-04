package com.akvalentine.bigalarm.alarm

import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.data.AlarmRepository

/** Every write to the alarm table goes through here so the system alarms always match the database. */
class AlarmActions(private val repository: AlarmRepository, private val scheduler: AlarmScheduler) {

    suspend fun save(alarm: Alarm): Long {
        // Editing an alarm clears any pending snooze; the new settings win.
        val id = repository.upsert(alarm.copy(snoozedUntil = null))
        scheduler.rescheduleAll()
        return id
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
        scheduler.rescheduleAll()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        repository.setEnabled(id, enabled)
        scheduler.rescheduleAll()
    }

    suspend fun snooze(id: Long, minutes: Int) {
        repository.setSnoozed(id, System.currentTimeMillis() + minutes * 60_000L)
        scheduler.rescheduleAll()
    }
}
