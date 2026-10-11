package com.example.bookera.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.*
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
import com.example.bookera.data.local.LocalSettings
import com.example.bookera.data.local.ReadingActivityStore
import com.example.bookera.data.model.Book
import com.example.bookera.data.model.SearchField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.bookera.ui.navigation.Routes
import com.example.bookera.ui.viewmodel.BookViewModel
import com.example.bookera.ui.theme.SystemBars

private val navy: Color @Composable get() = HomeInk
private val mist: Color @Composable get() = HomePaper
private val accent: Color @Composable get() = Electric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(viewModel: BookViewModel, navController: NavHostController, settings: LocalSettings, modifier: Modifier = Modifier) {
    val allBooks by viewModel.allBooks.collectAsState()
    val favorites by viewModel.favoriteBooks.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val statuses by viewModel.sourceStatuses.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    val store = remember { ReadingActivityStore.get(context) }
    val options by viewModel.searchOptions.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val lastRead by settings.lastRead.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val openNote: (com.example.bookera.data.local.reader.ReaderNote) -> Unit = { note ->
        val key = if (note.locationType == "pdf") "page_${note.bookId}" else "position_${note.bookId}"
        context.getSharedPreferences("reader_settings", 0).edit().putInt(key, note.position).apply()
        navController.navigate(Routes.reader(note.bookId))
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var passages by rememberSaveable { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBook(it) { result ->
            Toast.makeText(context, result.fold({ "Книга добавлена" }, { it.message ?: "Не удалось открыть книгу" }), Toast.LENGTH_LONG).show()
        } }
    }
    val importAction = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/xml", "application/octet-stream", "*/*")) }
    val byId = remember(allBooks) { allBooks.associateBy { it.id } }
    val visible = if (options.isActive) (allBooks.filter { it.isDownloaded && options.matches(it) } +
        results.mapNotNull { r -> byId["${r.pluginId}:${r.id}".hashCode().toLong()] }).distinctBy { it.id }
        else when (filter) {
            1 -> allBooks.filter { it.isDownloaded }
            2 -> favorites
            3 -> allBooks.filter { it.isRead }
            else -> allBooks.filter { it.isDownloaded || it.isFavorite || it.isRead || it.rating > 0f }
        }
    val sourceNames = remember(results, statuses) { results.associate {
        "${it.pluginId}:${it.id}".hashCode().toLong() to statuses.find { status -> status.id == it.pluginId }?.name.orEmpty()
    } }
    SystemBars(mist, MaterialTheme.colorScheme.surface)
    BackHandler(enabled = passages || tab != 0) { if (passages) passages = false else { tab = 0; viewModel.searchBooks("") } }
    Scaffold(modifier = modifier.imePadding(), containerColor = mist,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val navColors = NavigationBarItemDefaults.colors(selectedIconColor = accent, selectedTextColor = accent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant, indicatorColor = Lavender)
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0; passages = false; viewModel.searchBooks("") },
                    icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("Главная") }, colors = navColors)
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1; passages = false },
                    icon = { Icon(Icons.Outlined.AutoStories, null) }, label = { Text("Книги") }, colors = navColors)
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2; passages = false; viewModel.searchBooks("") },
                    icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("Профиль") }, colors = navColors)
            }
        }
    ) { padding ->
        AnimatedContent(targetState = if (passages) 3 else tab, label = "section", modifier = Modifier.padding(padding)) { destination ->
            when (destination) {
                0 -> HomeScreen(allBooks, store, notes, lastRead, onRead = { navController.navigate(Routes.reader(it)) },
                    onSearch = { tab = 1 }, onLibrary = { tab = 1; filter = 1 }, onPassages = { passages = true }, onImport = importAction)
                2 -> ProfileScreen(books = allBooks.filter { it.isDownloaded || it.isRead || it.isFavorite || it.rating > 0f },
                    favorites = favorites.size, onImport = importAction, onOpenLibrary = { tab = 1 },
                    onOpenBook = { navController.navigate(Routes.bookDetail(it)) }, onSetRead = viewModel::setRead, settings = settings, notes = notes, onOpenNote = openNote, onDeleteNote = viewModel::deleteNote)
                3 -> PassagesScreen(allBooks, notes, onBack = { passages = false }, onOpenNote = openNote, onDeleteNote = viewModel::deleteNote)
                else -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Новая история", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = navy, modifier = Modifier.weight(1f))
                            IconButton(onClick = { sources = true }) { Icon(Icons.Outlined.Tune, "Настроить источники", tint = accent) }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = query, onValueChange = viewModel::searchBooks, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(when (options.field) { SearchField.TITLE -> "Название книги"; SearchField.AUTHOR -> "Имя автора"; else -> "Название или автор" }) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                            trailingIcon = { if (options.isActive) IconButton(onClick = { viewModel.applySearch(com.example.bookera.data.model.BookSearchQuery()) }) { Icon(Icons.Outlined.Close, "Очистить поиск") } },
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = accent, unfocusedBorderColor = Color.Transparent), shape = RoundedCornerShape(22.dp))
                    }
                    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { showFilters = true }, label = { Text("Фильтры") }, leadingIcon = { Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)) })
                        if (options.field != SearchField.ALL || options.author.isNotBlank() || options.year != null) {
                            TextButton(onClick = { showFilters = true }) { Text(listOfNotNull(options.field.label, options.author.takeIf { it.isNotBlank() }, options.year?.toString()).joinToString(" · "), maxLines = 1) }
                        }
                    }
                    if (!options.isActive) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Все", "Мои книги", "Избранное", "Прочитано").forEachIndexed { index, label ->
                            FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, labelColor = navy,
                                    selectedContainerColor = accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (options.isActive) item {
                            Column(Modifier.animateContentSize()) {
                                Text("${results.size} изданий · ${statuses.count { !it.failed && it.message != "Ищем…" }} источников ответили", color = navy,
                                    style = MaterialTheme.typography.labelLarge)
                                statuses.forEach { status -> Text("${status.name}: ${status.message}",
                                    color = if (status.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                                if (!loading && statuses.any { it.failed }) TextButton(onClick = { viewModel.retrySearch() }) { Text("Повторить поиск") }
                            }
                        }
                        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        if (visible.isEmpty()) item {
                            Column(Modifier.padding(20.dp)) {
                                Icon(Icons.Outlined.AutoStories, null, Modifier.size(48.dp), tint = accent)
                                Text(if (options.isActive) if (loading) "Ищем вашу историю…" else "Совпадений нет. Попробуйте другое название или автора."
                                    else "Ваши книги появятся здесь. Найдите книгу или добавьте свой файл.", color = navy, modifier = Modifier.padding(vertical = 18.dp))
                                if (!options.isActive) Button(onClick = importAction) { Text("Добавить книгу") }
                            }
                        }
                        items(visible, key = { it.id }) { book ->
                            Column {
                                if (options.isActive) Text(sourceNames[book.id].orEmpty(),
                                    color = accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
                                SwipeToDismissBox(state = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
                                    when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> { viewModel.toggleFavorite(book.id, !book.isFavorite); false }
                                        SwipeToDismissBoxValue.EndToStart -> { pendingDeletion = book; false }
                                        else -> false
                                    }
                                }), backgroundContent = {
                                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(accent).padding(20.dp), contentAlignment = Alignment.CenterEnd) {
                                        Text("Избранное  •  Удалить", color = Color.White)
                                    }
                                }) { BookCard(book, onClick = { navController.navigate(Routes.bookDetail(book.id)) },
                                    onFavorite = { viewModel.toggleFavorite(book.id, !book.isFavorite) }) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (sources) SourceSettingsSheet(viewModel, settings, onDismiss = { sources = false; if (options.isActive) viewModel.retrySearch() })
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
            text = { Text("${book.title} будет удалена из библиотеки.") },
            confirmButton = { TextButton(onClick = { viewModel.removeBook(book); pendingDeletion = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Отмена") } })
    }
}

@Composable
internal fun BookCard(book: Book, onClick: () -> Unit, onFavorite: () -> Unit,
    onMissingCover: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArtwork(book, Modifier.size(86.dp, 120.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                book.publishedYear?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(13.dp))
                Text(if (book.isRead) "✓ Прочитано" else if (book.isDownloaded) "В библиотеке · Читать" else "Каталог · Подробнее",
                    style = MaterialTheme.typography.labelSmall, color = Electric)
            }
            IconButton(onClick = onFavorite) { Icon(if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                "Избранное", tint = accent) }
        }
    }
}

@Composable
fun CoverArtwork(book: Book, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(Lavender), contentAlignment = Alignment.Center) {
        Text(book.title.take(1).uppercase(), color = navy, style = MaterialTheme.typography.headlineLarge)
        if (!book.coverUrl.isNullOrBlank()) AsyncImage(model = book.coverUrl, contentDescription = "Обложка ${book.title}",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
