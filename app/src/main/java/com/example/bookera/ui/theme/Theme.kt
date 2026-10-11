package com.example.bookera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFFB83C00), onPrimary = Color.White,
    secondary = Color(0xFF5137BA), onSecondary = Color.White,
    primaryContainer = Color(0xFFFFE0CF), onPrimaryContainer = Color(0xFF602000),
    background = Color(0xFFF6F3F0), onBackground = Color(0xFF19181D),
    surface = Color.White, onSurface = Color(0xFF19181D),
    surfaceVariant = Color(0xFFECE6E0), onSurfaceVariant = Color(0xFF554D47),
    outline = Color(0xFF7E7269))
private val Dark = darkColorScheme(primary = Color(0xFFFFA16B), onPrimary = Color(0xFF431900),
    secondary = Color(0xFFBCAFff), onSecondary = Color(0xFF261464),
    primaryContainer = Color(0xFF63301C), onPrimaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF111014), onBackground = Color(0xFFF5F0EB),
    surface = Color(0xFF211F26), onSurface = Color(0xFFF5F0EB),
    surfaceVariant = Color(0xFF37323C), onSurfaceVariant = Color(0xFFD5CADB),
    outline = Color(0xFF9F90A5))

@Composable
fun BookERATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
