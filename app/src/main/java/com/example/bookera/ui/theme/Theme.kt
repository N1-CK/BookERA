package com.example.bookera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Ink, onPrimary = Color.White,
    secondary = Color(0xFF79452F), onSecondary = Color.White,
    background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFE5E9E9), onSurfaceVariant = Color(0xFF46545E))
private val Dark = darkColorScheme(primary = Color(0xFFE6B79D), onPrimary = Color(0xFF172837),
    secondary = Color(0xFFE6B79D), onSecondary = Color(0xFF172837),
    background = Color(0xFF14202B), onBackground = Color(0xFFF4F5F5),
    surface = Color(0xFF223240), onSurface = Color(0xFFF4F5F5),
    surfaceVariant = Color(0xFF354653), onSurfaceVariant = Color(0xFFD4DEE3))

@Composable
fun BookERATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
