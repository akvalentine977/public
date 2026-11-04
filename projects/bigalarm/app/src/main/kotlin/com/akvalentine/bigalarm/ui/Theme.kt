package com.akvalentine.bigalarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import com.akvalentine.bigalarm.appGraph
import com.akvalentine.bigalarm.data.Settings

/** User-chosen multiplier for alarm-name text, on top of the system font scale. */
val LocalTextScale = compositionLocalOf { Settings.DEFAULT_TEXT_SCALE }

@Composable
fun BigAlarmTheme(content: @Composable () -> Unit) {
    val settings = LocalContext.current.appGraph.settings
    val scale by settings.textScale.collectAsState()
    CompositionLocalProvider(LocalTextScale provides scale) {
        MaterialTheme(content = content)
    }
}

/** Font sizes used for alarm names; everything scales from these. */
object NameText {
    val listBase = 18.sp
    val ringBase = 30.sp

    @Composable
    fun listSize(): TextUnit = listBase * LocalTextScale.current

    @Composable
    fun ringSize(): TextUnit = ringBase * LocalTextScale.current
}
