package com.akvalentine.bigalarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* status shown in Settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            BigAlarmTheme {
                AppScaffold {
                    BigAlarmNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cheap self-healing: make sure the system alarms always match the database.
        activityScope.launch { appGraph.scheduler.rescheduleAll() }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

object Routes {
    const val ALARMS = "alarms"
    const val EDIT = "edit"
    const val EDIT_NAME = "edit/name"
    const val EDIT_TIME = "edit/time"
    const val EDIT_SNOOZE = "edit/snooze"
    const val EDIT_REPEAT = "edit/repeat"
    const val EDIT_REPEAT_DAYS = "edit/repeat/days"
    const val EDIT_REPEAT_INTERVAL = "edit/repeat/interval"
    const val EDIT_REPEAT_ANCHOR = "edit/repeat/anchor"
    const val EDIT_ONCE_DATE = "edit/once/date"
    const val SETTINGS = "settings"
    const val SETTINGS_TEXT = "settings/text"
    const val SETTINGS_SNOOZE = "settings/snooze"
    const val SETTINGS_SILENCE = "settings/silence"
    const val SETTINGS_VOICE_TEST = "settings/voice-test"
}

@Composable
fun BigAlarmNavHost() {
    val navController = rememberSwipeDismissableNavController()
    val graph = LocalContext.current.appGraph
    val scope = rememberCoroutineScope()

    SwipeDismissableNavHost(navController = navController, startDestination = Routes.ALARMS) {
        composable(Routes.ALARMS) {
            AlarmListScreen(
                onAdd = {
                    val start = LocalTime.now().plusHours(1).withSecond(0)
                    EditDraft.start(
                        Alarm(
                            hour = start.hour,
                            minute = (start.minute / 5) * 5,
                            vibrate = graph.settings.defaultVibrate.value,
                            sound = graph.settings.defaultSound.value,
                            ringUntilDismissed = graph.settings.defaultRingUntilDismissed.value,
                        ),
                    )
                    navController.navigate(Routes.EDIT)
                },
                onEdit = { id ->
                    scope.launch {
                        graph.repository.get(id)?.let { alarm ->
                            EditDraft.start(alarm)
                            navController.navigate(Routes.EDIT)
                        }
                    }
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EDIT) {
            EditAlarmScreen(
                onName = { navController.navigate(Routes.EDIT_NAME) },
                onTime = { navController.navigate(Routes.EDIT_TIME) },
                onRepeat = { navController.navigate(Routes.EDIT_REPEAT) },
                onSnooze = { navController.navigate(Routes.EDIT_SNOOZE) },
                onDone = { navController.popBackStack(Routes.ALARMS, inclusive = false) },
            )
        }
        composable(Routes.EDIT_SNOOZE) { SnoozeScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.EDIT_NAME) { NameScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.EDIT_TIME) { TimeScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.EDIT_REPEAT) {
            RepeatScreen(
                onDays = { navController.navigate(Routes.EDIT_REPEAT_DAYS) },
                onInterval = { navController.navigate(Routes.EDIT_REPEAT_INTERVAL) },
                onOnceDate = { navController.navigate(Routes.EDIT_ONCE_DATE) },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.EDIT_REPEAT_DAYS) { WeekdaysScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.EDIT_REPEAT_INTERVAL) {
            IntervalScreen(
                onAnchor = { navController.navigate(Routes.EDIT_REPEAT_ANCHOR) },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.EDIT_REPEAT_ANCHOR) { AnchorDateScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.EDIT_ONCE_DATE) { OnceDateScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onTextSize = { navController.navigate(Routes.SETTINGS_TEXT) },
                onSnooze = { navController.navigate(Routes.SETTINGS_SNOOZE) },
                onAutoSilence = { navController.navigate(Routes.SETTINGS_SILENCE) },
                onVoiceTest = { navController.navigate(Routes.SETTINGS_VOICE_TEST) },
            )
        }
        composable(Routes.SETTINGS_VOICE_TEST) { VoiceTestScreen() }
        composable(Routes.SETTINGS_TEXT) { TextSizeScreen() }
        composable(Routes.SETTINGS_SNOOZE) { SnoozeMinutesScreen() }
        composable(Routes.SETTINGS_SILENCE) { AutoSilenceScreen() }
    }
}
