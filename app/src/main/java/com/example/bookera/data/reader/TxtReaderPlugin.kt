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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

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
                paginate(file.nameWithoutExtension, decodeText(file.readBytes())).also {
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

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return runCatching {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        }.getOrElse { String(bytes, java.nio.charset.Charset.forName("windows-1251")) }
    }
}
