package com.example.bookera.data.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
        val pager = rememberPagerState(initialPage = prefs.getInt("page_$bookId", 0).coerceIn(0, count - 1), pageCount = { count })
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager.currentPage) { prefs.edit().putInt("page_$bookId", pager.currentPage).apply() }
        Column(Modifier.fillMaxSize().background(Color(0xFFDFE2E5))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Назад") }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / $count")
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                var bitmap by remember(filePath, index) { mutableStateOf<Bitmap?>(null) }
                var scale by remember(index) { mutableFloatStateOf(1f) }
                var offset by remember(index) { mutableStateOf(Offset.Zero) }
                val transform = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
                LaunchedEffect(filePath, index) {
                    bitmap = withContext(Dispatchers.IO) {
                        ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                renderer.openPage(index).use { page ->
                                    val ratio = minOf(1440f / page.width, 2000f / page.height, 2f)
                                    val width = (page.width * ratio).toInt().coerceAtLeast(1)
                                    val height = (page.height * ratio).toInt().coerceAtLeast(1)
                                    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    image.eraseColor(AndroidColor.WHITE)
                                    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    image
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    bitmap?.let { Image(it.asImageBitmap(), "Страница ${index + 1}", Modifier.fillMaxSize()
                        .transformable(transform).graphicsLayer(scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) }
                        ?: CircularProgressIndicator()
                }
            }
            Slider(value = pager.currentPage.toFloat(), onValueChange = { scope.launch { pager.scrollToPage(it.toInt().coerceIn(0, count - 1)) } },
                valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(), modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}
