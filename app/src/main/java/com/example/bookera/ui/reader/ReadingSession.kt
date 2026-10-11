package com.example.bookera.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.bookera.data.local.ReadingActivityStore
import kotlinx.coroutines.delay

@Composable
fun ReadingSession() {
    val store = ReadingActivityStore.get(LocalContext.current)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Merely opening a book does not count as a day of reading.
            while (true) { delay(30_000); store.markReadingDay() }
        }
    }
}
