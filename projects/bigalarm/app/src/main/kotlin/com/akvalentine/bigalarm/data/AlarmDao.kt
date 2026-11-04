package com.akvalentine.bigalarm.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, name")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute, name")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Upsert
    suspend fun upsert(entity: AlarmEntity): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled, snoozedUntil = NULL WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET enabled = 1, snoozedUntil = :until WHERE id = :id")
    suspend fun setSnoozed(id: Long, until: Long)

    @Query("SELECT DISTINCT name FROM alarms WHERE name <> '' ORDER BY name")
    fun observeNames(): Flow<List<String>>
}
