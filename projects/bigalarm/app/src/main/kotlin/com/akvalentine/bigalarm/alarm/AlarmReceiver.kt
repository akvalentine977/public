package com.akvalentine.bigalarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.akvalentine.bigalarm.BigAlarmApp
import com.akvalentine.bigalarm.R
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.ui.AlarmRingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for AlarmManager (ACTION_FIRE) and for system events after which every alarm must be
 * re-registered: reboot, clock or time-zone change, app update.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> fire(context, intent.getLongExtra(EXTRA_ALARM_ID, -1L))

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> rescheduleAll(context)
        }
    }

    private fun fire(context: Context, alarmId: Long) {
        if (alarmId < 0) return
        val serviceIntent = Intent(context, AlarmRingService::class.java)
            .setAction(AlarmRingService.ACTION_START)
            .putExtra(EXTRA_ALARM_ID, alarmId)
        try {
            // Allowed from the background because this broadcast comes from an exact alarm.
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start ringing service, posting plain notification", e)
            postFallbackNotification(context, alarmId)
        }
    }

    /** Last resort if the foreground service cannot be started: at least surface a notification. */
    private fun postFallbackNotification(context: Context, alarmId: Long) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, AlarmRingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, BigAlarmApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Alarm")
            .setContentText("Tap to open")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(alarmId.toInt(), notification)
    }

    private fun rescheduleAll(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                context.appGraph.scheduler.rescheduleAll()
            } catch (e: Exception) {
                Log.e(TAG, "Reschedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.akvalentine.bigalarm.action.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val TAG = "AlarmReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
