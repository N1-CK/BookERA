//// EpubReaderPlugin.kt
//package com.example.bookera.data.reader
//
//import android.content.Context
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.material3.TopAppBar
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import nl.siegmann.epublib.domain.Book
//import nl.siegmann.epublib.epub.EpubReader
//import java.io.FileInputStream
//
//class EpubReaderPlugin : ReaderPlugin {
//    override val supportedFormats = listOf("epub", "epub3")
//    override val name = "EPUB Reader"
//
//    @Composable
//    override fun ReaderScreen(filePath: String, onBack: () -> Unit) {
//        val context = LocalContext.current
//        var chapters by remember { mutableStateOf<List<ChapterContent>>(emptyList()) }
//        var isLoading by remember { mutableStateOf(true) }
//        var error by remember { mutableStateOf<String?>(null) }
//        var bookTitle by remember { mutableStateOf("EPUB Reader") }
//
//        LaunchedEffect(filePath) {
//            try {
//                val (title, content) = withContext(Dispatchers.IO) {
//                    readEpub(context, filePath)
//                }
//                bookTitle = title
//                chapters = content
//                isLoading = false
//            } catch (e: Exception) {
//                error = e.message
//                isLoading = false
//            }
//        }
//
//        EpubReaderScreen(
//            title = bookTitle,
//            chapters = chapters,
//            isLoading = isLoading,
//            error = error,
//            onBack = onBack
//        )
//    }
//
//    private fun readEpub(context: Context, filePath: String): Pair<String, List<ChapterContent>> {
//        val file = File(filePath)
//        if (!file.exists()) return "Unknown" to emptyList()
//
//        val epubReader = EpubReader()
//        val book = epubReader.readEpub(FileInputStream(file))
//
//        val chapters = mutableListOf<ChapterContent>()
//        book.contents.forEach { resource ->
//            val content = String(resource.data)
//            chapters.add(ChapterContent(
//                title = resource.title ?: "Chapter ${chapters.size + 1}",
//                content = content
//            ))
//        }
//
//        return book.title to chapters
//    }
//}
//
//data class ChapterContent(
//    val title: String,
//    val content: String
//)
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//private fun EpubReaderScreen(
//    title: String,
//    chapters: List<ChapterContent>,
//    isLoading: Boolean,
//    error: String?,
//    onBack: () -> Unit
//) {
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text(title.take(30)) },
//                navigationIcon = {
//                    IconButton(onClick = onBack) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
//                    }
//                }
//            )
//        }
//    ) { paddingValues ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(paddingValues)
//        ) {
//            when {
//                isLoading -> {
//                    androidx.compose.material3.CircularProgressIndicator(
//                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
//                    )
//                }
//                error != null -> {
//                    Text(
//                        text = "Error: $error",
//                        modifier = Modifier
//                            .padding(16.dp)
//                            .align(androidx.compose.ui.Alignment.CenterHorizontally)
//                    )
//                }
//                chapters.isEmpty() -> {
//                    Text(
//                        text = "No content to display",
//                        modifier = Modifier
//                            .padding(16.dp)
//                            .align(androidx.compose.ui.Alignment.CenterHorizontally)
//                    )
//                }
//                else -> {
//                    LazyColumn(
//                        modifier = Modifier.fillMaxSize()
//                    ) {
//                        items(chapters) { chapter ->
//                            Column(
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .padding(16.dp)
//                            ) {
//                                Text(
//                                    text = chapter.title,
//                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
//                                    modifier = Modifier.padding(bottom = 8.dp)
//                                )
//                                Text(
//                                    text = chapter.content,
//                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
//                                )
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//}