package com.example.bookera.data.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

class EpubReaderPlugin : ReaderPlugin {
    override val supportedFormats = listOf("epub")
    override val name = "EPUB Reader"

    @Composable
    override fun ReaderScreen(filePath: String, bookId: Long, onBack: () -> Unit) {
        var pages by remember(filePath) { mutableStateOf<List<ReadingPage>?>(null) }
        var error by remember(filePath) { mutableStateOf<String?>(null) }
        LaunchedEffect(filePath) {
            runCatching { withContext(Dispatchers.IO) { readEpub(File(filePath)) } }
                .onSuccess { pages = it }.onFailure { error = it.message }
        }
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("EPUB: $error") }
            pages.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PagedTextReader(File(filePath).nameWithoutExtension, bookId, pages!!, onBack)
        }
    }

    private fun readEpub(file: File): List<ReadingPage> = ZipFile(file).use { zip ->
        fun content(path: String): String {
            val entry = zip.getEntry(path) ?: error("Missing EPUB resource: $path")
            require(entry.size >= 0 && entry.size <= 2_000_000L) { "EPUB chapter too large" }
            return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val container = Jsoup.parse(content("META-INF/container.xml"), "", Parser.xmlParser())
        val opfPath = container.selectFirst("rootfile")?.attr("full-path") ?: error("Missing EPUB manifest")
        val opf = Jsoup.parse(content(opfPath), "", Parser.xmlParser())
        val base = opfPath.substringBeforeLast('/', "")
        val manifest = opf.select("manifest item").associateBy { it.attr("id") }
        val result = mutableListOf<ReadingPage>()
        opf.select("spine itemref").forEach { item ->
            val resource = manifest[item.attr("idref")] ?: return@forEach
            val href = resource.attr("href").substringBefore('#')
            if (!resource.attr("media-type").contains("html")) return@forEach
            val path = java.net.URI(null, null, if (base.isBlank()) href else "$base/$href", null).normalize().path
            val doc = Jsoup.parse(content(path))
            val chapter = doc.selectFirst("h1, h2, title")?.text()?.ifBlank { null } ?: "Глава ${result.size + 1}"
            val text = doc.select("body h1, body h2, body h3, body p, body blockquote, body li")
                .joinToString("\n\n") { it.text() }
                .ifBlank { doc.body()?.text().orEmpty() }
            if (text.isNotBlank()) result.addAll(paginate(chapter, text))
            require(result.size <= 5000) { "EPUB contains too many pages" }
        }
        require(result.isNotEmpty()) { "No readable text in EPUB" }
        result
    }
}
