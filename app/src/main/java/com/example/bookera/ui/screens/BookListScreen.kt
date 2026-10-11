package com.example.bookera.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.bookera.data.model.SearchField
import com.example.bookera.data.local.LocalSettings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.bookera.ui.navigation.Routes
import com.example.bookera.ui.viewmodel.BookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(viewModel: BookViewModel, navController: NavHostController, settings: LocalSettings, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val navy = colors.onSurface
    val mist = colors.background
    val accent = colors.primary
    val options by viewModel.searchOptions.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val error by viewModel.error.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val books by viewModel.books.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val favorites by viewModel.favoriteBooks.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var pendingDeletion by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBook(it) { result ->
            Toast.makeText(context, result.fold({ "Книга добавлена" }, { it.message ?: "Не удалось открыть книгу" }), Toast.LENGTH_LONG).show()
        } }
    }
    val importAction = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/xml", "application/octet-stream", "*/*")) }
    val visible = when (filter) {
        1 -> books.filter { it.isDownloaded }
        2 -> books.filter { it.isFavorite }
        3 -> books.filter { it.isRead }
        else -> books
    }
    Scaffold(modifier = modifier, containerColor = mist,
        bottomBar = {
            NavigationBar(containerColor = colors.surface) {
                val navColors = NavigationBarItemDefaults.colors(selectedIconColor = accent, selectedTextColor = accent,
                    unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant, indicatorColor = colors.primaryContainer)
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.MenuBook, null) }, label = { Text("Библиотека") }, colors = navColors)
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("Профиль") }, colors = navColors)
            }
        }
    ) { padding ->
        AnimatedContent(targetState = tab, label = "section", modifier = Modifier.padding(padding)) { destination ->
            if (destination == 1) ProfileScreen(books = allBooks, favorites = favorites.size, onImport = importAction,
                onOpenLibrary = { tab = 0 }, onOpenBook = { navController.navigate(Routes.bookDetail(it)) },
                onSetRead = viewModel::setRead, settings = settings, notes = notes,
                onDeleteNote = viewModel::deleteNote,
                onOpenNote = { note ->
                    val key = if (note.locationType == "pdf") "page_${note.bookId}" else "position_${note.bookId}"
                    context.getSharedPreferences("reader_settings", 0).edit().putInt(key, note.position).apply()
                    navController.navigate(Routes.reader(note.bookId))
                })
            else Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("BookERA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = navy)
                    Spacer(Modifier.weight(1f))
                    Text("Ваша библиотека", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = viewModel::searchBooks, modifier = Modifier.weight(1f),
                        placeholder = { Text(when (options.field) { SearchField.TITLE -> "Название книги"; SearchField.AUTHOR -> "Имя автора"; else -> "Название или автор" }) },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.Tune, "Параметры поиска", tint = accent) }
                }
                if (options.field != SearchField.ALL || options.author.isNotBlank() || options.year != null) {
                    TextButton(onClick = { showFilters = true }, modifier = Modifier.padding(horizontal = 18.dp)) {
                        Text(listOfNotNull(options.field.label, options.author.takeIf { it.isNotBlank() }, options.year?.toString()).joinToString(" · "))
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Все", "Мои книги", "Избранное", "Прочитано").forEachIndexed { index, label ->
                        FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface, labelColor = navy,
                                selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.onPrimaryContainer))
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                error?.let { message -> Text(message, color = colors.error, modifier = Modifier.padding(horizontal = 18.dp)) }
                if (visible.isEmpty()) Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank() && filter == 3) "Прочитанные книги появятся здесь" else if (query.isBlank()) "Откройте профиль, чтобы добавить книгу, или найдите её в каталоге" else "Совпадений пока нет",
                        color = navy, style = MaterialTheme.typography.titleMedium)
                } else LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
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
                                onFavorite = { viewModel.toggleFavorite(book.id, !book.isFavorite) },
                                onMissingCover = { viewModel.resolveCover(book) })
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        var field by remember { mutableStateOf(options.field) }
        var author by remember { mutableStateOf(options.author) }
        var year by remember { mutableStateOf(options.year?.toString().orEmpty()) }
        val yearValid = year.isBlank() || year.toIntOrNull()?.let { it in 1..2100 } == true
        AlertDialog(onDismissRequest = { showFilters = false }, title = { Text("Параметры поиска") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Искать запрос по")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchField.entries.forEach { item -> FilterChip(field == item, { field = item }, label = { Text(item.label) }) }
                }
                OutlinedTextField(author, { author = it }, label = { Text("Дополнительно: автор") }, singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Год публикации") },
                    singleLine = true, isError = !yearValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Фильтры применяются к библиотеке и каталогам. Книги без известного года исключаются при фильтре по году.",
                    style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = yearValid, onClick = {
                viewModel.applySearch(options.copy(field = field, author = author.trim(), year = year.toIntOrNull()))
                showFilters = false
            }) { Text("Применить") } },
            dismissButton = { TextButton(onClick = { viewModel.applySearch(options.copy(field = SearchField.ALL, author = "", year = null)); showFilters = false }) { Text("Сбросить") } })
    }
    pendingDeletion?.let { book ->
        AlertDialog(onDismissRequest = { pendingDeletion = null }, title = { Text("Удалить книгу?") },
            text = { Text("${book.title} будет удалена из библиотеки. Импортированный файл также удалится.") },
            confirmButton = { TextButton(onClick = { viewModel.removeBook(book); pendingDeletion = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Отмена") } })
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit, onFavorite: () -> Unit,
    onMissingCover: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val navy = colors.onSurface
    val accent = colors.primary
    LaunchedEffect(book.id, book.coverUrl) { if (book.coverUrl.isNullOrBlank()) onMissingCover() }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArtwork(book, Modifier.size(86.dp, 120.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(13.dp))
                book.publishedYear?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                Text(if (book.isRead) "✓ Прочитано" else if (book.isDownloaded) "В библиотеке · Читать" else "Каталог · Подробнее",
                    style = MaterialTheme.typography.labelSmall, color = accent)
            }
            IconButton(onClick = onFavorite) { Icon(if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                "Избранное", tint = accent) }
        }
    }
}

@Composable
fun CoverArtwork(book: Book, settings: LocalSettings, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(book.title.take(1).uppercase(), color = navy, style = MaterialTheme.typography.headlineLarge)
        if (!book.coverUrl.isNullOrBlank()) AsyncImage(model = book.coverUrl, contentDescription = "Обложка ${book.title}",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
