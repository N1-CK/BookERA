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

    internal fun readEpub(file: File): List<ReadingPage> = ZipFile(file).use { zip ->
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
            if (item.attr("linear").equals("no", true)) return@forEach
            val resource = manifest[item.attr("idref")] ?: return@forEach
            if ("nav" in resource.attr("properties").split(' ')) return@forEach
            val href = resource.attr("href").substringBefore('#')
            if (!resource.attr("media-type").contains("html")) return@forEach
            val path = java.net.URI(null, null, if (base.isBlank()) href else "$base/$href", null).normalize().path
            val doc = Jsoup.parse(content(path))
            val chapter = doc.selectFirst("h1, h2, title")?.text()?.ifBlank { null } ?: "Глава ${result.size + 1}"
            val text = extractEpubText(doc)
            if (text.isNotBlank()) result.addAll(paginate(chapter, text))
            require(result.size <= 5000) { "EPUB contains too many pages" }
        }
        require(result.isNotEmpty()) { "No readable text in EPUB" }
        result
    }
    internal fun extractEpubText(doc: org.jsoup.nodes.Document): String {
        doc.select("script, style, nav, [hidden], [aria-hidden=true]").remove()
        val output = StringBuilder()
        val block = setOf("p", "div", "blockquote", "li", "pre", "h1", "h2", "h3", "h4", "h5", "h6")
        fun walk(node: org.jsoup.nodes.Node) {
            when (node) {
                is org.jsoup.nodes.TextNode -> output.append(node.wholeText.replace(Regex("[\\t\\r\\n ]+"), " "))
                is org.jsoup.nodes.Element -> {
                    val tag = node.tagName()
                    if (tag == "br") { output.append('\n'); return }
                    if (tag in block && output.isNotEmpty()) output.append("\n\n")
                    node.childNodes().forEach(::walk)
                    if (tag in block) output.append("\n\n")
                }
            }
        }
        walk(doc.body() ?: doc)
        return output.toString().replace(Regex(" *\n *"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

}
