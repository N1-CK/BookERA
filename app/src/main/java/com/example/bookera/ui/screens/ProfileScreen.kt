package com.example.bookera.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookera.ui.viewmodel.BookViewModel

@Composable
fun ProfileScreen(viewModel: BookViewModel, onSources: () -> Unit) {
    val name by viewModel.profileName.collectAsState()
    val books by viewModel.books.collectAsState()
    val sources by viewModel.enabledSources.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Профиль", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, viewModel::renameProfile, modifier = Modifier.fillMaxWidth(), label = { Text("Как тебя зовут") }, singleLine = true)
        Card { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Моя статистика", style = MaterialTheme.typography.titleLarge)
            Text("В библиотеке: ${books.size}")
            Text("Скачано: ${books.count { it.isDownloaded }}")
            Text("Оценено: ${books.count { it.rating > 0f }}")
        } }
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onSources)) {
            Column(Modifier.padding(20.dp)) {
                Text("Источники книг →", style = MaterialTheme.typography.titleMedium)
                Text("Подключено: ${sources.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Профиль, книги, оценки и настройки хранятся только на этом устройстве.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(viewModel: BookViewModel, onBack: () -> Unit) {
    val plugins by viewModel.availablePlugins.collectAsState()
    val enabled by viewModel.enabledSources.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Источники книг") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
    }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Выбери источники для поиска. Они работают независимо; результаты помечены названием источника.")
            plugins.forEach { plugin ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (plugin.id == "openlibrary") "Каталог и обложки · без гарантии файла" else "Поиск и скачивание FB2 · доступность зависит от сайта", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = plugin.id in enabled, onCheckedChange = { viewModel.setSource(plugin.id, it) })
                    }
                }
            }
            Text("Подключение сторонних JAR-модулей пока не поддерживается. Встроенные источники можно включать и выключать здесь.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
