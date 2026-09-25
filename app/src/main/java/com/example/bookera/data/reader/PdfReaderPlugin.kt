//// PdfReaderPlugin.kt
//package com.example.bookera.data.reader
//
//import android.graphics.pdf.PdfRenderer
//import android.os.ParcelFileDescriptor
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.itemsIndexed
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.TopAppBar
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.io.File
//
//class PdfReaderPlugin : ReaderPlugin {
//    override val supportedFormats = listOf("pdf")
//    override val name = "PDF Reader"
//
//    @Composable
//    override fun ReaderScreen(filePath: String, onBack: () -> Unit) {
//        val context = LocalContext.current
//        var pages by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
//        var isLoading by remember { mutableStateOf(true) }
//        var error by remember { mutableStateOf<String?>(null) }
//
//        LaunchedEffect(filePath) {
//            try {
//                pages = withContext(Dispatchers.IO) {
//                    renderPdfPages(context, filePath)
//                }
//                isLoading = false
//            } catch (e: Exception) {
//                error = e.message
//                isLoading = false
//            }
//        }
//
//        PdfReaderScreen(
//            pages = pages,
//            isLoading = isLoading,
//            error = error,
//            onBack = onBack
//        )
//    }
//
//    private suspend fun renderPdfPages(context: Context, filePath: String): List<android.graphics.Bitmap> =
//        withContext(Dispatchers.IO) {
//            val file = File(filePath)
//            if (!file.exists()) return@withContext emptyList()
//
//            val bitmapList = mutableListOf<android.graphics.Bitmap>()
//            var pdfRenderer: PdfRenderer? = null
//            var input: ParcelFileDescriptor? = null
//
//            try {
//                input = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
//                pdfRenderer = PdfRenderer(input)
//
//                for (i in 0 until pdfRenderer.pageCount) {
//                    val page = pdfRenderer.openPage(i)
//                    val bitmap = android.graphics.Bitmap.createBitmap(
//                        page.width,
//                        page.height,
//                        android.graphics.Bitmap.Config.ARGB_8888
//                    )
//                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
//                    bitmapList.add(bitmap)
//                    page.close()
//                }
//            } finally {
//                pdfRenderer?.close()
//                input?.close()
//            }
//
//            bitmapList
//        }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//private fun PdfReaderScreen(
//    pages: List<android.graphics.Bitmap>,
//    isLoading: Boolean,
//    error: String?,
//    onBack: () -> Unit
//) {
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("PDF Reader") },
//                navigationIcon = {
//                    IconButton(onClick = onBack) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
//                    }
//                }
//            )
//        }
//    ) { paddingValues ->
//        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
//            when {
//                isLoading -> {
//                    androidx.compose.material3.CircularProgressIndicator(
//                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
//                    )
//                }
//                error != null -> {
//                    Text(
//                        text = "Error: $error",
//                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
//                    )
//                }
//                pages.isEmpty() -> {
//                    Text(
//                        text = "No pages to display",
//                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
//                    )
//                }
//                else -> {
//                    LazyColumn(
//                        modifier = Modifier.fillMaxSize()
//                    ) {
//                        itemsIndexed(pages) { index, bitmap ->
//                            Image(
//                                bitmap = bitmap.asImageBitmap(),
//                                contentDescription = "Page ${index + 1}",
//                                modifier = Modifier.fillMaxSize()
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//}