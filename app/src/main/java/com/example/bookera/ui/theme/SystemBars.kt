package com.example.bookera.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private class Chrome(private val background: Color) {
    private val requests = linkedMapOf<Any, Pair<Color, Color>>()
    var colors by mutableStateOf(background to background)
        private set
    fun add(key: Any, value: Pair<Color, Color>) { requests[key] = value; refresh() }
    fun remove(key: Any) { requests.remove(key); refresh() }
    private fun refresh() { colors = requests.values.lastOrNull() ?: (background to background) }
}
private val LocalFullscreen = staticCompositionLocalOf<MutableState<Boolean>?> { null }

@Composable
fun SystemSafePadding(): Modifier = if (LocalFullscreen.current?.value == true)
    Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier.safeDrawingPadding()

@Composable
fun ReaderFullscreen(enabled: Boolean) {
    val state = LocalFullscreen.current ?: return
    DisposableEffect(state, enabled) { state.value = enabled; onDispose { state.value = false } }
}

private val LocalChrome = staticCompositionLocalOf<Chrome?> { null }

/** Paint inset areas as well as the bars: Android edge-to-edge bars can be transparent. */
@Composable
fun SystemChrome(content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val chrome = remember(background) { Chrome(background) }
    val fullscreen = remember { mutableStateOf(false) }
    val view = LocalView.current
    val activity = view.context.activity()
    val (status, navigation) = chrome.colors
    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = status.luminance() > .5f
                isAppearanceLightNavigationBars = navigation.luminance() > .5f
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen.value) hide(WindowInsetsCompat.Type.systemBars())
                else show(WindowInsetsCompat.Type.systemBars())
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    CompositionLocalProvider(LocalChrome provides chrome, LocalFullscreen provides fullscreen) {
        Box(Modifier.fillMaxSize().background(status)) {
            content()
            if (!fullscreen.value) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars).background(navigation))
        }
    }
}

@Composable
fun SystemBars(color: Color, navigationColor: Color = color) {
    val chrome = LocalChrome.current ?: return
    val key = remember { Any() }
    DisposableEffect(chrome, color, navigationColor) {
        chrome.add(key, color to navigationColor)
        onDispose { chrome.remove(key) }
    }
}
