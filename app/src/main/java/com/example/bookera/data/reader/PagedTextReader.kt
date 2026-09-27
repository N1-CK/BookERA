package com.example.bookera.data.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal data class ReadingPage(val chapter: String, val text: String)

internal fun paginate(chapter: String, text: String): List<ReadingPage> {
    val pages = mutableListOf<ReadingPage>()
    val paragraphs = text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
    val content = StringBuilder()
    fun flush() {
        if (content.isNotBlank()) { pages.add(ReadingPage(chapter, content.toString().trim())); content.clear() }
    }
    paragraphs.forEach { paragraph ->
        val words = paragraph.trim().split(Regex("\\s+"))
        words.forEach { word ->
            if (content.length > 1250) flush()
            content.append(word).append(' ')
        }
        content.append("\n\n")
    }
    flush()
    return pages
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedTextReader(title: String, bookId: Long, pages: List<ReadingPage>, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader_settings", 0) }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 19f)) }
    var palette by remember { mutableIntStateOf(prefs.getInt("palette", 0)) }
    var showSettings by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var bookmarks by remember(bookId) { mutableStateOf(prefs.getStringSet("bookmark_$bookId", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()) }
    var serif by remember { mutableStateOf(prefs.getBoolean("serif", true)) }
    val colors = listOf(Color(0xFFF8F6F1), Color(0xFFEADFC9), Color(0xFF1D242B))
    val paper = colors[palette.coerceIn(0, 2)]
    val ink = if (palette == 2) Color(0xFFECE7E0) else Color(0xFF252932)
    val pager = rememberPagerState(initialPage = prefs.getInt("page_$bookId", 0).coerceIn(0, (pages.size - 1).coerceAtLeast(0)), pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { prefs.edit().putInt("page_$bookId", pager.currentPage).apply() }
    Column(Modifier.fillMaxSize().background(paper)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад", color = ink) }
            Text(title, Modifier.weight(1f), maxLines = 1, color = ink, textAlign = TextAlign.Center)
            TextButton(onClick = { showSettings = true }) { Text("Aa", color = ink) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp), pageSpacing = 8.dp) { index ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(pages[index].chapter, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .65f))
                Spacer(Modifier.height(20.dp))
                SelectionContainer { Text(pages[index].text, fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif, fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.55f).sp, color = ink) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showContents = true }) { Text("Оглавление", color = ink) }
            TextButton(onClick = {
                bookmarks = if (pager.currentPage in bookmarks) bookmarks - pager.currentPage else bookmarks + pager.currentPage
                prefs.edit().putStringSet("bookmark_$bookId", bookmarks.map { it.toString() }.toSet()).apply()
            }) { Text(if (pager.currentPage in bookmarks) "★" else "☆", color = ink) }
            TextButton(onClick = { showBookmarks = true }) { Text("Метки", color = ink) }
            Spacer(Modifier.weight(1f))
            Text("${pager.currentPage + 1} / ${pages.size}", color = ink, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex)) } }) { Text("→", color = ink) }
        }
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Настройки чтения") },
        text = { Column {
            Text("Размер шрифта: ${fontSize.toInt()}")
            Slider(value = fontSize, onValueChange = { fontSize = it; prefs.edit().putFloat("font_size", it).apply() }, valueRange = 14f..30f)
            TextButton(onClick = { serif = !serif; prefs.edit().putBoolean("serif", serif).apply() }) {
                Text(if (serif) "Шрифт: с засечками" else "Шрифт: без засечек")
            }
            listOf("Светлая", "Сепия", "Тёмная").forEachIndexed { index, name ->
                Text(name, Modifier.fillMaxWidth().selectable(palette == index) { palette = index; prefs.edit().putInt("palette", index).apply() }.padding(10.dp))
            }
        } }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Готово") } })
    if (showContents) {
        val chapters = remember(pages) { pages.mapIndexed { index, page -> page.chapter to index }.distinctBy { it.first } }
        AlertDialog(onDismissRequest = { showContents = false }, title = { Text("Оглавление") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                chapters.forEach { (name, index) -> Text(name, Modifier.fillMaxWidth().selectable(false) {
                    scope.launch { pager.scrollToPage(index) }; showContents = false
                }.padding(12.dp)) }
            } }, confirmButton = { TextButton(onClick = { showContents = false }) { Text("Закрыть") } })
    }
    if (showBookmarks) AlertDialog(onDismissRequest = { showBookmarks = false }, title = { Text("Закладки") },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            if (bookmarks.isEmpty()) Text("Закладок пока нет")
            bookmarks.sorted().forEach { index -> Text("Страница ${index + 1}: ${pages.getOrNull(index)?.chapter.orEmpty()}",
                Modifier.fillMaxWidth().selectable(false) { scope.launch { pager.scrollToPage(index.coerceIn(0, pages.lastIndex)) }; showBookmarks = false }.padding(10.dp)) }
        } }, confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("Закрыть") } })
}
