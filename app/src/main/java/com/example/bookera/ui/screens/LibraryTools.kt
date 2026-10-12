package com.example.bookera.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.bookera.data.local.LocalSettings
import com.example.bookera.data.local.reader.ReaderNote
import com.example.bookera.data.model.Book
import com.example.bookera.ui.viewmodel.BookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsSheet(viewModel: BookViewModel, settings: LocalSettings, onDismiss: () -> Unit) {
    val plugins by viewModel.availablePlugins.collectAsState()
    val enabled by settings.enabled.collectAsState()
    var apiKey by remember { mutableStateOf(settings.googleApiKey()) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HomePaper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding()) {
            Text("Источники книг", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Выберите каталоги для общего поиска", modifier = Modifier.padding(vertical = 8.dp))
            plugins.forEach { plugin ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(plugin.name, fontWeight = FontWeight.SemiBold)
                        Text(plugin.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = plugin.id in enabled, onCheckedChange = { settings.setSource(plugin.id, it) })
                }
            }
            OutlinedTextField(apiKey, { apiKey = it; settings.setGoogleApiKey(it) }, label = { Text("Google Books API key · необязательно") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Text("Свой ключ помогает использовать квоту вашего Google-проекта, если общий доступ ограничен.", style = MaterialTheme.typography.bodySmall)
            Text("Добавить OPDS-каталог", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("OPDS 1/2: EPUB, FB2, PDF и TXT, поиск и вложенные карточки. Можно подключить свой публичный каталог.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("https://…/opds") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                runCatching { settings.addCatalog(name, url); viewModel.loadPlugins(); name = ""; url = ""; error = null }
                    .onFailure { error = it.message }
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) { Text("Добавить каталог") }
            Text("Standard Ebooks может требовать членство; закрытые каталоги покажут ошибку доступа. Выключенные источники не участвуют в поиске.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Готово") }
        }
    }
}

@Composable
fun PassagesScreen(books: List<Book>, notes: List<ReaderNote>, onBack: () -> Unit,
    onOpenNote: (ReaderNote) -> Unit, onDeleteNote: (ReaderNote) -> Unit) {
    var quotes by remember { mutableStateOf(false) }
    val byId = remember(books) { books.associateBy { it.id } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Закладки и цитаты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!quotes, { quotes = false }, label = { Text("Закладки") })
            FilterChip(quotes, { quotes = true }, label = { Text("Цитаты") })
        }
        val visible = notes.filter { it.isQuote == quotes }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (visible.isEmpty()) item { Text(if (quotes) "Выделите текст в книге и нажмите «Сохранить цитату»."
                else "Добавляйте закладки во время чтения. Здесь можно вернуться к каждой из них.", modifier = Modifier.padding(20.dp)) }
            items(visible, key = { it.id }) { note ->
                val book = byId[note.bookId]
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (quotes) Lavender else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(book?.title ?: note.bookTitle, color = Electric, style = MaterialTheme.typography.labelLarge)
                        Text(if (quotes) "«${note.quote}»" else if (note.locationType == "pdf") "Страница ${note.position + 1}" else "Закладка · ${note.chapter}", modifier = Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = book?.isDownloaded == true, onClick = { onOpenNote(note) }, modifier = Modifier.weight(1f)) { Text("Открыть в книге →") }
                            IconButton(onClick = { onDeleteNote(note) }) { Icon(Icons.Outlined.DeleteOutline, "Удалить") }
                        }
                    }
                }
            }
        }
    }
}
