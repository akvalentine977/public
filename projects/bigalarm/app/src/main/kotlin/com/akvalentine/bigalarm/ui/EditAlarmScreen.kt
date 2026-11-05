package com.akvalentine.bigalarm.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Format
import com.akvalentine.bigalarm.core.Schedule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

@Composable
fun EditAlarmScreen(
    onName: () -> Unit,
    onTime: () -> Unit,
    onRepeat: () -> Unit,
    onSnooze: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val graph = context.appGraph
    val scope = rememberCoroutineScope()
    val draft by EditDraft.alarm.collectAsState()
    val defaultSnooze by graph.settings.snoozeMinutes.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val listState = rememberTransformingLazyColumnState()
    var confirmDelete by remember { mutableStateOf(false) }
    val nameSize = NameText.listSize()

    // "Tap again to delete" resets after a few seconds.
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4000)
            confirmDelete = false
        }
    }

    val nextRing = remember(alarm) {
        Schedule.nextTrigger(alarm.copy(enabled = true, snoozedUntil = null), ZonedDateTime.now())
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = {
                scope.launch {
                    graph.actions.save(alarm)
                    EditDraft.clear()
                    onDone()
                }
            }) { Text("Save", fontSize = 18.sp) }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(if (alarm.id == 0L) "New alarm" else "Edit alarm") } }

            item {
                Button(
                    onClick = onName,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            alarmTitle(alarm),
                            fontSize = nameSize,
                            lineHeight = nameSize * 1.2f,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 6,
                        )
                    },
                    secondaryLabel = { Text("Name") },
                )
            }

            item {
                Button(
                    onClick = onTime,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(Format.time(alarm.hour, alarm.minute, is24h), fontSize = 24.sp) },
                    secondaryLabel = { Text("Time") },
                )
            }

            item {
                Button(
                    onClick = onRepeat,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(Format.repeat(alarm.repeat), maxLines = 3) },
                    secondaryLabel = { Text("Repeat") },
                )
            }

            item {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(alarm.snoozeMinutes?.let { "$it min" } ?: "Default ($defaultSnooze min)") },
                    secondaryLabel = { Text("Snooze") },
                )
            }

            item {
                SwitchButton(
                    checked = alarm.ringUntilDismissed,
                    onCheckedChange = { on -> EditDraft.update { it.copy(ringUntilDismissed = on) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ring until dismissed") },
                    secondaryLabel = { Text("Never times out") },
                )
            }

            item {
                SwitchButton(
                    checked = alarm.vibrate,
                    onCheckedChange = { on -> EditDraft.update { it.copy(vibrate = on) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Vibrate") },
                )
            }

            item {
                SwitchButton(
                    checked = alarm.sound,
                    onCheckedChange = { on -> EditDraft.update { it.copy(sound = on) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sound") },
                )
            }

            item {
                val hint = nextRing?.let { "Next ring ${Format.untilText(it, ZonedDateTime.now())}" } ?: "Will not ring"
                Text(
                    hint,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (alarm.id != 0L) {
                item {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                graph.actions.save(alarm.copy(id = 0, createdAt = 0, enabled = true))
                                EditDraft.clear()
                                onDone()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Duplicate") },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = {
                            if (!confirmDelete) {
                                confirmDelete = true
                            } else {
                                scope.launch {
                                    graph.actions.delete(alarm.id)
                                    EditDraft.clear()
                                    onDone()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (confirmDelete) "Tap again to delete" else "Delete",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}
