package com.example.bookera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Ink, secondary = Copper, background = Paper,
    surface = Color.White, onSurface = Ink)
private val Dark = darkColorScheme(primary = Color(0xFFD4A386), secondary = Copper,
    background = Color(0xFF14202B), surface = Color(0xFF223240))

@Composable
fun BookERATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
