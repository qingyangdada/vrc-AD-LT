package com.vrc.friendtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vrc.friendtracker.VrApp

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A3FD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF24005C),
    secondary = Color(0xFF635B70),
    background = Color(0xFFFDF7FF),
    surface = Color(0xFFFDF7FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF3B0082),
    primaryContainer = Color(0xFF5223AC),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    // Pure black base so OLED panels switch pixels off (power saving);
    // cards/text keep their own subtle tints so the UI stays readable.
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF111111),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VrApp
    val nightMode by app.settingsRepo.darkMode.collectAsState()
    // Night mode switch overrides the system setting; off follows the system.
    val dark = nightMode || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}