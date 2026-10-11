package com.example.bookera.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookera.data.local.ReadingActivityStore
import com.example.bookera.data.model.Book
import com.example.bookera.data.local.reader.ReaderNote
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

internal val Electric: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val HeroViolet = Color(0xFF6445F5)
internal val AccentInk = Color(0xFF242037)
internal val HomePaper: Color @Composable get() = MaterialTheme.colorScheme.background
internal val HomeInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
internal val Lavender: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val Lime = Color(0xFFDFEF88)

@Composable
fun HomeScreen(books: List<Book>, store: ReadingActivityStore, notes: List<ReaderNote>, lastRead: Long, onRead: (Long) -> Unit,
    onSearch: () -> Unit, onLibrary: () -> Unit, onPassages: () -> Unit, onImport: () -> Unit) {
    val revision by store.revision.collectAsState()
    val visits = remember(books, revision, lastRead) {
        val tracked = store.visits()
        if (tracked.none { it.bookId == lastRead } && lastRead != 0L)
            tracked + com.example.bookera.data.local.ReadingVisit(lastRead, 0L, 0f) else tracked
    }
    val recent = visits.mapNotNull { v -> books.find { it.id == v.bookId && !it.isRead && it.isDownloaded }?.let { it to v.progress } }.take(5)
    val saved = notes
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("bookera", fontSize = 30.sp, fontWeight = FontWeight.Black, color = HomeInk, letterSpacing = (-1).sp)
                    Text("Ваша следующая глава", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                FilledIconButton(onClick = onSearch, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = HomeInk), modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Outlined.Search, "Найти книгу")
                }
            }
        }
        item { WeekStrip(store, revision) }
        item {
            AnimatedContent(targetState = recent.firstOrNull(), label = "continue") { latest ->
                if (latest == null) Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = HeroViolet)) {
                    Box(Modifier.fillMaxWidth()) {
                        OrbitArtwork(Modifier.matchParentSize())
                        Column(Modifier.padding(24.dp)) {
                            Text("ВСЁ НАЧИНАЕТСЯ С КНИГИ", color = Color(0xFFE5DDFF), style = MaterialTheme.typography.labelMedium)
                            Text("Найдите историю,\nв которой хочется\nостаться", color = Color.White, fontSize = 30.sp, lineHeight = 35.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 20.dp))
                            Button(onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = AccentInk)) {
                                Text("Выбрать книгу"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                            }
                            TextButton(onClick = onImport) { Text("Или добавить свой файл", color = Color.White) }
                        }
                    }
                } else ContinueCard(latest.first, latest.second) { onRead(latest.first.id) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onPassages), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Lime)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(Color.White.copy(alpha = .65f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Bookmarks, null, tint = AccentInk)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Закладки и цитаты", color = AccentInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (saved.isEmpty()) "Мысли, которые хочется сохранить" else "${saved.count { !it.isQuote }} закладок · ${saved.count { it.isQuote }} цитат",
                            color = AccentInk, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AccentInk)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Продолжить читать", color = HomeInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onLibrary) { Text("Все", color = Electric) }
            }
            if (recent.isEmpty()) Text("Откройте книгу — здесь появятся пять последних недочитанных историй.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(recent, key = { it.first.id }) { (book, progress) ->
                    Card(Modifier.width(146.dp).clickable { onRead(book.id) }, shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp)) {
                            CoverArtwork(book, Modifier.fillMaxWidth().height(168.dp))
                            Text(book.title, color = HomeInk, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp).heightIn(min = 42.dp))
                            Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(top = 12.dp), color = Electric, trackColor = Lavender)
                            Text("${(progress * 100).toInt()}% прочитано", color = Electric, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onLibrary), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Lavender)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoStories, null, tint = Electric)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Моя библиотека", color = HomeInk, fontWeight = FontWeight.Bold)
                        Text("${books.count { it.isDownloaded }} книг для чтения · всё под рукой", color = HomeInk, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Electric)
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(book: Book, progress: Float, action: () -> Unit) {
    val animated by animateFloatAsState(progress, label = "readingProgress")
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = HeroViolet)) {
        Box {
            OrbitArtwork(Modifier.matchParentSize())
            Column(Modifier.padding(22.dp)) {
                Text("ЧИТАЛИ РАНЕЕ · ВЕРНЁМСЯ?", color = Color(0xFFE5DDFF), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArtwork(book, Modifier.size(100.dp, 145.dp).rotate(-5f))
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text(book.title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(book.author, color = Color(0xFFE5DDFF), maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                        Text("${(animated * 100).toInt()}% прочитано", color = Lime, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                    }
                }
                Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = AccentInk)) {
                    Text("Продолжить чтение", fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun OrbitArtwork(modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = .06f), radius = size.width * .55f, center = Offset(size.width, size.height * .1f))
        drawCircle(Color.White.copy(alpha = .09f), radius = size.width * .3f, center = Offset(size.width * .97f, size.height * .1f))
        drawCircle(Lime.copy(alpha = .3f), radius = 9.dp.toPx(), center = Offset(size.width * .8f, size.height * .15f))
    }
}

@Composable
private fun WeekStrip(store: ReadingActivityStore, revision: Int) {
    val days = remember(revision) { store.days() }
    val today = LocalDate.now()
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 12.dp)) {
                Text("${store.streak()} дней", fontWeight = FontWeight.Bold, color = Electric, style = MaterialTheme.typography.labelLarge)
                Text("подряд", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            (6L downTo 0L).forEach { offset ->
                val date = today.minusDays(offset)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(25.dp).background(if (date in days) Electric else if (date == today) Lavender else HomePaper, CircleShape), contentAlignment = Alignment.Center) {
                        if (date in days) Icon(Icons.Outlined.Check, "Читали ${date}", tint = Color.White, modifier = Modifier.size(17.dp))
                        else Text(date.dayOfMonth.toString(), color = HomeInk, fontSize = 10.sp)
                    }
                    Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("ru")), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}
