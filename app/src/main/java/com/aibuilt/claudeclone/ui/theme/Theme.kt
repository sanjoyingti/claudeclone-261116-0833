package com.aibuilt.claudeclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Paper,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = InkSoft,
    outline = Border,
    outlineVariant = Border,
    error = ErrorRed,
    onError = Paper
)

private val DarkScheme = darkColorScheme(
    primary = Terracotta,
    onPrimary = NightInk,
    primaryContainer = NightSand,
    onPrimaryContainer = NightInk,
    background = NightBg,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSand,
    onSurfaceVariant = NightInkSoft,
    outline = NightBorder,
    outlineVariant = NightBorder,
    error = ErrorRedNight,
    onError = NightInk
)

@Composable
fun ClaudeCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ClaudeTypography,
        content = content
    )
}
