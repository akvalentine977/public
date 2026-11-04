package com.akvalentine.bigalarm.alarm

import android.app.AlarmManager
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.akvalentine.bigalarm.core.RepeatType
import com.akvalentine.bigalarm.core.Schedule
import com.akvalentine.bigalarm.data.AlarmRepository
import com.akvalentine.bigalarm.ui.MainActivity
import java.time.ZonedDateTime

/**
 * Registers every enabled alarm with [AlarmManager.setAlarmClock], which Android treats as the
 * highest-priority kind of alarm: delivery is never shifted and low-power modes are exited for it.
 */
class AlarmScheduler(private val context: Context, private val repository: AlarmRepository) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** Recomputes and re-registers all alarms. Safe to call often; it is idempotent. */
    suspend fun rescheduleAll() {
        val now = ZonedDateTime.now()
        for (alarm in repository.getAll()) {
            val next = Schedule.nextTrigger(alarm, now)
            if (next == null) {
                cancel(alarm.id)
                // A one-off whose date has passed (watch was off, etc.) is switched off so the list is honest.
                if (alarm.enabled && alarm.repeat.type == RepeatType.ONCE) {
                    repository.setEnabled(alarm.id, false)
                }
            } else {
                set(alarm.id, next)
            }
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(fireIntent(alarmId))
    }

    // Lint only knows SCHEDULE_EXACT_ALARM; USE_EXACT_ALARM (declared) grants the same capability on API 33+.
    @SuppressLint("MissingPermission")
    private fun set(alarmId: Long, at: ZonedDateTime) {
        val triggerMillis = at.toInstant().toEpochMilli()
        val operation = fireIntent(alarmId)
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showIntent), operation)
        } else {
            // Should not happen with USE_EXACT_ALARM, but never silently drop an alarm.
            Log.w(TAG, "Exact alarms not permitted; falling back to inexact scheduling")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
        }
        Log.i(TAG, "Alarm $alarmId scheduled for $at")
    }

    private fun fireIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        // The request code makes each alarm's PendingIntent distinct (extras are not compared).
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
