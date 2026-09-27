package com.example.bookera.data.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TxtReaderPlugin : ReaderPlugin {
    override val supportedFormats = listOf("txt")
    override val name = "TXT Reader"

    @Composable
    override fun ReaderScreen(filePath: String, bookId: Long, onBack: () -> Unit) {
        var pages by remember(filePath) { mutableStateOf<List<ReadingPage>?>(null) }
        var error by remember(filePath) { mutableStateOf<String?>(null) }
        LaunchedEffect(filePath) {
            runCatching { withContext(Dispatchers.IO) {
                val file = File(filePath)
                require(file.length() <= 10L * 1024 * 1024) { "Text file exceeds 10 MB" }
                paginate(file.nameWithoutExtension, file.readText(Charsets.UTF_8)).also {
                    require(it.isNotEmpty()) { "Empty text file" }
                }
            } }.onSuccess { pages = it }.onFailure { error = it.message }
        }
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: $error") }
            pages.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PagedTextReader(File(filePath).nameWithoutExtension, bookId, pages!!, onBack)
        }
    }
}
