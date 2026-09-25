package com.example.bookera.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.bookera.data.model.Book
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.ui.navigation.Routes
import com.example.bookera.ui.viewmodel.BookViewModel

@Composable
fun BookListScreen(viewModel: BookViewModel, navController: NavHostController, library: Boolean = false) {
    val books by viewModel.books.collectAsState()
    val favorites by viewModel.favoriteBooks.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val featured by viewModel.featured.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val error by viewModel.error.collectAsState()
    val lastRead by viewModel.lastRead.collectAsState()
    var onlyDownloads by remember { mutableStateOf(false) }
    var onlyFavorites by remember { mutableStateOf(false) }
    val downloaded = books.filter { it.isDownloaded && it.localFilePath != null }
    val visibleLibrary = books.filter { (!onlyDownloads || it.isDownloaded) && (!onlyFavorites || it.isFavorite) }
    val open: (Long) -> Unit = { navController.navigate(Routes.bookDetail(it)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(if (library) "Моя библиотека" else "Читать сейчас", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(if (library) "Книги и оценки на этом устройстве" else "Найди книгу, которая захватит", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!library) {
            item {
                val recent = downloaded.firstOrNull { it.id == lastRead } ?: downloaded.firstOrNull()
                if (recent != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("ПРОДОЛЖИТЬ ЧТЕНИЕ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(recent.coverUrl, recent.title, Modifier.size(76.dp, 106.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(recent.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text(recent.author, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(onClick = { viewModel.rememberReading(recent.id); navController.navigate(Routes.reader(recent.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Продолжить читать") }
                        }
                    }
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text("Твоя история начнётся здесь. Найди книгу и скачай FB2 для чтения без сети.", Modifier.padding(20.dp))
                    }
                }
            }
        }
        if (library) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onlyDownloads && !onlyFavorites, onClick = { onlyDownloads = false; onlyFavorites = false }, label = { Text("Все") })
                    FilterChip(selected = onlyDownloads, onClick = { onlyDownloads = !onlyDownloads; onlyFavorites = false }, label = { Text("Скачанные") })
                    FilterChip(selected = onlyFavorites, onClick = { onlyFavorites = !onlyFavorites; onlyDownloads = false }, label = { Text("Избранное") })
                }
            }
            if (visibleLibrary.isEmpty()) item { Text("Здесь пока пусто. Найди книгу на главной.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(visibleLibrary, key = { it.id }) { book -> BookRow(book, { open(book.id) }) }
        } else {
            item {
                OutlinedTextField(value = query, onValueChange = viewModel::searchBooks,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Поиск по названию или автору") })
            }
            if (query.isNotBlank()) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != null) item { Text(error ?: "", color = MaterialTheme.colorScheme.error) }
                if (!loading && results.isEmpty() && error == null) item { Text("Ничего не найдено в подключённых источниках") }
                items(results, key = { "${it.sourceId}:${it.id}" }) { result ->
                    SearchRow(result) { viewModel.openSearchResult(result, open) }
                }
            } else {
                if (downloaded.isNotEmpty()) {
                    item { SectionTitle("Мои книги", "Все", { navController.navigate(Routes.LIBRARY) }) }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(downloaded.take(12), key = { it.id }) { book -> BookTile(book, { open(book.id) }) }
                    } }
                }
                if (favorites.isNotEmpty()) {
                    item { SectionTitle("Избранное", "Все", { navController.navigate(Routes.LIBRARY) }) }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favorites.take(12), key = { it.id }) { book -> BookTile(book, { open(book.id) }) }
                    } }
                }
                if (featured.isNotEmpty()) {
                    item { SectionTitle("Подборка книг", "", {}) }
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(featured, key = { "${it.sourceId}:${it.id}" }) { result ->
                            Column(Modifier.width(130.dp).clickable { viewModel.openSearchResult(result, open) }) {
                                Cover(result.coverUrl, result.title, Modifier.size(130.dp, 185.dp))
                                Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(result.author, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } }
                }
                item { Text("Выбор за тобой", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item { Text("Ищи книги по названию или автору. Open Library показывает каталог; доступные FB2 ищи через источник Флибуста.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (action.isNotEmpty()) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
fun Cover(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxSize()) {}
        AsyncImage(model = url, contentDescription = "Обложка: $title", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun BookTile(book: Book, onClick: () -> Unit) {
    Column(Modifier.width(130.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Cover(book.coverUrl, book.title, Modifier.size(130.dp, 185.dp))
        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(book.author, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book.coverUrl, book.title, Modifier.size(70.dp, 96.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(book.author, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (book.isDownloaded) "Скачана · ★ ${book.rating}" else "В библиотеке · ★ ${book.rating}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SearchRow(result: BookSearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(result.coverUrl, result.title, Modifier.size(64.dp, 88.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(result.author, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (result.sourceId == "flibusta") "Флибуста · проверь FB2" else "Open Library · каталог", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
