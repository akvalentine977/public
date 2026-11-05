package com.akvalentine.bigalarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.akvalentine.bigalarm.alarm.AlarmRingService
import com.akvalentine.bigalarm.alarm.RingingState
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.core.Format
import com.akvalentine.bigalarm.core.VoiceCommands

/** The screen shown while an alarm rings: the name as big as the user wants it, Dismiss, Snooze. */
class AlarmRingActivity : ComponentActivity() {

    private var voice: VoiceCommandListener? = null
    private var voiceStatus by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            BigAlarmTheme {
                val alarm by RingingState.current.collectAsState()
                val queued by RingingState.queuedCount.collectAsState()
                val active = alarm
                if (active == null) {
                    // Opened after the alarm already stopped (or dismissed from the notification).
                    LaunchedEffect(Unit) { finish() }
                } else {
                    RingScreen(
                        alarm = active,
                        queued = queued,
                        voiceStatus = voiceStatus,
                        onDismiss = { AlarmRingService.dismiss(this) },
                        onSnooze = { AlarmRingService.snooze(this) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startVoiceControlIfEnabled()
    }

    override fun onPause() {
        voice?.stop()
        voice = null
        super.onPause()
    }

    private fun startVoiceControlIfEnabled() {
        val settings = appGraph.settings
        if (!settings.voiceControl.value) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceStatus = "Voice control: microphone permission missing"
            return
        }
        if (RingingState.current.value == null) return
        voice = VoiceCommandListener(
            context = this,
            onCommand = { command ->
                when (command) {
                    VoiceCommands.Command.DISMISS -> AlarmRingService.dismiss(this)
                    VoiceCommands.Command.SNOOZE -> AlarmRingService.snooze(this)
                }
            },
            onStatus = { voiceStatus = it },
        ).also { it.start() }
    }
}

@Composable
fun RingScreen(alarm: Alarm, queued: Int, voiceStatus: String?, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    val defaultSnooze by context.appGraph.settings.snoozeMinutes.collectAsState()
    val snoozeMinutes = alarm.snoozeMinutes ?: defaultSnooze
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val scrollState = rememberScrollState()
    val nameSize = NameText.ringSize()

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Format.time(alarm.hour, alarm.minute, is24h),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                alarmTitle(alarm),
                fontSize = nameSize,
                lineHeight = nameSize * 1.15f,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            if (queued > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "+$queued more waiting",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                label = { Text("Dismiss", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                label = { Text("Snooze $snoozeMinutes min", fontSize = 18.sp) },
            )
            if (voiceStatus != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    voiceStatus,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
