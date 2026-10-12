package com.example.bookera.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarOutline
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
import com.example.bookera.ui.theme.SystemBars
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(bookId: Long, viewModel: BookViewModel, onNavigateBack: () -> Unit,
    onReadBook: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins by viewModel.availablePlugins.collectAsState()
    val books by viewModel.allBooks.collectAsState()
    var initial by remember(bookId) { mutableStateOf<Book?>(null) }
    val book = books.find { it.id == bookId } ?: initial
    var options by remember(bookId) { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var links by remember(bookId) { mutableStateOf<List<Pair<String, DownloadLink>>>(emptyList()) }
    var loading by remember(bookId) { mutableStateOf(false) }
    var progress by remember(bookId) { mutableFloatStateOf(0f) }
    var message by remember(bookId) { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf(false) }
    LaunchedEffect(bookId) { initial = viewModel.getBookById(bookId); initial?.let { viewModel.resolveCover(it) } }
    SystemBars(HomePaper)

    fun download(source: String, link: DownloadLink) {
        loading = true; progress = 0f; message = null
        viewModel.downloadBook(source, link.url, "book_$bookId.${link.format.lowercase()}", bookId,
            onProgress = { progress = it }, onComplete = { success ->
                loading = false
                if (success) scope.launch { initial = viewModel.getBookById(bookId) }
                else message = "Не удалось скачать файл. Проверьте доступ к источнику."
                Toast.makeText(context, if (success) "Книга готова к чтению" else "Не удалось скачать книгу", Toast.LENGTH_LONG).show()
            })
    }
    fun loadLinks(current: Book) {
        loading = true; message = null; links = emptyList()
        scope.launch {
            try {
                links = viewModel.downloadLinks(current)?.let { (source, found) -> found.map { source to it } }.orEmpty()
                if (links.isEmpty()) message = "Источник не отдал доступный файл. Попробуйте поискать другое издание."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Источник сейчас недоступен. Попробуйте позже." }
            finally { loading = false }
        }
    }
    Scaffold(modifier = modifier, containerColor = HomePaper, topBar = {
        TopAppBar(title = { Text("О книге") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = HomePaper), navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        })
    }) { padding ->
        val current = book
        if (current == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Lavender, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) { CoverArtwork(current, Modifier.size(138.dp, 196.dp)) }
            }
            Spacer(Modifier.height(22.dp))
            Text(current.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HomeInk)
            Text(current.author, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            current.publishedYear?.let { Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.weight(1f).clickable { rating = true }, shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Lime)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.StarOutline, null, tint = AccentInk, modifier = Modifier.size(30.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(if (current.rating > 0) "${(current.rating * 2).toInt()} / 10" else "Оценить книгу", color = AccentInk,
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Моё впечатление", color = AccentInk, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                FilledTonalIconButton(onClick = { viewModel.toggleFavorite(current.id, !current.isFavorite) }, modifier = Modifier.size(60.dp)) {
                    Icon(if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Избранное", tint = Electric)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (current.isDownloaded) Button(onClick = { onReadBook(current.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Читать", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { viewModel.setRead(current.id, !current.isRead) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(if (current.isRead) "✓ Прочитано · отменить отметку" else "Отметить как прочитанную")
            }
            current.finishedAt?.takeIf { current.isRead }?.let {
                Text("Завершено: ${java.text.DateFormat.getDateInstance().format(java.util.Date(it))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            current.description?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.fillMaxWidth().padding(vertical = 22.dp), color = HomeInk) }
            if (!current.isDownloaded) {
                Text("Доступные издания", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start).padding(top = 20.dp, bottom = 10.dp))
                if (!current.downloadUrl.isNullOrBlank()) Button(onClick = { loadLinks(current) }, enabled = !loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Показать форматы этого издания") }
                OutlinedButton(onClick = {
                    loading = true; message = null; options = emptyList(); links = emptyList()
                    scope.launch {
                        try {
                            options = viewModel.findEditions(current.title).filter { it.downloadUrl.isNotBlank() }.take(30)
                            if (options.isEmpty()) message = "Файлы не найдены. Попробуйте другое название или добавьте свой файл в профиле."
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = "Не удалось найти издания" }
                        finally { loading = false }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("Найти в других источниках") }
                if (loading) {
                    if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp)) }
                options.forEach { option ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(enabled = !loading) {
                        val optionBook = Book(id = "${option.pluginId}:${option.id}".hashCode().toLong(), title = option.title,
                            author = option.author, downloadUrl = option.downloadUrl)
                        loadLinks(optionBook)
                    }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(option.title, fontWeight = FontWeight.SemiBold)
                            Text("${option.author} · ${plugins.find { it.id == option.pluginId }?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                links.forEach { (source, link) ->
                    FilledTonalButton(onClick = { download(source, link) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text("Скачать ${link.format.uppercase()} · ${plugins.find { it.id == source }?.name.orEmpty()}")
                    }
                }
            }
        }
    }
    if (rating && book != null) RatingSheet(book.title, book.rating, onDismiss = { rating = false }, onSave = {
        viewModel.updateRating(bookId, it); rating = false
    })
}
