package com.akvalentine.bigalarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import com.akvalentine.bigalarm.BigAlarmApp
import com.akvalentine.bigalarm.R
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.core.Format
import com.akvalentine.bigalarm.core.Schedule
import com.akvalentine.bigalarm.ui.AlarmRingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that makes an alarm ring: vibration, optional sound, a wake lock, a
 * high-priority notification with Dismiss/Snooze actions, and the full-screen ringing activity.
 *
 * Wear OS does not honour full-screen intents, and Android blocks activity launches from the
 * background, so the activity opens automatically only when the user has granted the optional
 * overlay permission (see README). Otherwise the user taps the notification.
 */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ringer by lazy { Ringer(this, scope) }
    private val queue = ArrayDeque<Long>()
    /** Id of the alarm being started or ringing; set synchronously so concurrent starts queue up. */
    private var activeId: Long? = null
    private var current: Alarm? = null
    private var autoSilenceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> finishCurrent(snooze = false)
            ACTION_SNOOZE -> finishCurrent(snooze = true)
            ACTION_START -> {
                val id = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L) ?: -1L
                if (id >= 0) enqueue(id)
            }
            else -> if (current == null && queue.isEmpty()) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun enqueue(alarmId: Long) {
        // Every startForegroundService() must be answered by startForeground() within seconds,
        // so call it on each start; with the same id it merely refreshes the notification.
        startForegroundSafely(current?.let { buildNotification(it) } ?: buildNotification("Alarm", "Loading..."))

        if (activeId != null) {
            if (activeId != alarmId && alarmId !in queue) {
                queue.addLast(alarmId)
                publishState()
                // Persist "fired" state now so a queued alarm is never lost if the process dies.
                scope.launch { markFired(alarmId) }
            }
            return
        }
        begin(alarmId)
    }

    private fun begin(alarmId: Long) {
        activeId = alarmId
        val graph = appGraph
        scope.launch {
            val alarm = graph.repository.get(alarmId)
            if (alarm == null) {
                Log.w(TAG, "Alarm $alarmId no longer exists")
                advance()
                return@launch
            }
            markFired(alarmId)
            current = alarm
            publishState()

            acquireWakeLocks()
            notificationManager().notify(NOTIFICATION_ID, buildNotification(alarm))
            // An alarm with neither vibration nor sound would be useless, so vibrate at least.
            ringer.start(vibrate = alarm.vibrate || !alarm.sound, sound = alarm.sound)
            tryOpenRingingScreen()

            autoSilenceJob?.cancel()
            val minutes = graph.settings.autoSilenceMinutes.value.coerceIn(1, 60)
            autoSilenceJob = launch {
                delay(minutes * 60_000L)
                postMissed(alarm)
                finishCurrent(snooze = false)
            }
        }
    }

    /** Writes the post-fire state (one-offs switch off, snooze cleared) and registers the next ring. */
    private suspend fun markFired(alarmId: Long) {
        val graph = appGraph
        val alarm = graph.repository.get(alarmId) ?: return
        graph.repository.upsert(Schedule.afterFiring(alarm))
        graph.scheduler.rescheduleAll()
    }

    private fun finishCurrent(snooze: Boolean) {
        val alarm = current
        if (alarm == null) {
            // Nothing loaded yet (or already finished): just move on.
            if (activeId == null) advance()
            return
        }
        autoSilenceJob?.cancel()
        ringer.stop()
        val graph = appGraph
        scope.launch {
            if (snooze) {
                graph.actions.snooze(alarm.id, graph.settings.snoozeMinutes.value)
            }
            advance()
        }
    }

    /** Starts the next queued alarm, or shuts the service down. */
    private fun advance() {
        current = null
        activeId = null
        val next = queue.removeFirstOrNull()
        if (next != null) {
            publishState()
            begin(next)
        } else {
            publishState()
            releaseWakeLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun publishState() = RingingState.set(current, queue.size)

    private fun startForegroundSafely(notification: Notification) {
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: Exception) {
            Log.w(TAG, "systemExempted foreground type refused, trying mediaPlayback", e)
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not enter foreground; ringing anyway", e2)
            }
        }
    }

    private fun tryOpenRingingScreen() {
        val intent = Intent(this, AlarmRingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Silently blocked by the system unless the overlay permission was granted via adb.
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "Could not open ringing screen", it) }
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val is24h = DateFormat.is24HourFormat(this)
        return buildNotification(alarm.displayName, Format.time(alarm.hour, alarm.minute, is24h))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmRingActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)
        val snoozeMinutes = appGraph.settings.snoozeMinutes.value
        return Notification.Builder(this, BigAlarmApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(Notification.Action.Builder(icon, "Dismiss", servicePendingIntent(ACTION_DISMISS, 1)).build())
            .addAction(Notification.Action.Builder(icon, "Snooze $snoozeMinutes min", servicePendingIntent(ACTION_SNOOZE, 2)).build())
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AlarmRingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun postMissed(alarm: Alarm) {
        val notification = Notification.Builder(this, BigAlarmApp.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Missed: ${alarm.displayName}")
            .setContentText(Format.time(alarm.hour, alarm.minute, DateFormat.is24HourFormat(this)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(MISSED_NOTIFICATION_BASE + alarm.id.toInt(), notification)
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLocks() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BigAlarm:ring").apply {
            acquire(MAX_RING_MILLIS)
        }
        // Deprecated but still the only way for an app to turn the screen on from a service.
        runCatching {
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "BigAlarm:screen",
            ).acquire(SCREEN_ON_MILLIS)
        }
    }

    private fun releaseWakeLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        autoSilenceJob?.cancel()
        ringer.stop()
        releaseWakeLocks()
        current = null
        activeId = null
        queue.clear()
        publishState()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.akvalentine.bigalarm.action.START_RINGING"
        const val ACTION_DISMISS = "com.akvalentine.bigalarm.action.DISMISS"
        const val ACTION_SNOOZE = "com.akvalentine.bigalarm.action.SNOOZE"

        private const val TAG = "AlarmRingService"
        private const val NOTIFICATION_ID = 1
        private const val MISSED_NOTIFICATION_BASE = 1000
        private const val MAX_RING_MILLIS = 61 * 60_000L
        private const val SCREEN_ON_MILLIS = 15_000L

        fun dismiss(context: Context) = context.startService(
            Intent(context, AlarmRingService::class.java).setAction(ACTION_DISMISS),
        )

        fun snooze(context: Context) = context.startService(
            Intent(context, AlarmRingService::class.java).setAction(ACTION_SNOOZE),
        )
    }
}
