package com.example.bookera.data.reader

import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.Fullscreen
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bookera.data.db.AppDatabase
import com.example.bookera.data.local.reader.ReaderNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal data class ReadingPage(val chapter: String, val text: String)
internal fun paginate(chapter: String, text: String): List<ReadingPage> =
    text.takeIf { it.isNotBlank() }?.let { listOf(ReadingPage(chapter, it.replace("\r\n", "\n").trim())) } ?: emptyList()

internal data class LaidPage(val chapter: String, val text: String, val position: Int)
internal enum class ReaderFont(val label: String, val family: String) {
    SERIF("С засечками", "serif"), SANS("Без засечек", "sans-serif"),
    MONO("Моноширинный", "monospace"), CONDENSED("Узкий", "sans-serif-condensed");
    val typeface: Typeface get() = Typeface.create(family, Typeface.NORMAL)
}

/** The same typeface, line spacing and break strategy are used by layout and display. */
internal suspend fun reflow(sections: List<ReadingPage>, width: Int, height: Int, fontPx: Float, font: ReaderFont): List<LaidPage> {
    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = fontPx; typeface = font.typeface }
    val document = sections.joinToString("\n") { it.text }
    val boundaries = mutableListOf<Pair<Int, String>>()
    var offset = 0
    sections.forEach { boundaries += offset to it.chapter; offset += it.text.length + 1 }
    val result = mutableListOf<LaidPage>()
    var start = 0
    var chapterIndex = 0
    while (start < document.length) {
        coroutineContext.ensureActive()
        val windowStart = start
        val end = (start + 16000).coerceAtMost(document.length)
        val slice = document.substring(windowStart, end)
        val layout = StaticLayout.Builder.obtain(slice, 0, slice.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setLineSpacing(fontPx * .4f, 1f).build()
        while (start < end) {
            coroutineContext.ensureActive()
            val firstLine = layout.getLineForOffset(start - windowStart)
            val bottom = layout.getLineTop(firstLine) + height
            var low = firstLine
            var high = layout.lineCount - 1
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (layout.getLineBottom(middle) <= bottom) low = middle else high = middle - 1
            }
            // Do not emit a short page just because a layout window ends here.
            if (end < document.length && low >= layout.lineCount - 2 && start > windowStart) break
            var pageEnd = windowStart + layout.getLineEnd(low)
            pageEnd = pageEnd.coerceAtLeast(start + 1)
            while (pageEnd < document.length && document[pageEnd].isWhitespace()) pageEnd++
            while (chapterIndex + 1 < boundaries.size && boundaries[chapterIndex + 1].first <= start) chapterIndex++
            result += LaidPage(boundaries.getOrNull(chapterIndex)?.second.orEmpty(), document.substring(start, pageEnd), start)
            start = pageEnd
            require(result.size <= 20000) { "Слишком много страниц" }
        }
    }
    return result
}

internal fun pageAt(pages: List<LaidPage>, position: Int): Int {
    val found = pages.binarySearch { it.position.compareTo(position) }
    return (if (found >= 0) found else -found - 2).coerceIn(0, pages.lastIndex.coerceAtLeast(0))
}

@Composable
internal fun PagedTextReader(title: String, bookId: Long, pages: List<ReadingPage>, onBack: () -> Unit) {
    val context = LocalContext.current
    com.example.bookera.ui.reader.ReadingSession()
    val activityStore = remember { com.example.bookera.data.local.ReadingActivityStore.get(context) }
    var fullscreen by rememberSaveable(bookId) { mutableStateOf(false) }
    com.example.bookera.ui.theme.ReaderFullscreen(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }
    val prefs = remember { context.getSharedPreferences("reader_settings", 0) }
    val db = remember { AppDatabase.getDatabase(context) }
    val noteDao = remember { db.readerNoteDao() }
    val notes by remember(bookId) { noteDao.observeBook(bookId) }.collectAsState(initial = emptyList())
    var bookTitle by remember(bookId) { mutableStateOf(title) }
    LaunchedEffect(bookId) { bookTitle = db.bookDao().getBookById(bookId)?.title ?: title }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 19f).coerceIn(12f, 36f)) }
    var font by remember { mutableStateOf(ReaderFont.entries.getOrNull(prefs.getInt("font", if (prefs.getBoolean("serif", true)) 0 else 1)) ?: ReaderFont.SERIF) }
    val appDark = MaterialTheme.colorScheme.background.luminanceValue() < .5f
    var palette by remember { mutableIntStateOf(prefs.getInt("palette", if (appDark) 2 else 0).coerceIn(0, 2)) }
    var vertical by remember { mutableStateOf(prefs.getBoolean("vertical", false)) }
    var dialog by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var finished by remember(bookId) { mutableStateOf(false) }
    var anchor by remember(bookId) { mutableIntStateOf(prefs.getInt("position_$bookId", prefs.getInt("page_$bookId", 0) * 1250)) }
    var laidPages by remember(pages) { mutableStateOf<List<LaidPage>>(emptyList()) }
    val cache = remember(pages) { linkedMapOf<String, List<LaidPage>>() }
    var layoutError by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(true) }
    val paper = listOf(Color(0xFFFFFCF6), Color(0xFFF0E3CB), Color(0xFF17161B))[palette]
    val ink = if (palette == 2) Color(0xFFF8F1E7) else Color(0xFF242026)
    val muted = if (palette == 2) Color(0xFFCAC0D0) else Color(0xFF62554A)
    val accent = if (palette == 2) Color(0xFFFFA16B) else Color(0xFFAD3900)
    com.example.bookera.ui.theme.SystemBars(paper)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pager = rememberPagerState(pageCount = { laidPages.size.coerceAtLeast(1) })
    val current = pager.settledPage.coerceIn(0, laidPages.lastIndex.coerceAtLeast(0))
    val currentPage = laidPages.getOrNull(current)
    val currentMark = currentPage?.let { page -> notes.firstOrNull { !it.isQuote && it.locationType == "text" && pageAt(laidPages, it.position) == current } }
    LaunchedEffect(pager.settledPage, restoring, laidPages) {
        if (!restoring) laidPages.getOrNull(pager.settledPage)?.let { page ->
            anchor = page.position
            prefs.edit().putInt("position_$bookId", anchor).apply()
            activityStore.remember(bookId, pager.settledPage.toFloat() / laidPages.lastIndex.coerceAtLeast(1))
        }
    }
    fun goTo(position: Int) {
        scope.launch { pager.scrollToPage(pageAt(laidPages, position)) }
        dialog = ""
    }
    fun step(delta: Int) { scope.launch { pager.animateScrollToPage((current + delta).coerceIn(0, laidPages.lastIndex.coerceAtLeast(0))) } }
    val contents = remember(pages) {
        var start = 0
        pages.map { section -> (section.chapter to start).also { start += section.text.length + 1 } }
    }
    var searchResults by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(search, pages, dialog) {
        searchResults = emptyList()
        if (dialog != "search" || search.trim().length < 2) return@LaunchedEffect
        delay(200)
        searchResults = withContext(Dispatchers.Default) {
            val found = mutableListOf<Pair<String, Int>>()
            var base = 0
            val query = search.trim()
            for (section in pages) {
                coroutineContext.ensureActive()
                var index = section.text.indexOf(query, ignoreCase = true)
                while (index >= 0 && found.size < 40) {
                    val snippet = section.text.substring((index - 25).coerceAtLeast(0), (index + query.length + 45).coerceAtMost(section.text.length)).replace('\n', ' ')
                    found.add("…$snippet…" to (base + index))
                    index = section.text.indexOf(query, index + query.length, ignoreCase = true)
                }
                if (found.size >= 40) break
                base += section.text.length + 1
            }
            found
        }
    }
    Column(Modifier.fillMaxSize().background(paper)) {
        if (!fullscreen) Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = ink) }
            Text(bookTitle, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = ink)
            IconButton(onClick = { fullscreen = true }) { Icon(Icons.Outlined.Fullscreen, "Полный экран · коснитесь текста для выхода", tint = ink) }
            TextButton(onClick = { dialog = "settings" }) { Text("Aa", color = accent) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val textWidth = with(density) { (maxWidth - 40.dp).roundToPx().coerceAtLeast(1) }
            val textHeight = with(density) { (maxHeight - if (fullscreen) 20.dp else 44.dp).roundToPx().coerceAtLeast(1) }
            val fontPx = with(density) { fontSize.sp.toPx() }
            LaunchedEffect(pages, textWidth, textHeight, fontPx, font) {
                restoring = true
                try {
                    val key = "$textWidth:$textHeight:$fontPx:$font"
                    val result = cache[key] ?: withContext(Dispatchers.Default) { reflow(pages, textWidth, textHeight, fontPx, font) }
                    cache[key] = result
                    while (cache.size > 2) cache.remove(cache.keys.first())
                    laidPages = result
                    pager.scrollToPage(pageAt(result, anchor))
                    layoutError = null
                    restoring = false
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { layoutError = e.message ?: "Не удалось подготовить страницы"; restoring = false }
            }
            val renderPage: @Composable (Int) -> Unit = { index ->
                laidPages.getOrNull(index)?.let { page ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        if (!fullscreen) Text(page.chapter, color = muted, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(24.dp))
                        SelectablePage(page, font, fontPx, ink, notes, Modifier.fillMaxWidth().weight(1f), onTap = { if (fullscreen) fullscreen = false }) { start, end, quote ->
                            scope.launch {
                                noteDao.save(ReaderNote(UUID.randomUUID().toString(), bookId, bookTitle,
                                    page.position + start, page.position + end, quote, page.chapter))
                                Toast.makeText(context, "Цитата сохранена в профиле", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            if (laidPages.isNotEmpty()) {
                if (vertical) VerticalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !restoring, key = { laidPages.getOrNull(it)?.position ?: it }, pageContent = { renderPage(it) })
                else HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !restoring, key = { laidPages.getOrNull(it)?.position ?: it }, pageContent = { renderPage(it) })
            }
            if (restoring || layoutError != null) Box(Modifier.fillMaxSize().background(paper.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
                if (layoutError != null) Text(layoutError.orEmpty(), color = ink) else CircularProgressIndicator(color = accent)
            }
        }
        if (!fullscreen) {
        LinearProgressIndicator(progress = { if (laidPages.isEmpty()) 0f else (current + 1f) / laidPages.size },
            modifier = Modifier.fillMaxWidth().height(3.dp), color = accent, trackColor = paper)
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dialog = "contents" }) { Icon(Icons.Outlined.FormatListBulleted, "Оглавление", tint = ink) }
            IconButton(onClick = { dialog = "search" }) { Icon(Icons.Outlined.Search, "Поиск по книге", tint = ink) }
            IconButton(enabled = currentPage != null, onClick = {
                currentPage?.let { page -> scope.launch {
                    if (currentMark != null) noteDao.delete(currentMark.id)
                    else noteDao.save(ReaderNote(ReaderNote.bookmarkId(bookId, page.position), bookId, bookTitle, page.position, chapter = page.chapter))
                } }
            }) { Icon(if (currentMark != null) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "Закладка", tint = accent) }
            IconButton(onClick = { dialog = "notes" }) { Icon(Icons.Outlined.CollectionsBookmark, "Закладки и цитаты", tint = ink) }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { step(-1) }) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Предыдущая страница", tint = ink) }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = laidPages.isNotEmpty(), onClick = { dialog = "jump" }) { Text("${if (laidPages.isEmpty()) 0 else current + 1} / ${laidPages.size}", color = ink) }
            Spacer(Modifier.weight(1f))
            if (laidPages.isNotEmpty() && current == laidPages.lastIndex) TextButton(onClick = {
                scope.launch { db.bookDao().setRead(bookId, true, System.currentTimeMillis()); finished = true }
            }) { Text(if (finished) "✓" else "Готово", color = accent) }
            else IconButton(onClick = { step(1) }) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Следующая страница", tint = ink) }
        }
        }
    }
    when (dialog) {
        "settings" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Настройки чтения") },
            text = { Column {
                Text("Размер: ${fontSize.toInt()}")
                var draftSize by remember { mutableFloatStateOf(fontSize) }
                Slider(draftSize, { draftSize = it }, valueRange = 12f..36f, steps = 23,
                    onValueChangeFinished = { fontSize = draftSize; prefs.edit().putFloat("font_size", fontSize).apply() })
                ReaderFont.entries.forEach { item -> TextButton(onClick = { font = item; prefs.edit().putInt("font", item.ordinal).apply() }) { Text("${if (font == item) "●" else "○"} ${item.label}") } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (vertical) "Листать вверх-вниз" else "Листать вправо-влево", Modifier.weight(1f))
                    Switch(vertical, { vertical = it; prefs.edit().putBoolean("vertical", it).apply() })
                }
                Row { listOf("Светлая", "Сепия", "Тёмная").forEachIndexed { index, name ->
                    TextButton(onClick = { palette = index; prefs.edit().putInt("palette", index).apply() }) { Text("${if (palette == index) "●" else "○"} $name") }
                } }
            } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Готово") } })
        "jump" -> {
            var target by remember { mutableFloatStateOf(current.toFloat()) }
            AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Перейти к странице") },
                text = { Column { Text("${target.toInt() + 1} из ${laidPages.size}")
                    Slider(target, { target = it }, valueRange = 0f..laidPages.lastIndex.coerceAtLeast(1).toFloat()) } },
                confirmButton = { TextButton(onClick = { laidPages.getOrNull(target.toInt())?.let { goTo(it.position) } }) { Text("Перейти") } })
        }
        "contents", "search", "notes" -> AlertDialog(onDismissRequest = { dialog = "" },
            title = { Text(when (dialog) { "contents" -> "Оглавление"; "notes" -> "Закладки и цитаты"; else -> "Поиск по книге" }) },
            text = { Column(Modifier.heightIn(max = 440.dp)) {
                if (dialog == "search") OutlinedTextField(search, { search = it }, label = { Text("Слово или фраза") }, singleLine = true)
                LazyColumn {
                    if (dialog == "notes") {
                        if (notes.isEmpty()) item { Text("Выделите текст долгим нажатием и выберите «Сохранить цитату».") }
                        items(notes, key = { it.id }) { note ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (note.isQuote) "«${note.quote}»" else "Закладка · страница ${pageAt(laidPages, note.position) + 1}",
                                    Modifier.weight(1f).clickable { goTo(note.position) }.padding(vertical = 12.dp))
                                TextButton(onClick = { scope.launch { noteDao.delete(note.id) } }) { Text("×") }
                            }
                        }
                    } else {
                        val entries = if (dialog == "contents") contents else searchResults
                        if (entries.isEmpty()) item { Text(if (search.trim().length < 2) "Введите минимум 2 символа" else "Ничего не найдено") }
                        items(entries) { (label, position) -> Text(label, Modifier.fillMaxWidth().clickable { goTo(position) }.padding(12.dp)) }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Закрыть") } })
    }
}

private fun Color.luminanceValue(): Float = .2126f * red + .7152f * green + .0722f * blue

/** Native selection exposes exact offsets; saved highlights survive page reflow. */
@Composable
private fun SelectablePage(page: LaidPage, font: ReaderFont, fontPx: Float, ink: Color,
    notes: List<ReaderNote>, modifier: Modifier, onTap: () -> Unit, onQuote: (Int, Int, String) -> Unit) {
    val saveQuote by rememberUpdatedState(onQuote)
    val tap by rememberUpdatedState(onTap)
    val styled = remember(page, notes) {
        SpannableString(page.text).apply {
            notes.filter { it.isQuote && it.locationType == "text" && it.position < page.position + page.text.length && it.endPosition > page.position }.forEach { note ->
                val start = (note.position - page.position).coerceAtLeast(0)
                val end = (note.endPosition - page.position).coerceAtMost(length)
                if (start < end) setSpan(BackgroundColorSpan(0x6688AA22), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    AndroidView(modifier = modifier, factory = { context ->
        TextView(context).apply {
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            setTextIsSelectable(true)
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean { tap(); return false }
            })
            setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, 9001, 0, "Сохранить цитату").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                override fun onDestroyActionMode(mode: ActionMode) = Unit
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId != 9001) return false
                    val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
                    val end = maxOf(selectionStart, selectionEnd).coerceAtMost(text.length)
                    if (end > start) saveQuote(start, end, text.subSequence(start, end).toString())
                    mode.finish()
                    return true
                }
            }
        }
    }, update = { view ->
        view.typeface = font.typeface
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontPx)
        view.setLineSpacing(fontPx * .4f, 1f)
        view.setTextColor(ink.toArgb())
        if (view.tag !== styled) { view.text = styled; view.tag = styled }
    })
}
