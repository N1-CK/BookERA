package com.example.bookera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF6445F5), onPrimary = Color.White,
    secondary = Color(0xFF5137BA), onSecondary = Color.White,
    primaryContainer = Color(0xFFE9E3FF), onPrimaryContainer = Color(0xFF302164),
    background = Color(0xFFF6F5FA), onBackground = Color(0xFF242037),
    surface = Color.White, onSurface = Color(0xFF242037),
    surfaceVariant = Color(0xFFEAE6F1), onSurfaceVariant = Color(0xFF655C76),
    outline = Color(0xFF81748E))
private val Dark = darkColorScheme(primary = Color(0xFFC7B8FF), onPrimary = Color(0xFF2D165F),
    secondary = Color(0xFFBCAFff), onSecondary = Color(0xFF261464),
    primaryContainer = Color(0xFF40305F), onPrimaryContainer = Color(0xFFE9E3FF),
    background = Color(0xFF111014), onBackground = Color(0xFFF5F0EB),
    surface = Color(0xFF211F26), onSurface = Color(0xFFF5F0EB),
    surfaceVariant = Color(0xFF37323C), onSurfaceVariant = Color(0xFFD5CADB),
    outline = Color(0xFF9F90A5))

@Composable
fun BookERATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
