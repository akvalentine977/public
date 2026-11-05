package com.akvalentine.bigalarm.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SplitSwitchButton
import androidx.wear.compose.material3.Text
import com.akvalentine.bigalarm.alarm.RingingState
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.core.Format
import kotlinx.coroutines.launch

@Composable
fun AlarmListScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val graph = context.appGraph
    val scope = rememberCoroutineScope()
    val alarms by graph.repository.observeAll().collectAsState(initial = emptyList())
    val ringing by RingingState.current.collectAsState()
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onAdd) { Text("+ New alarm", fontSize = 18.sp) }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Alarms") } }

            ringing?.let { active ->
                item {
                    Button(
                        onClick = { context.startActivity(Intent(context, AlarmRingActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ringing: ${alarmTitle(active)}", fontWeight = FontWeight.Bold) },
                    )
                }
            }

            if (alarms.isEmpty()) {
                item {
                    Text(
                        "No alarms yet.\nTap + New alarm below.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    is24h = is24h,
                    onToggle = { enabled -> scope.launch { graph.actions.setEnabled(alarm.id, enabled) } },
                    onClick = { onEdit(alarm.id) },
                )
            }

            item {
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Settings") },
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, is24h: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val nameSize = NameText.listSize()
    val subtitle = buildString {
        append(Format.time(alarm.hour, alarm.minute, is24h))
        append(" · ")
        append(Format.repeat(alarm.repeat))
        if (alarm.snoozedUntil != null && alarm.snoozedUntil > System.currentTimeMillis()) append(" · snoozed")
    }
    SplitSwitchButton(
        checked = alarm.enabled,
        onCheckedChange = onToggle,
        toggleContentDescription = "Alarm enabled",
        onContainerClick = onClick,
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
        secondaryLabel = {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        },
    )
}
