package com.example.bookera.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.bookera.data.model.Book
import com.example.bookera.ui.navigation.Routes
import com.example.bookera.ui.viewmodel.BookViewModel

private val navy = Color(0xFF172837)
private val mist = Color(0xFFF5F6F5)
private val accent = Color(0xFFBB7958)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(viewModel: BookViewModel, navController: NavHostController, modifier: Modifier = Modifier) {
    val books by viewModel.books.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val favorites by viewModel.favoriteBooks.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    var pendingDeletion by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBook(it) { result ->
            Toast.makeText(context, result.fold({ "Книга добавлена" }, { it.message ?: "Не удалось открыть книгу" }), Toast.LENGTH_LONG).show()
        } }
    }
    val importAction = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/xml", "application/octet-stream", "*/*")) }
    val visible = when (filter) {
        1 -> books.filter { it.isDownloaded }
        2 -> favorites
        else -> books
    }
    Scaffold(modifier = modifier, containerColor = mist,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("▤") }, label = { Text("Библиотека") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("◉") }, label = { Text("Профиль") })
            }
        }, floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = importAction, containerColor = navy,
                contentColor = Color.White, text = { Text("Добавить книгу") }, icon = { Text("＋") })
        }
    ) { padding ->
        AnimatedContent(targetState = tab, label = "section", modifier = Modifier.padding(padding)) { destination ->
            if (destination == 1) ProfileScreen(books = allBooks, favorites = favorites.size, onImport = importAction,
                onOpenLibrary = { tab = 0 })
            else Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().background(navy).padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp)) {
                    Text("BOOKERA  ·  ЛИЧНАЯ БИБЛИОТЕКА", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD5BDB0))
                    Spacer(Modifier.height(9.dp))
                    Text("Книги, к которым\nхочется вернуться", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold, color = Color.White, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(value = query, onValueChange = viewModel::searchBooks, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Название или автор") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        shape = RoundedCornerShape(18.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Все", "Мои книги", "Избранное").forEachIndexed { index, label ->
                        FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = navy, selectedLabelColor = Color.White))
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                if (visible.isEmpty()) Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "Добавьте книгу или найдите её в каталоге" else "Совпадений пока нет",
                        color = navy, style = MaterialTheme.typography.titleMedium)
                } else LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 95.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visible, key = { it.id }) { book ->
                        SwipeToDismissBox(state = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
                            when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> { viewModel.toggleFavorite(book.id, !book.isFavorite); false }
                                SwipeToDismissBoxValue.EndToStart -> { pendingDeletion = book; false }
                                else -> false
                            }
                        }), backgroundContent = {
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                                .background(if (book.isFavorite) accent else navy).padding(20.dp),
                                contentAlignment = Alignment.CenterEnd) { Text("Избранное  •  Удалить", color = Color.White) }
                        }) {
                            BookCard(book, onClick = { navController.navigate(Routes.bookDetail(book.id)) },
                                onFavorite = { viewModel.toggleFavorite(book.id, !book.isFavorite) })
                        }
                    }
                }
            }
        }
    }
    pendingDeletion?.let { book ->
        AlertDialog(onDismissRequest = { pendingDeletion = null }, title = { Text("Удалить книгу?") },
            text = { Text("${book.title} будет удалена из библиотеки. Импортированный файл также удалится.") },
            confirmButton = { TextButton(onClick = { viewModel.removeBook(book); pendingDeletion = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Отмена") } })
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArtwork(book, Modifier.size(86.dp, 120.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF69727B), maxLines = 1)
                Spacer(Modifier.height(13.dp))
                Text(if (book.isDownloaded) "В библиотеке · Читать" else "Каталог · Подробнее",
                    style = MaterialTheme.typography.labelSmall, color = accent)
            }
            IconButton(onClick = onFavorite) { Icon(if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                "Избранное", tint = accent) }
        }
    }
}

@Composable
fun CoverArtwork(book: Book, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFFB7C4C5)), contentAlignment = Alignment.Center) {
        Text(book.title.take(1).uppercase(), color = navy, style = MaterialTheme.typography.headlineLarge)
        if (!book.coverUrl.isNullOrBlank()) AsyncImage(model = book.coverUrl, contentDescription = "Обложка ${book.title}",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
