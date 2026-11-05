package com.akvalentine.bigalarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.core.Alarm
import com.akvalentine.bigalarm.core.NameFormat
import com.akvalentine.bigalarm.data.Settings

/** User-chosen multiplier for alarm-name text, on top of the system font scale. */
val LocalTextScale = compositionLocalOf { Settings.DEFAULT_TEXT_SCALE }

/** Whether alarm names are shown in capitals. */
val LocalUppercaseNames = compositionLocalOf { true }

/** Yellow on black: high contrast and easy on tired eyes. Surfaces keep the Material defaults. */
val YellowColorScheme = ColorScheme(
    primary = Color(0xFFF5F540),
    primaryDim = Color(0xFFD4D42A),
    primaryContainer = Color(0xFF50500A),
    onPrimary = Color(0xFF1A1A00),
    onPrimaryContainer = Color(0xFFF5F540),
    secondary = Color(0xFFE6D08A),
    secondaryDim = Color(0xFFC9B46E),
    secondaryContainer = Color(0xFF4A4120),
    onSecondary = Color(0xFF1F1A00),
    onSecondaryContainer = Color(0xFFFFEDB8),
    tertiary = Color(0xFFFFB84D),
    tertiaryDim = Color(0xFFE09E2E),
    tertiaryContainer = Color(0xFF5C3D00),
    onTertiary = Color(0xFF221400),
    onTertiaryContainer = Color(0xFFFFDDB0),
)

@Composable
fun BigAlarmTheme(content: @Composable () -> Unit) {
    val settings = LocalContext.current.appGraph.settings
    val scale by settings.textScale.collectAsState()
    val uppercase by settings.uppercaseNames.collectAsState()
    CompositionLocalProvider(LocalTextScale provides scale, LocalUppercaseNames provides uppercase) {
        MaterialTheme(colorScheme = YellowColorScheme, content = content)
    }
}

/** The alarm's name as it should be displayed on screen. */
@Composable
fun alarmTitle(alarm: Alarm): String = nameForDisplay(alarm.name)

@Composable
fun nameForDisplay(raw: String): String =
    NameFormat.display(raw, LocalUppercaseNames.current, LocalConfiguration.current.locales[0])

/** Font sizes used for alarm names; everything scales from these. */
object NameText {
    val listBase = 18.sp
    val ringBase = 30.sp

    @Composable
    fun listSize(): TextUnit = listBase * LocalTextScale.current

    @Composable
    fun ringSize(): TextUnit = ringBase * LocalTextScale.current
}
