package com.example.bookera.data.reader.fb2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.bookera.data.reader.PagedTextReader
import com.example.bookera.data.reader.ReaderPlugin
import com.example.bookera.data.reader.ReadingPage
import com.example.bookera.data.reader.paginate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class Fb2ReaderPlugin : ReaderPlugin {
    override val supportedFormats = listOf("fb2")
    override val name = "FB2 Reader"

    @Composable
    override fun ReaderScreen(filePath: String, bookId: Long, onBack: () -> Unit) {
        val context = LocalContext.current
        var content by remember(filePath) { mutableStateOf<Pair<String, List<ReadingPage>>?>(null) }
        var error by remember(filePath) { mutableStateOf<String?>(null) }
        LaunchedEffect(filePath) {
            runCatching { withContext(Dispatchers.IO) {
                val parsed = Fb2Parser().parse(File(filePath))
                val sections = mutableListOf<ReadingPage>()
                fun append(chapter: Fb2Chapter) {
                    val text = chapter.blocks.joinToString("\n\n") { block -> when (block) {
                        is Fb2Block.Paragraph -> block.text
                        is Fb2Block.SectionTitle -> block.text
                        is Fb2Block.Subtitle -> block.text
                        is Fb2Block.Epigraph -> block.text
                        is Fb2Block.Poem -> listOfNotNull(block.title, block.verses.joinToString("\n")).joinToString("\n")
                        is Fb2Block.Image -> "[Изображение]"
                        is Fb2Block.EmptyLine -> ""
                    } }
                    sections.addAll(paginate(chapter.title?.takeIf { it.isNotBlank() } ?: parsed.title, text))
                    chapter.children.forEach(::append)
                }
                parsed.chapters.forEach(::append)
                require(sections.isNotEmpty()) { "В книге нет доступного текста" }
                val settings = context.getSharedPreferences("reader_settings", 0)
                if (!settings.contains("position_$bookId")) {
                    val oldIndex = context.getSharedPreferences("fb2_reader_progress", 0)
                        .getInt("book_${bookId}_index", -1)
                    if (oldIndex > 0) {
                        val oldChapter = parsed.chapters.getOrNull(oldIndex - 1)?.title
                        var position = 0
                        for (section in sections) {
                            if (section.chapter == oldChapter) break
                            position += section.text.length + 1
                        }
                        settings.edit().putInt("position_$bookId", position).apply()
                    }
                }
                parsed.title to sections
            } }.onSuccess { content = it }.onFailure { error = it.message ?: "Не удалось открыть FB2" }
        }
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty()) }
            content == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PagedTextReader(content!!.first, bookId, content!!.second, onBack)
        }
    }
}
