package com.example.bookera.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookera.data.model.Book
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.data.plugin.DownloadLink
import com.example.bookera.ui.viewmodel.BookViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(bookId: Long, viewModel: BookViewModel, onNavigateBack: () -> Unit,
    onReadBook: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins by viewModel.availablePlugins.collectAsState()
    var book by remember(bookId) { mutableStateOf<Book?>(null) }
    var options by remember { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var links by remember { mutableStateOf<List<Pair<String, DownloadLink>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookId) { book = viewModel.getBookById(bookId) }

    fun download(pluginId: String, url: String, format: String) {
        loading = true
        progress = 0f
        viewModel.downloadBook(pluginId, url, "book_$bookId.${format.lowercase()}", bookId,
            onProgress = { progress = it }, onComplete = { success ->
                loading = false
                if (success) scope.launch { book = viewModel.getBookById(bookId) }
                Toast.makeText(context, if (success) "Книга готова к чтению" else "Не удалось скачать книгу", Toast.LENGTH_LONG).show()
            })
    }
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("О книге") }, navigationIcon = {
        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
    }) }) { padding ->
        val current = book
        if (current == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            CoverArtwork(current, Modifier.size(174.dp, 248.dp))
            Spacer(Modifier.height(22.dp))
            Text(current.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(current.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    viewModel.toggleFavorite(current.id, !current.isFavorite)
                    book = current.copy(isFavorite = !current.isFavorite)
                }) { Icon(if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Избранное", tint = Color(0xFFBB7958)) }
                Text("Моя оценка")
                (1..5).forEach { star -> Text("★", Modifier.clickable {
                    viewModel.updateRating(current.id, star.toFloat()); book = current.copy(rating = star.toFloat())
                }.padding(3.dp), color = if (current.rating >= star) Color(0xFFBB7958) else Color.LightGray) }
            }
            Spacer(Modifier.height(16.dp))
            if (current.isDownloaded) Button(onClick = { onReadBook(current.id) }, modifier = Modifier.fillMaxWidth()) { Text("Читать") }
            current.description?.let { Text(it, Modifier.padding(vertical = 18.dp)) }
            if (!current.isDownloaded) {
                Text("Доступные издания", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Start).padding(top = 20.dp, bottom = 10.dp))
                val directUrl = current.downloadUrl
                if (directUrl?.startsWith("https://www.gutenberg.org/") == true) {
                    Button(onClick = { download("gutenberg", directUrl, if (directUrl.contains("epub")) "epub" else "txt") },
                        enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Скачать из Project Gutenberg") }
                }
                OutlinedButton(onClick = {
                    loading = true; message = null; options = emptyList(); links = emptyList()
                    scope.launch {
                        val found = plugins.filter { it.id != "openlibrary" }.map { plugin ->
                            async {
                                withTimeoutOrNull(8_000L) { plugin.search(current.title).getOrNull().orEmpty() }
                                    .orEmpty().map { it.copy(pluginId = plugin.id) }
                            }
                        }.awaitAll().flatten()
                        options = found.filter { it.downloadUrl.isNotBlank() }.take(20)
                        if (options.isEmpty()) message = "Свободных файлов не найдено. Можно добавить свой файл."
                        loading = false
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("Найти версии для скачивания") }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                options.forEach { option ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                        if (option.pluginId == "gutenberg") {
                            download("gutenberg", option.downloadUrl, if (option.downloadUrl.contains("epub")) "epub" else "txt")
                        } else {
                            loading = true
                            scope.launch {
                                links = plugins.find { it.id == option.pluginId }?.findAllDownloadLinks(option.downloadUrl)
                                    .orEmpty().map { option.pluginId to it }
                                if (links.isEmpty()) message = "Ссылки для этой версии недоступны"
                                loading = false
                            }
                        }
                    }) { Column(Modifier.padding(14.dp)) {
                        Text(option.title, fontWeight = FontWeight.SemiBold)
                        Text("${option.author} · ${plugins.find { it.id == option.pluginId }?.name.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall)
                    } }
                }
                links.forEach { (source, link) ->
                    TextButton(onClick = { download(source, link.url, link.format) }, enabled = !loading) {
                        Text("Скачать ${link.format.uppercase()}")
                    }
                }
            }
        }
    }
}
