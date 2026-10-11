package com.example.bookera.data.reader

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.bookera.data.db.AppDatabase
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Each source section retains its full text so reflow can follow font and screen changes.
internal data class ReadingPage(val chapter: String, val text: String)
internal fun paginate(chapter: String, text: String): List<ReadingPage> =
    text.takeIf { it.isNotBlank() }?.let { listOf(ReadingPage(chapter, it.replace("\r\n", "\n").trim())) } ?: emptyList()

private data class LaidPage(val chapter: String, val text: String, val position: Int)

/** Android text layout gives a safe page boundary for the current viewport, including newlines. */
private fun reflow(sections: List<ReadingPage>, width: Int, height: Int, fontPx: Float, serif: Boolean): List<LaidPage> {
    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontPx
        typeface = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
    }
    val lines = (height / (fontPx * 1.58f)).toInt().coerceAtLeast(2) - 1
    val result = mutableListOf<LaidPage>()
    var base = 0
    sections.forEach { section ->
        var start = 0
        while (start < section.text.length) {
            // Limit individual layout work even for multi-megabyte books.
            val end = (start + 6000).coerceAtMost(section.text.length)
            val slice = section.text.substring(start, end)
            val layout = StaticLayout.Builder.obtain(slice, 0, slice.length, paint, width.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(fontPx * .52f, 1f).setMaxLines(lines.coerceAtLeast(1)).build()
            var length = layout.getLineEnd(layout.lineCount - 1).coerceAtLeast(1)
            if (start + length < section.text.length) {
                val split = slice.lastIndexOfAny(charArrayOf(' ', '\n'), (length - 1).coerceAtMost(slice.lastIndex))
                if (split > length / 2) length = split + 1
            }
            result.add(LaidPage(section.chapter, section.text.substring(start, start + length).trimEnd(), base + start))
            start += length
            require(result.size <= 12000) { "Слишком много страниц" }
        }
        base += section.text.length + 1
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedTextReader(title: String, bookId: Long, pages: List<ReadingPage>, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader_settings", 0) }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 19f).coerceIn(14f, 30f)) }
    var palette by remember { mutableIntStateOf(prefs.getInt("palette", 0).coerceIn(0, 2)) }
    var serif by remember { mutableStateOf(prefs.getBoolean("serif", true)) }
    var dialog by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var finished by remember(bookId) { mutableStateOf(false) }
    var anchor by remember(bookId) { mutableIntStateOf(prefs.getInt("position_$bookId", prefs.getInt("page_$bookId", 0) * 1250)) }
    var bookmarks by remember(bookId) { mutableStateOf(
        if (prefs.contains("bookmark_position_$bookId"))
            prefs.getStringSet("bookmark_position_$bookId", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        else prefs.getStringSet("bookmark_$bookId", emptySet()).orEmpty().mapNotNull { it.toIntOrNull()?.times(1250) }.toSet()
    ) }
    var laidPages by remember(pages) { mutableStateOf<List<LaidPage>>(emptyList()) }
    var layoutError by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(true) }
    val paper = listOf(Color(0xFFF8F6F1), Color(0xFFEADFC9), Color(0xFF1D242B))[palette]
    val ink = if (palette == 2) Color(0xFFF4F0E9) else Color(0xFF202833)
    val muted = if (palette == 2) Color(0xFFCCC7C0) else Color(0xFF46515B)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(paper)) {
        val textWidth = with(density) { (maxWidth - 52.dp).roundToPx().coerceAtLeast(1) }
        val textHeight = with(density) { (maxHeight - 156.dp).roundToPx().coerceAtLeast(1) }
        val fontPx = with(density) { fontSize.sp.toPx() }
        LaunchedEffect(pages, textWidth, textHeight, fontPx, serif) {
            restoring = true
            try {
                laidPages = withContext(Dispatchers.Default) { reflow(pages, textWidth, textHeight, fontPx, serif) }
                layoutError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                layoutError = e.message ?: "Не удалось разметить книгу"
                laidPages = emptyList()
            }
        }
        if (laidPages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(layoutError ?: "Подготовка страниц…", color = ink) }
        } else {
            val pager = rememberPagerState(initialPage = laidPages.indexOfLast { it.position <= anchor }.coerceAtLeast(0),
                pageCount = { laidPages.size })
            LaunchedEffect(laidPages) {
                pager.scrollToPage(laidPages.indexOfLast { it.position <= anchor }.coerceAtLeast(0))
                restoring = false
            }
            LaunchedEffect(pager.currentPage, restoring) {
                if (!restoring) laidPages.getOrNull(pager.currentPage)?.let {
                    anchor = it.position
                    prefs.edit().putInt("position_$bookId", anchor).apply()
                }
            }
            val current = pager.currentPage.coerceIn(0, laidPages.lastIndex)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← Назад", color = ink) }
                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = ink)
                    TextButton(onClick = { dialog = "settings" }) { Text("Aa", color = ink) }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).pointerInput(pager) {
                    detectTapGestures { point ->
                        if (point.x < size.width * .22f) scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                        if (point.x > size.width * .78f) scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(laidPages.lastIndex)) }
                    }
                }, pageSpacing = 6.dp) { index ->
                    val page = laidPages[index]
                    Column(Modifier.fillMaxSize().graphicsLayer {
                        val distance = (pager.currentPage - index) + pager.currentPageOffsetFraction
                        rotationY = distance * 9f
                        cameraDistance = 20f * density.density
                    }.padding(horizontal = 26.dp, vertical = 8.dp)) {
                        Text(page.chapter, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer {
                            Text(page.text, fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
                                fontSize = fontSize.sp, lineHeight = (fontSize * 1.52f).sp, color = ink)
                        }
                    }
                }
                LinearProgressIndicator(progress = { (current + 1f) / laidPages.size }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = if (palette == 2) Color(0xFFD4A386) else Color(0xFF90543A), trackColor = paper)
                Row(Modifier.fillMaxWidth().height(86.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage((current - 1).coerceAtLeast(0)) } }) { Text("←", color = ink) }
                    TextButton(onClick = { dialog = "contents" }) { Text("☷", color = ink) }
                    TextButton(onClick = { dialog = "search" }) { Text("⌕", color = ink) }
                    TextButton(onClick = {
                        val position = laidPages[current].position
                        bookmarks = if (position in bookmarks) bookmarks - position else bookmarks + position
                        prefs.edit().putStringSet("bookmark_position_$bookId", bookmarks.map { it.toString() }.toSet()).apply()
                    }) { Text(if (laidPages[current].position in bookmarks) "★" else "☆", color = ink) }
                    TextButton(onClick = { dialog = "bookmarks" }) { Text("Метки", color = ink) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { dialog = "jump" }) { Text("${current + 1} / ${laidPages.size}", color = ink) }
                    if (current == laidPages.lastIndex) TextButton(onClick = {
                        scope.launch(Dispatchers.IO) { AppDatabase.getDatabase(context).bookDao()
                            .setRead(bookId, true, System.currentTimeMillis()) }
                        finished = true
                    }) { Text(if (finished) "✓" else "Готово", color = ink) }
                    else TextButton(onClick = { scope.launch { pager.animateScrollToPage(current + 1) } }) { Text("→", color = ink) }
                }
            }
            fun goTo(position: Int) {
                scope.launch { pager.animateScrollToPage(laidPages.indexOfLast { it.position <= position }.coerceAtLeast(0)) }
                dialog = ""
            }
            when (dialog) {
                "settings" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Настройки чтения") },
                    text = { Column {
                        Text("Размер шрифта: ${fontSize.toInt()}")
                        Slider(value = fontSize, onValueChange = { fontSize = it; prefs.edit().putFloat("font_size", it).apply() }, valueRange = 14f..30f)
                        TextButton(onClick = { serif = !serif; prefs.edit().putBoolean("serif", serif).apply() }) {
                            Text(if (serif) "Шрифт с засечками" else "Шрифт без засечек")
                        }
                        listOf("Светлая", "Сепия", "Тёмная").forEachIndexed { index, name ->
                            TextButton(onClick = { palette = index; prefs.edit().putInt("palette", index).apply() }) {
                                Text(if (palette == index) "● $name" else "○ $name")
                            }
                        }
                    } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Готово") } })
                "jump" -> {
                    var target by remember { mutableFloatStateOf(current.toFloat()) }
                    AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Перейти к странице") },
                        text = { Column {
                            Text("${target.toInt() + 1} из ${laidPages.size}")
                            Slider(target, { target = it }, valueRange = 0f..laidPages.lastIndex.coerceAtLeast(1).toFloat())
                        } }, confirmButton = { TextButton(onClick = { goTo(laidPages[target.toInt().coerceIn(0, laidPages.lastIndex)].position) }) { Text("Перейти") } },
                        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Отмена") } })
                }
                "contents", "bookmarks", "search" -> AlertDialog(onDismissRequest = { dialog = "" },
                    title = { Text(when (dialog) { "contents" -> "Оглавление"; "bookmarks" -> "Закладки"; else -> "Поиск по книге" }) },
                    text = { Column(Modifier.heightIn(max = 440.dp)) {
                        if (dialog == "search") OutlinedTextField(search, { search = it }, label = { Text("Слово или фраза") }, singleLine = true)
                        val entries: List<Pair<String, Int>> = when (dialog) {
                            "contents" -> {
                                var start = 0
                                pages.map { section -> (section.chapter to start).also { start += section.text.length + 1 } }
                            }
                            "bookmarks" -> bookmarks.sorted().map { position ->
                                "Страница ${laidPages.indexOfLast { it.position <= position }.coerceAtLeast(0) + 1}" to position
                            }
                            else -> if (search.length < 2) emptyList() else {
                                var base = 0
                                pages.flatMap { section ->
                                    val found = mutableListOf<Pair<String, Int>>()
                                    var index = section.text.indexOf(search, ignoreCase = true)
                                    while (index >= 0 && found.size < 25) {
                                        val snippet = section.text.substring((index - 30).coerceAtLeast(0),
                                            (index + search.length + 40).coerceAtMost(section.text.length)).replace('\n', ' ')
                                        found.add("…$snippet…" to (base + index))
                                        index = section.text.indexOf(search, index + search.length, ignoreCase = true)
                                    }
                                    base += section.text.length + 1
                                    found
                                }.take(40)
                            }
                        }
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (entries.isEmpty()) Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            entries.forEach { (label, position) ->
                                Text(label, Modifier.fillMaxWidth().clickable { goTo(position) }.padding(12.dp))
                            }
                        }
                    } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Закрыть") } })
            }
        }
    }
}
