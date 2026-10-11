package com.example.bookera.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bookera.data.model.Book
import com.example.bookera.data.local.LocalSettings
import com.example.bookera.data.local.reader.ReaderNote
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val profileInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val secondaryInk: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun ProfileScreen(
    books: List<Book>, favorites: Int, onImport: () -> Unit, onOpenLibrary: () -> Unit,
    onOpenBook: (Long) -> Unit, onSetRead: (Long, Boolean) -> Unit,
    settings: LocalSettings, notes: List<ReaderNote>, onOpenNote: (ReaderNote) -> Unit, onDeleteNote: (ReaderNote) -> Unit
) {
    val theme by settings.theme.collectAsState()
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var quotesOnly by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = history || showNotes) { history = false; showNotes = false }
    val finished = remember(books) { books.filter { it.isRead }.sortedByDescending { it.finishedAt ?: 0L } }
    val pageTotal = finished.mapNotNull { it.pages?.takeIf { count -> count > 0 } }.sum()
    val rated = finished.filter { it.rating > 0f }
    if (showNotes) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            TextButton(onClick = { showNotes = false }) { Text("← Профиль") }
            Text("Закладки и цитаты", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!quotesOnly, { quotesOnly = false }, label = { Text("Все") })
                FilterChip(quotesOnly, { quotesOnly = true }, label = { Text("Цитаты") })
            }
            val booksById = remember(books) { books.associateBy { it.id } }
            val visibleNotes = remember(notes, quotesOnly) { notes.filter { !quotesOnly || it.isQuote } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (visibleNotes.isEmpty()) item { Text("Во время чтения добавьте закладку или выделите текст и нажмите «Сохранить цитату».", color = secondaryInk) }
                items(visibleNotes, key = { it.id }) { note ->
                    val book = booksById[note.bookId]
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(book?.title ?: note.bookTitle, fontWeight = FontWeight.Bold)
                            Text(if (note.isQuote) "«${note.quote}»" else if (note.locationType == "pdf") "Закладка · страница ${note.position + 1}" else "Закладка · ${note.chapter}", color = secondaryInk)
                            Row {
                                TextButton(enabled = book?.isDownloaded == true, onClick = { onOpenNote(note) }) { Text("К месту в книге") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onDeleteNote(note) }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(16.dp))
        if (history) {
            TextButton(onClick = { history = false }) { Text("← Профиль", color = profileInk) }
            Text("Прочитанные", style = MaterialTheme.typography.headlineLarge, color = profileInk)
            Text("Ваша история чтения", color = secondaryInk)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Книг", finished.size.toString(), Modifier.weight(1f))
                StatCard("Страниц¹", pageTotal.toString(), Modifier.weight(1f))
                StatCard("Оценка", if (rated.isEmpty()) "—" else "%.1f".format(Locale.getDefault(), rated.map { it.rating * 2 }.average()), Modifier.weight(1f))
            }
            Text("¹ Сумма страниц из карточек книг, где это число известно.", color = secondaryInk,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(22.dp))
            if (finished.isEmpty()) {
                Text("Отметьте прочитанную книгу в её карточке. Здесь появятся даты, оценки и история.", color = secondaryInk)
                TextButton(onClick = onOpenLibrary) { Text("Открыть библиотеку", color = profileInk) }
            }
            finished.forEach { book ->
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverArtwork(book, Modifier.size(56.dp, 80.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = profileInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(book.author, color = secondaryInk, maxLines = 1)
                            Text(listOfNotNull(book.finishedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                                book.pages?.let { "$it стр." }, book.rating.takeIf { it > 0f }?.let { "★ ${(it * 2).toInt()} / 10" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = secondaryInk)
                        }
                    }
                    TextButton(onClick = { onSetRead(book.id, false) }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                        Text("Не прочитана", color = profileInk)
                    }
                }
            }
        } else {
            Text("Моё пространство", style = MaterialTheme.typography.headlineLarge, color = profileInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text("B", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Читатель BookERA", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge)
                        Text("Книги и прогресс хранятся на устройстве", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Книг", books.size.toString(), Modifier.weight(1f), onOpenLibrary)
                StatCard("В файлах", books.count { it.isDownloaded }.toString(), Modifier.weight(1f), onOpenLibrary)
                StatCard("Прочитано", finished.size.toString(), Modifier.weight(1f)) { history = true }
            }
            Spacer(Modifier.height(12.dp))
            ProfileAction("✓", "Прочитанные книги", "Даты завершения, оценки и история · ${finished.size}") { history = true }
            Spacer(Modifier.height(24.dp))
            ProfileAction("❝", "Закладки и цитаты", "Сохранённые места · ${notes.size}") { showNotes = true }
            Spacer(Modifier.height(16.dp))
            Text("Оформление", style = MaterialTheme.typography.titleMedium, color = profileInk)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "Авто", "light" to "Светлая", "dark" to "Тёмная").forEach { (id, label) ->
                    FilterChip(theme == id, { settings.setTheme(id) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Быстрые действия", style = MaterialTheme.typography.titleLarge, color = profileInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            ProfileAction("＋", "Загрузить свою книгу", "FB2, EPUB, PDF или TXT", onImport)
            Spacer(Modifier.height(10.dp))
            ProfileAction("▤", "Открыть библиотеку", "Все книги и избранное", onOpenLibrary)
            Spacer(Modifier.height(24.dp))
            Text("Каталоги", style = MaterialTheme.typography.titleLarge, color = profileInk, fontWeight = FontWeight.SemiBold)
            Text("Поиск: Флибуста, Open Library, Google Books, Internet Archive, Викитека, Project Gutenberg и OPDS. Доступность книги и права на неё зависят от источника и страны.",
                style = MaterialTheme.typography.bodyMedium, color = secondaryInk, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, action: (() -> Unit)? = null) {
    Card(modifier.then(if (action != null) Modifier.clickable(onClick = action) else Modifier), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = profileInk)
            Text(label, style = MaterialTheme.typography.labelMedium, color = secondaryInk)
        }
    }
}

@Composable
private fun ProfileAction(symbol: String, title: String, detail: String, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = action), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = profileInk)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = secondaryInk)
            }
        }
    }
}
