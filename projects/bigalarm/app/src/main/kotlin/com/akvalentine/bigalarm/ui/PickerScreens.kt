package com.akvalentine.bigalarm.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.DatePicker
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.input.RemoteInputIntentHelper
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Format
import com.akvalentine.bigalarm.core.Repeat
import com.akvalentine.bigalarm.core.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

private const val REMOTE_INPUT_KEY = "alarm_name"

/** Name entry: the system keyboard / voice input, plus one-tap reuse of names already in use. */
@Composable
fun NameScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val names by context.appGraph.repository.observeNames().collectAsState(initial = emptyList())
    val draft by EditDraft.alarm.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val nameSize = NameText.listSize()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val typed = result.data?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(REMOTE_INPUT_KEY)?.toString()?.trim()
        if (!typed.isNullOrEmpty()) {
            EditDraft.update { it.copy(name = typed) }
            onDone()
        }
    }

    fun openInput() {
        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel("Alarm name").build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        RemoteInputIntentHelper.putTitleExtra(intent, "Alarm name")
        launcher.launch(intent)
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Name") } }
            item {
                draft?.let { alarm ->
                    Text(
                        alarmTitle(alarm),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        fontSize = nameSize,
                        lineHeight = nameSize * 1.2f,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 6,
                    )
                }
            }
            item {
                Button(
                    onClick = { openInput() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type or speak") },
                )
            }
            if (names.isNotEmpty()) {
                item { ListHeader { Text("Reuse a name") } }
                items(names, key = { it }) { name ->
                    FilledTonalButton(
                        onClick = {
                            EditDraft.update { it.copy(name = name) }
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(nameForDisplay(name), maxLines = 3) },
                    )
                }
            }
        }
    }
}

@Composable
fun TimeScreen(onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    // Default picker type follows the watch's 12/24-hour setting.
    TimePicker(
        initialTime = alarm.time,
        onTimePicked = { picked ->
            EditDraft.update { it.copy(hour = picked.hour, minute = picked.minute) }
            onDone()
        },
    )
}

@Composable
fun RepeatScreen(onDays: () -> Unit, onInterval: () -> Unit, onOnceDate: () -> Unit, onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val repeat = alarm.repeat
    val listState = rememberTransformingLazyColumnState()

    fun select(type: RepeatType, anchor: Long? = repeat.anchorEpochDay) {
        EditDraft.update { it.copy(repeat = it.repeat.copy(type = type, anchorEpochDay = anchor)) }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Repeat") } }
            item {
                RadioButton(
                    selected = repeat.type == RepeatType.ONCE && repeat.anchorEpochDay == null,
                    onSelect = { select(RepeatType.ONCE, anchor = null) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Once") },
                    secondaryLabel = { Text("Next time it comes round") },
                )
            }
            item {
                RadioButton(
                    selected = repeat.type == RepeatType.ONCE && repeat.anchorEpochDay != null,
                    onSelect = {
                        select(RepeatType.ONCE, anchor = repeat.anchorEpochDay ?: LocalDate.now().toEpochDay())
                        onOnceDate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Once, on a date") },
                    secondaryLabel = {
                        Text(
                            if (repeat.type == RepeatType.ONCE && repeat.anchorEpochDay != null) Format.date(repeat.anchorEpochDay) else "Pick a day",
                        )
                    },
                )
            }
            item {
                RadioButton(
                    selected = repeat.type == RepeatType.DAILY,
                    onSelect = { select(RepeatType.DAILY) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Every day") },
                )
            }
            item {
                RadioButton(
                    selected = repeat.type == RepeatType.WEEKLY,
                    onSelect = {
                        EditDraft.update {
                            val mask = if (it.repeat.daysMask == 0) Repeat.MON_TO_FRI else it.repeat.daysMask
                            it.copy(repeat = it.repeat.copy(type = RepeatType.WEEKLY, daysMask = mask))
                        }
                        onDays()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Days of the week") },
                    secondaryLabel = {
                        if (repeat.type == RepeatType.WEEKLY) Text(Format.repeat(repeat)) else Text("Choose days")
                    },
                )
            }
            item {
                RadioButton(
                    selected = repeat.type == RepeatType.EVERY_N_DAYS,
                    onSelect = {
                        select(RepeatType.EVERY_N_DAYS, anchor = repeat.anchorEpochDay ?: LocalDate.now().toEpochDay())
                        onInterval()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Every N days") },
                    secondaryLabel = {
                        if (repeat.type == RepeatType.EVERY_N_DAYS) Text(Format.repeat(repeat)) else Text("Pick an interval")
                    },
                )
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Done") })
            }
        }
    }
}

@Composable
fun WeekdaysScreen(onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val listState = rememberTransformingLazyColumnState()
    val locale = LocalConfiguration.current.locales[0]

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Days") } }
            items(DayOfWeek.entries, key = { it.value }) { day ->
                CheckboxButton(
                    checked = alarm.repeat.has(day),
                    onCheckedChange = { EditDraft.update { it.copy(repeat = it.repeat.toggled(day)) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(day.getDisplayName(TextStyle.FULL, locale)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { EditDraft.update { it.copy(repeat = it.repeat.copy(daysMask = Repeat.MON_TO_FRI)) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Weekdays") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { EditDraft.update { it.copy(repeat = it.repeat.copy(daysMask = Repeat.ALL_DAYS)) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("All days") },
                )
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Done") })
            }
        }
    }
}

@Composable
fun IntervalScreen(onAnchor: () -> Unit, onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val listState = rememberTransformingLazyColumnState()
    val anchor = alarm.repeat.anchorEpochDay ?: LocalDate.now().toEpochDay()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Interval") } }
            item {
                Stepper(
                    value = alarm.repeat.intervalDays.coerceIn(1, 90),
                    onValueChange = { n -> EditDraft.update { it.copy(repeat = it.repeat.copy(intervalDays = n)) } },
                    valueProgression = 1..90,
                    decreaseIcon = { Text("−", fontSize = 24.sp) },
                    increaseIcon = { Text("+", fontSize = 24.sp) },
                ) {
                    Text("Every ${alarm.repeat.intervalDays} days", fontSize = 20.sp)
                }
            }
            item {
                Button(
                    onClick = onAnchor,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(Format.date(anchor)) },
                    secondaryLabel = { Text("Starting on") },
                )
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Done") })
            }
        }
    }
}

/** Start date for "every N days". */
@Composable
fun AnchorDateScreen(onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    DatePicker(
        initialDate = alarm.repeat.anchorEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
        onDatePicked = { date ->
            EditDraft.update { it.copy(repeat = it.repeat.copy(anchorEpochDay = date.toEpochDay())) }
            onDone()
        },
    )
}

/** Specific date for a one-off alarm. */
@Composable
fun OnceDateScreen(onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val today = LocalDate.now()
    DatePicker(
        initialDate = alarm.repeat.anchorEpochDay?.let(LocalDate::ofEpochDay)?.takeIf { !it.isBefore(today) } ?: today,
        onDatePicked = { date ->
            EditDraft.update { it.copy(repeat = it.repeat.copy(type = RepeatType.ONCE, anchorEpochDay = date.toEpochDay())) }
            onDone()
        },
        minValidDate = today,
    )
}

/** Per-alarm snooze length; null means "use the app default". */
@Composable
fun SnoozeScreen(onDone: () -> Unit) {
    val draft by EditDraft.alarm.collectAsState()
    val defaultSnooze by LocalContext.current.appGraph.settings.snoozeMinutes.collectAsState()
    val alarm = draft ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val listState = rememberTransformingLazyColumnState()
    val choices: List<Int?> = listOf(null, 1, 3, 5, 10, 15, 20, 30, 45, 60)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Snooze") } }
            items(choices, key = { it ?: 0 }) { minutes ->
                RadioButton(
                    selected = alarm.snoozeMinutes == minutes,
                    onSelect = {
                        EditDraft.update { it.copy(snoozeMinutes = minutes) }
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(minutes?.let { "$it min" } ?: "App default ($defaultSnooze min)") },
                )
            }
        }
    }
}

/** Shared "pick a number of minutes" screen for snooze and auto-silence settings. */
@Composable
fun MinutesStepperScreen(title: String, value: Int, range: IntProgression, onChange: (Int) -> Unit) {
    Stepper(
        value = value.coerceIn(range.first, range.last),
        onValueChange = onChange,
        valueProgression = range,
        decreaseIcon = { Text("−", fontSize = 24.sp) },
        increaseIcon = { Text("+", fontSize = 24.sp) },
    ) {
        Text("$title\n$value min", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
