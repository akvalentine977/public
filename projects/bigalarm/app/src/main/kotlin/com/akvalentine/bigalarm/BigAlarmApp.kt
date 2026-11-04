package com.akvalentine.bigalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.akvalentine.bigalarm.alarm.AlarmActions
import com.akvalentine.bigalarm.alarm.AlarmScheduler
import com.akvalentine.bigalarm.data.AlarmDatabase
import com.akvalentine.bigalarm.data.AlarmRepository
import com.akvalentine.bigalarm.data.Settings

/** Hand-rolled dependency graph; small enough that a DI framework would be overkill. */
class AppGraph(context: Context) {
    val settings = Settings(context)
    val database = AlarmDatabase.build(context)
    val repository = AlarmRepository(database.alarmDao())
    val scheduler = AlarmScheduler(context, repository)
    val actions = AlarmActions(repository, scheduler)
}

class BigAlarmApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        // The ringing service does its own vibration and sound, so the channel itself is silent.
        val ringing = NotificationChannel(CHANNEL_RINGING, "Ringing alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while an alarm is ringing"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(CHANNEL_MISSED, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "An alarm rang until it timed out"
        }
        manager.createNotificationChannel(ringing)
        manager.createNotificationChannel(missed)
    }

    companion object {
        const val CHANNEL_RINGING = "ringing"
        const val CHANNEL_MISSED = "missed"
    }
}

val Context.appGraph: AppGraph
    get() = (applicationContext as BigAlarmApp).graph
