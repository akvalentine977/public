package com.akvalentine.bigalarm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.data.Settings

@Composable
fun SettingsScreen(onTextSize: () -> Unit, onSnooze: () -> Unit, onAutoSilence: () -> Unit) {
    val context = LocalContext.current
    val settings = context.appGraph.settings
    val scale by settings.textScale.collectAsState()
    val snooze by settings.snoozeMinutes.collectAsState()
    val silence by settings.autoSilenceMinutes.collectAsState()
    val defaultVibrate by settings.defaultVibrate.collectAsState()
    val defaultSound by settings.defaultSound.collectAsState()
    val listState = rememberTransformingLazyColumnState()

    val notificationsAllowed =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val popupAllowed = AndroidSettings.canDrawOverlays(context)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text("Settings") } }
            item {
                Button(
                    onClick = onTextSize,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Text size") },
                    secondaryLabel = { Text("${scale}x") },
                )
            }
            item {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Snooze length") },
                    secondaryLabel = { Text("$snooze min") },
                )
            }
            item {
                Button(
                    onClick = onAutoSilence,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ring for at most") },
                    secondaryLabel = { Text("$silence min, then marked missed") },
                )
            }
            item { ListHeader { Text("New alarm defaults") } }
            item {
                SwitchButton(
                    checked = defaultVibrate,
                    onCheckedChange = settings::setDefaultVibrate,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Vibrate") },
                )
            }
            item {
                SwitchButton(
                    checked = defaultSound,
                    onCheckedChange = settings::setDefaultSound,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sound") },
                )
            }
            item { ListHeader { Text("Status") } }
            item {
                Button(
                    onClick = {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notifications") },
                    secondaryLabel = { Text(if (notificationsAllowed) "Allowed" else "Blocked: tap to fix") },
                )
            }
            item {
                Text(
                    if (popupAllowed) "Ringing screen pops up by itself: on"
                    else "Ringing screen pops up by itself: off (see README for the adb command)",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun TextSizeScreen() {
    val settings = LocalContext.current.appGraph.settings
    val scale by settings.textScale.collectAsState()
    val scales = Settings.TEXT_SCALES
    val index = scales.indexOfFirst { it >= scale }.let { if (it < 0) scales.lastIndex else it }
    val previewSize = NameText.listBase * scale

    Stepper(
        value = index,
        onValueChange = { i -> settings.setTextScale(scales[i.coerceIn(0, scales.lastIndex)]) },
        valueProgression = 0..scales.lastIndex,
        decreaseIcon = { Text("A", fontSize = 14.sp) },
        increaseIcon = { Text("A", fontSize = 26.sp, fontWeight = FontWeight.Bold) },
    ) {
        Text(
            "Take meds",
            fontSize = previewSize,
            lineHeight = previewSize * 1.2f,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
        )
    }
}

@Composable
fun SnoozeMinutesScreen() {
    val settings = LocalContext.current.appGraph.settings
    val value by settings.snoozeMinutes.collectAsState()
    MinutesStepperScreen(title = "Snooze", value = value, range = 1..60, onChange = settings::setSnoozeMinutes)
}

@Composable
fun AutoSilenceScreen() {
    val settings = LocalContext.current.appGraph.settings
    val value by settings.autoSilenceMinutes.collectAsState()
    MinutesStepperScreen(title = "Ring for", value = value, range = 1..30, onChange = settings::setAutoSilenceMinutes)
}
