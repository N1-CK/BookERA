package com.example.bookera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookera.data.model.Book

@Composable
fun ProfileScreen(books: List<Book>, favorites: Int, onImport: () -> Unit, onOpenLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F6F5)).verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("Моё пространство", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF172837))) {
            Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).background(Color(0xFFBD9078), CircleShape), contentAlignment = Alignment.Center) {
                    Text("B", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(16.dp))
                Column { Text("Читатель BookERA", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Книги и прогресс хранятся на устройстве", color = Color(0xFFCFD8DF), style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Книг", books.size.toString(), Modifier.weight(1f))
            StatCard("Скачано", books.count { it.isDownloaded }.toString(), Modifier.weight(1f))
            StatCard("Любимых", favorites.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))
        Text("Быстрые действия", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        ProfileAction("＋", "Загрузить свою книгу", "FB2, EPUB, PDF или TXT", onImport)
        Spacer(Modifier.height(10.dp))
        ProfileAction("▤", "Открыть библиотеку", "Все книги и избранное", onOpenLibrary)
        Spacer(Modifier.height(24.dp))
        Text("Каталоги", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Поиск: Open Library, Project Gutenberg, Флибуста. Доступность книги и права на неё зависят от источника и страны.",
            style = MaterialTheme.typography.bodyMedium, color = Color(0xFF65717A), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Color(0xFF172837))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF65717A))
        }
    }
}

@Composable
private fun ProfileAction(symbol: String, title: String, detail: String, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = action), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = Color(0xFFBB7958), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Column { Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF65717A)) }
        }
    }
}
