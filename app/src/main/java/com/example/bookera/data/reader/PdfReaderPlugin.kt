package com.example.bookera.data.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.bookera.data.db.AppDatabase
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.VerticalPager
import android.util.LruCache
import com.example.bookera.data.local.reader.ReaderNote
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfReaderPlugin : ReaderPlugin {
    override val supportedFormats = listOf("pdf")
    override val name = "PDF Reader"

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun ReaderScreen(filePath: String, bookId: Long, onBack: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("reader_settings", 0) }
        val db = remember { AppDatabase.getDatabase(context) }
        val dao = remember { db.readerNoteDao() }
        val notes by remember(bookId) { dao.observeBook(bookId) }.collectAsState(initial = emptyList())
        val bookmarks = notes.filter { !it.isQuote && it.locationType == "pdf" }.map { it.position }.toSet()
        var bookTitle by remember(bookId) { mutableStateOf(File(filePath).nameWithoutExtension) }
        LaunchedEffect(bookId) { bookTitle = db.bookDao().getBookById(bookId)?.title ?: bookTitle }
        var vertical by remember { mutableStateOf(prefs.getBoolean("vertical", false)) }
        var showSettings by remember { mutableStateOf(false) }
        val bitmapCache = remember(filePath) { object : LruCache<Int, Bitmap>(20 * 1024 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
        } }
        var count by remember(filePath) { mutableIntStateOf(0) }
        var error by remember(filePath) { mutableStateOf<String?>(null) }
        LaunchedEffect(filePath) {
            runCatching { withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { it.pageCount }
                }
            } }.onSuccess { count = it }.onFailure { error = it.message }
        }
        if (count == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator() else Text("PDF: $error")
            }
            return
        }
        com.example.bookera.ui.reader.ReadingSession()
        val activityStore = remember { com.example.bookera.data.local.ReadingActivityStore.get(context) }
        com.example.bookera.ui.theme.SystemBars(MaterialTheme.colorScheme.background)
        val pager = rememberPagerState(initialPage = prefs.getInt("page_$bookId", 0).coerceIn(0, count - 1), pageCount = { count })
        val scope = rememberCoroutineScope()
        var showBookmarks by remember { mutableStateOf(false) }
        var showJump by remember { mutableStateOf(false) }
        var finished by remember(bookId) { mutableStateOf(false) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val toolbarInk = MaterialTheme.colorScheme.onSurface
        LaunchedEffect(pager.currentPage) { scale = 1f; offset = Offset.Zero }
        LaunchedEffect(pager.settledPage) {
            prefs.edit().putInt("page_$bookId", pager.settledPage).apply()
            activityStore.remember(bookId, pager.settledPage.toFloat() / (count - 1).coerceAtLeast(1))
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Назад", color = toolbarInk) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showSettings = true }) { Text("Вид", color = toolbarInk) }
                TextButton(onClick = { showBookmarks = true }) { Text("Метки", color = toolbarInk) }
                TextButton(onClick = {
                    val page = pager.settledPage
                    scope.launch {
                        val id = ReaderNote.bookmarkId(bookId, page, "pdf")
                        if (page in bookmarks) dao.delete(id)
                        else dao.save(ReaderNote(id, bookId, bookTitle, page, locationType = "pdf"))
                    }
                }) { Text(if (pager.currentPage in bookmarks) "★" else "☆", color = toolbarInk) }
            }
            val renderPage: @Composable (Int) -> Unit = { index ->
                var bitmap by remember(filePath, index) { mutableStateOf<Bitmap?>(null) }
                val transform = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
                LaunchedEffect(filePath, index) {
                    try {
                        bitmap = bitmapCache.get(index) ?: withContext(Dispatchers.IO) {
                            ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                                PdfRenderer(descriptor).use { renderer ->
                                    renderer.openPage(index).use { page ->
                                        val ratio = minOf(1440f / page.width, 2000f / page.height, 2f)
                                        val width = (page.width * ratio).toInt().coerceAtLeast(1)
                                        val height = (page.height * ratio).toInt().coerceAtLeast(1)
                                        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        image.eraseColor(AndroidColor.WHITE)
                                        page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        bitmapCache.put(index, image)
                                        image
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Не удалось отрисовать PDF" }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    bitmap?.let { Image(it.asImageBitmap(), "Страница ${index + 1}", Modifier.fillMaxSize()
                        .pointerInput(index) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f; offset = Offset.Zero }) }
                        .transformable(transform).graphicsLayer(scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) }
                        ?: if (error == null) CircularProgressIndicator() else Text(error.orEmpty())
                }
            }
            if (vertical) VerticalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = scale <= 1.01f,
                beyondViewportPageCount = 1) { renderPage(it) }
            else HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = scale <= 1.01f,
                beyondViewportPageCount = 1) { renderPage(it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }) { Text("←", color = toolbarInk) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showJump = true }) { Text("${pager.currentPage + 1} / $count", color = toolbarInk) }
                Spacer(Modifier.weight(1f))
                if (pager.currentPage == count - 1) TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { AppDatabase.getDatabase(context).bookDao()
                        .setRead(bookId, true, System.currentTimeMillis()) }
                    finished = true
                }) { Text(if (finished) "✓" else "Готово", color = toolbarInk) }
                else TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text("→", color = toolbarInk) }
            }
            var sliderPage by remember(pager.settledPage) { mutableFloatStateOf(pager.settledPage.toFloat()) }
            Slider(value = sliderPage, onValueChange = { sliderPage = it },
                onValueChangeFinished = { scope.launch { pager.scrollToPage(sliderPage.toInt().coerceIn(0, count - 1)) } },
                valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(), modifier = Modifier.padding(horizontal = 20.dp),
                colors = SliderDefaults.colors(thumbColor = toolbarInk, activeTrackColor = toolbarInk))
        }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Чтение PDF") },
            text = { Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (vertical) "Листать вверх-вниз" else "Листать вправо-влево", Modifier.weight(1f))
                    Switch(vertical, { vertical = it; prefs.edit().putBoolean("vertical", it).apply() })
                }
                Text("Масштаб: жест двумя пальцами или двойное нажатие. В PDF шрифт задан документом. Выделение цитат доступно в FB2, EPUB и TXT.")
            } }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Готово") } })
        if (showBookmarks) AlertDialog(onDismissRequest = { showBookmarks = false }, title = { Text("Закладки PDF") },
            text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                if (bookmarks.isEmpty()) Text("Закладок пока нет")
                bookmarks.sorted().forEach { page ->
                    TextButton(onClick = { scope.launch { pager.scrollToPage(page.coerceIn(0, count - 1)) }; showBookmarks = false }) {
                        Text("Страница ${page + 1}")
                    }
                }
            } }, confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("Закрыть") } })
        if (showJump) {
            var target by remember { mutableFloatStateOf(pager.currentPage.toFloat()) }
            AlertDialog(onDismissRequest = { showJump = false }, title = { Text("Перейти к странице") },
                text = { Column { Text("${target.toInt() + 1} из $count")
                    Slider(target, { target = it }, valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat()) } },
                confirmButton = { TextButton(onClick = { scope.launch { pager.scrollToPage(target.toInt().coerceIn(0, count - 1)) }; showJump = false }) { Text("Перейти") } })
        }
    }
}
