package com.example.bookera.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookera.data.model.Book
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.data.plugin.DownloadLink
import com.example.bookera.ui.viewmodel.BookViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(bookId: Long, viewModel: BookViewModel, onNavigateBack: () -> Unit, onReadBook: (Long) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val plugins by viewModel.availablePlugins.collectAsState()
    val enabled by viewModel.enabledSources.collectAsState()
    val error by viewModel.error.collectAsState()
    var book by remember(bookId) { mutableStateOf<Book?>(null) }
    var matches by remember { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var links by remember { mutableStateOf<List<DownloadLink>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val flibusta = plugins.firstOrNull { it.id == "flibusta" && it.id in enabled }

    LaunchedEffect(bookId) { book = viewModel.getBookById(bookId) }
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("О книге") }, navigationIcon = {
        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
    }) }) { padding ->
        val current = book
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(current.coverUrl, current.title, Modifier.size(160.dp, 230.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(current.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { rating ->
                        TextButton(onClick = { book = current.copy(rating = rating.toFloat()); viewModel.updateRating(bookId, rating.toFloat()) }) {
                            Text(if (rating <= current.rating) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                Text("Моя оценка: ${current.rating.toInt()} / 5")
                current.description?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.fillMaxWidth()) }
                OutlinedButton(onClick = { book = current.copy(isFavorite = !current.isFavorite); viewModel.toggleFavorite(bookId, !current.isFavorite) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (current.isFavorite) "♥ В избранном" else "♡ В избранное")
                }
                if (current.isDownloaded && current.localFilePath != null) {
                    Button(onClick = { onReadBook(bookId) }, modifier = Modifier.fillMaxWidth()) { Text("Читать") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Удалить скачанный файл") }
                } else {
                    if (flibusta == null) Text("Для скачивания включи Флибусту в Профиль → Источники")
                    OutlinedButton(onClick = {
                        val source = flibusta ?: return@OutlinedButton
                        busy = true; message = null; matches = emptyList(); links = emptyList()
                        scope.launch {
                            val direct = current.downloadUrl.takeIf { it?.contains("flibusta.is/b/") == true }
                            if (direct != null) {
                                links = source.findAllDownloadLinks(direct).filter { it.format.equals("fb2", true) }
                                if (links.isEmpty()) message = "FB2 недоступен для этой книги"
                            } else {
                                source.search(current.title).onSuccess { found ->
                                    matches = found.filter { it.title.contains(current.title, ignoreCase = true) || current.title.contains(it.title, ignoreCase = true) }.take(15)
                                    if (matches.isEmpty()) message = "Совпадений на Флибусте нет"
                                }.onFailure { message = it.message ?: "Ошибка источника" }
                            }
                            busy = false
                        }
                    }, enabled = flibusta != null && !busy && !downloading, modifier = Modifier.fillMaxWidth()) { Text("Найти FB2") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message != null) Text(message ?: "", color = MaterialTheme.colorScheme.error)
                    if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    if (matches.isNotEmpty()) {
                        Text("Выбери точное издание:", style = MaterialTheme.typography.titleMedium)
                        matches.forEach { result ->
                            Card(modifier = Modifier.fillMaxWidth().clickable {
                                val source = flibusta ?: return@clickable
                                busy = true; links = emptyList(); message = null
                                scope.launch {
                                    links = source.findAllDownloadLinks(result.downloadUrl).filter { it.format.equals("fb2", true) }
                                    if (links.isEmpty()) message = "FB2 недоступен для выбранного издания"
                                    busy = false
                                }
                            }) { Text("${result.title} — ${result.author}", Modifier.padding(16.dp)) }
                        }
                    }
                    links.forEach { link ->
                        Button(onClick = {
                            downloading = true; progress = 0f; message = null
                            viewModel.downloadBook(pluginId = "flibusta", url = link.url,
                                fileName = "${bookId}_book.fb2", bookId = bookId,
                                onProgress = { progress = it }, onComplete = { success ->
                                    downloading = false
                                    if (success) scope.launch { book = viewModel.getBookById(bookId) }
                                    else message = "Не удалось скачать книгу"
                                })
                        }, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text("Скачать FB2") }
                    }
                    if (downloading) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Text("${(progress * 100).toInt()} %") }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить скачанную книгу?") },
        text = { Text("Файл будет удалён с устройства. Оценка и избранное сохранятся.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            viewModel.deleteDownload(bookId) { success -> if (success) scope.launch { book = viewModel.getBookById(bookId) } }
        }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
}
