package com.example.offlinellm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CalmLightColorScheme = lightColorScheme(
    primary = Color(0xFF425CD6),
    onPrimary = Color.White,
    secondary = Color(0xFF5E6A85),
    onSecondary = Color.White,
    background = Color(0xFFF3F5FA),
    onBackground = Color(0xFF171C28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2130),
    surfaceVariant = Color(0xFFE8ECF7),
    onSurfaceVariant = Color(0xFF4A556E),
    secondaryContainer = Color(0xFFDEE3F3),
    primaryContainer = Color(0xFFDCE3FF),
    surfaceContainerHigh = Color(0xFFF1F4FC)
)

private val CalmDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF1A2858),
    secondary = Color(0xFFBCC6E3),
    onSecondary = Color(0xFF27324B),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE8ECFA),
    surface = Color(0xFF161D2D),
    onSurface = Color(0xFFE8ECFA),
    surfaceVariant = Color(0xFF23304A),
    onSurfaceVariant = Color(0xFFBECAE6),
    secondaryContainer = Color(0xFF2A3752),
    primaryContainer = Color(0xFF2A376D),
    surfaceContainerHigh = Color(0xFF1E273B)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CalmDarkColorScheme else CalmLightColorScheme,
        content = content
    )
}
