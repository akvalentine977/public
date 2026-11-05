package com.akvalentine.bigalarm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.core.Repeat
import com.akvalentine.bigalarm.core.RepeatType

/** Room row for an alarm. Kept separate from the pure-Kotlin [Alarm] so the core stays testable. */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val repeatType: Int,
    val daysMask: Int,
    val intervalDays: Int,
    val anchorEpochDay: Long?,
    val vibrate: Boolean,
    val sound: Boolean,
    val snoozedUntil: Long?,
    val createdAt: Long,
    // Added in schema version 2 (see AlarmDatabase.MIGRATION_1_2).
    val snoozeMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val ringUntilDismissed: Boolean = false,
) {
    fun toAlarm(): Alarm = Alarm(
        id = id,
        name = name,
        hour = hour,
        minute = minute,
        enabled = enabled,
        repeat = Repeat(
            type = RepeatType.entries.getOrElse(repeatType) { RepeatType.ONCE },
            daysMask = daysMask,
            intervalDays = intervalDays,
            anchorEpochDay = anchorEpochDay,
        ),
        vibrate = vibrate,
        sound = sound,
        snoozedUntil = snoozedUntil,
        createdAt = createdAt,
        snoozeMinutes = snoozeMinutes,
        ringUntilDismissed = ringUntilDismissed,
    )

    companion object {
        fun from(alarm: Alarm): AlarmEntity = AlarmEntity(
            id = alarm.id,
            name = alarm.name,
            hour = alarm.hour,
            minute = alarm.minute,
            enabled = alarm.enabled,
            repeatType = alarm.repeat.type.ordinal,
            daysMask = alarm.repeat.daysMask,
            intervalDays = alarm.repeat.intervalDays,
            anchorEpochDay = alarm.repeat.anchorEpochDay,
            vibrate = alarm.vibrate,
            sound = alarm.sound,
            snoozedUntil = alarm.snoozedUntil,
            createdAt = alarm.createdAt,
            snoozeMinutes = alarm.snoozeMinutes,
            ringUntilDismissed = alarm.ringUntilDismissed,
        )
    }
}
