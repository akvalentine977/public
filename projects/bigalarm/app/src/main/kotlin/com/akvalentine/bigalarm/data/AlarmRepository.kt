package com.akvalentine.bigalarm.data

import com.akvalentine.bigalarm.core.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thin mapping layer between Room and the pure-Kotlin [Alarm] model. No limit on the number of alarms. */
class AlarmRepository(private val dao: AlarmDao) {

    fun observeAll(): Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

    fun observeNames(): Flow<List<String>> = dao.observeNames()

    suspend fun getAll(): List<Alarm> = dao.getAll().map { it.toAlarm() }

    suspend fun get(id: Long): Alarm? = dao.get(id)?.toAlarm()

    /** Inserts or updates; returns the id (new rows get a generated id). */
    suspend fun upsert(alarm: Alarm): Long {
        val stamped = if (alarm.createdAt == 0L) alarm.copy(createdAt = System.currentTimeMillis()) else alarm
        val rowId = dao.upsert(AlarmEntity.from(stamped))
        return if (alarm.id == 0L) rowId else alarm.id
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun setSnoozed(id: Long, untilEpochMillis: Long) = dao.setSnoozed(id, untilEpochMillis)
}
