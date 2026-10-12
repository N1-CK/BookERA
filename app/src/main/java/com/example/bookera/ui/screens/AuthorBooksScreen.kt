package com.example.bookera.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookera.data.plugin.AuthorSearchResult
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.ui.viewmodel.BookViewModel
import com.example.bookera.ui.theme.SystemBars
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorBooksScreen(author: AuthorSearchResult, viewModel: BookViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val books by viewModel.allBooks.collectAsState()
    val store = com.example.bookera.data.local.ReadingActivityStore.get(androidx.compose.ui.platform.LocalContext.current)
    val revision by store.revision.collectAsState()
    val progress = remember(revision) { store.visits().associate { it.bookId to it.progress } }
    var results by remember(author.id) { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var loading by remember(author.id) { mutableStateOf(true) }
    var error by remember(author.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(author.id, author.pluginId, attempt) {
        loading = true; error = null
        try { results = viewModel.booksByAuthor(author).getOrThrow() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Не удалось загрузить книги. ${e.message.orEmpty()}" }
        finally { loading = false }
    }
    SystemBars(HomePaper)
    val byId = remember(books) { books.associateBy { it.id } }
    Scaffold(containerColor = HomePaper, topBar = {
        TopAppBar(title = { Text("Книги автора") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(author.name, style = MaterialTheme.typography.headlineSmall) }
            item { Text(if (loading) "Загружаем книги…" else "${results.size} изданий · Флибуста", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { attempt++ }) { Text("Повторить") } } }
            if (!loading && error == null && results.isEmpty()) item { Text("Источник не вернул книги этого автора") }
            items(results, key = { it.id }) { result ->
                val id = "${result.pluginId}:${result.id}".hashCode().toLong()
                byId[id]?.let { book ->
                    BookCard(book, onClick = { onOpen(id) }, onFavorite = { viewModel.toggleFavorite(id, !book.isFavorite) },
                        onMissingCover = { viewModel.resolveVisibleCover(book) }, progress = progress[id])
                }
            }
        }
    }
}
