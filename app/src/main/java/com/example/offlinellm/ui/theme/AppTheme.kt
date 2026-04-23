package com.example.offlinellm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FrostLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF5A68D8),
    onPrimary = Color.White,
    secondary = Color(0xFF8B7DDB),
    onSecondary = Color.White,
    tertiary = Color(0xFF54A6DB),
    background = Color(0xFFF3F5FF),
    onBackground = Color(0xFF1A1E2B),
    surface = Color(0xE6FFFFFF),
    onSurface = Color(0xFF1F2430),
    surfaceVariant = Color(0xCCEDF0FF),
    onSurfaceVariant = Color(0xFF4D5570),
    outline = Color(0x667680B4)
)

private val FrostDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9BFFF),
    onPrimary = Color(0xFF22294C),
    secondary = Color(0xFFD0C6FF),
    onSecondary = Color(0xFF2C2552),
    tertiary = Color(0xFF9BDBFF),
    background = Color(0xFF0E1222),
    onBackground = Color(0xFFEDF0FF),
    surface = Color(0xB3182038),
    onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Color(0xB32A355C),
    onSurfaceVariant = Color(0xFFC7CEE8),
    outline = Color(0x667989B8)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) FrostDarkColorScheme else FrostLightColorScheme,
        content = content
    )
}
