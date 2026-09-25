package com.example.bookera.ui.screens

import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bookera.data.model.Book
import com.example.bookera.data.plugin.DownloadLink
import com.example.bookera.data.plugin.DownloadPlugin
import com.example.bookera.ui.viewmodel.BookViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    viewModel: BookViewModel,
    onNavigateBack: () -> Unit,
    onReadBook: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var book by remember { mutableStateOf<Book?>(null) }
    var localRating by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    // Состояния для диалога выбора ссылок
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadLinks by remember { mutableStateOf<List<DownloadLink>>(emptyList()) }
    var isLoadingLinks by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    // Получаем список плагинов (ПОДНИМАЕМ НАВЕРХ)
    val plugins by viewModel.availablePlugins.collectAsState()

    // Load book details when screen opens
    LaunchedEffect(bookId) {
        book = viewModel.getBookById(bookId)
        book?.let { localRating = it.rating }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Book Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val currentBook = book
            if (currentBook == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large cover image
                    AsyncImage(
                        model = currentBook.coverUrl,
                        contentDescription = "Cover of ${currentBook.title}",
                        modifier = Modifier
                            .size(width = 200.dp, height = 300.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = currentBook.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Author
                    Text(
                        text = currentBook.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Genre and Year row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        currentBook.genre?.let { genre ->
                            InfoChip(label = "Genre", value = genre)
                        }
                        currentBook.publishedYear?.let { year ->
                            InfoChip(label = "Year", value = year.toString())
                        }
                        currentBook.pages?.let { pages ->
                            InfoChip(label = "Pages", value = pages.toString())
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive rating
                    Text(
                        text = "Your Rating",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    InteractiveRatingStars(
                        rating = localRating,
                        onRatingChanged = { newRating ->
                            localRating = newRating
                            viewModel.updateRating(currentBook.id, newRating)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
                    currentBook.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favorite button
                        OutlinedButton(
                            onClick = {
                                viewModel.toggleFavorite(currentBook.id, !currentBook.isFavorite)
                                book = currentBook.copy(isFavorite = !currentBook.isFavorite)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (currentBook.isFavorite) Icons.Filled.Favorite
                                else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (currentBook.isFavorite) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (currentBook.isFavorite) "Favorited" else "Favorite")
                        }

                        // Read button
                        Button(
                            onClick = {
                                onReadBook(currentBook.id)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = currentBook.isDownloaded
                        ) {
                            Text("Read")
                        }

                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ТЕСТОВАЯ КНОПКА
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    Toast.makeText(context, "🔍 Тестируем Флибусту...", Toast.LENGTH_SHORT).show()
                                    android.util.Log.d("FlibustaTest", "🧪 Starting Flibusta test")

                                    // Проверяем наличие плагина
                                    val flibustaPlugin = plugins.find { it.id == "flibusta" }
                                    if (flibustaPlugin == null) {
                                        android.util.Log.e("FlibustaTest", "❌ Plugin not found!")
                                        Toast.makeText(context, "❌ Плагин не найден!", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    android.util.Log.d("FlibustaTest", "✅ Plugin found: ${flibustaPlugin.name}")

                                    // Тест 1: Поиск книг
                                    android.util.Log.d("FlibustaTest", "🔍 Testing search...")
                                    val query = buildString {
                                        append(currentBook.title)

                                        if (currentBook.author.isNotBlank()) {
                                            append(" ")
                                            append(currentBook.author)
                                        }
                                    }

                                    Log.d("FlibustaTest", "🔍 Search query: $query")

                                    val searchResult = flibustaPlugin.search(query)
                                    if (searchResult.isSuccess) {
                                        val books = searchResult.getOrNull()
                                        android.util.Log.d("FlibustaTest", "✅ Search success: ${books?.size} books found")
                                        Toast.makeText(
                                            context,
                                            "✅ Найдено ${books?.size ?: 0} книг!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val error = searchResult.exceptionOrNull()
                                        android.util.Log.e("FlibustaTest", "❌ Search failed: ${error?.message}")
                                        Toast.makeText(
                                            context,
                                            "❌ Ошибка: ${error?.message ?: "Unknown"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    // Тест 2: Поиск ссылок на скачивание
                                    if (currentBook != null) {
                                        android.util.Log.d("FlibustaTest", "🔗 Testing download links for book: ${currentBook.title}")
                                        val bookUrl = "https://flibusta.is/b/319823"

                                        android.util.Log.d(
                                            "FlibustaTest",
                                            "🔗 Testing URL: $bookUrl"
                                        )

                                        val links = flibustaPlugin.findAllDownloadLinks(bookUrl)
                                        android.util.Log.d("FlibustaTest", "🔗 Found ${links.size} download links")
                                        links.forEach { link ->
                                            android.util.Log.d("FlibustaTest", "  📄 ${link.format}: ${link.url}")
                                        }
                                        Toast.makeText(
                                            context,
                                            "🔗 Найдено ${links.size} ссылок для скачивания",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                } catch (e: Exception) {
                                    android.util.Log.e("FlibustaTest", "❌ Exception: ${e.message}", e)
                                    Toast.makeText(context, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading && plugins.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🧪 Тест Флибусты")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Download button with progress
                    if (currentBook.isDownloaded) {
                        OutlinedButton(
                            onClick = { /* Already downloaded */ },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        ) {
                            Text("✓ Downloaded")
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Кнопка поиска ссылок на Флибусте
                            OutlinedButton(
                                onClick = {
                                    Log.d("FlibustaButton", "🟢 BUTTON CLICKED")

                                    isLoadingLinks = true
                                    dialogError = null
                                    showDownloadDialog = true

                                    scope.launch {
                                        try {
                                            Log.d("FlibustaButton", "🟡 Coroutine started")

                                            val flibustaPlugin = plugins.find {
                                                it.id == "flibusta"
                                            }

                                            Log.d(
                                                "FlibustaButton",
                                                "🔌 Plugin = ${flibustaPlugin?.id}"
                                            )

                                            if (flibustaPlugin == null) {
                                                dialogError = "Плагин Флибуста не найден"
                                                isLoadingLinks = false
                                                return@launch
                                            }

                                            Log.d(
                                                "FlibustaButton",
                                                "📖 Book = ${currentBook.title}"
                                            )

                                            Log.d(
                                                "FlibustaButton",
                                                "👤 Author = ${currentBook.author}"
                                            )

                                            val query = buildString {
                                                append(currentBook.title)
                                            }

                                            Log.d(
                                                "FlibustaButton",
                                                "🔍 QUERY = $query"
                                            )

                                            val searchResult =
                                                flibustaPlugin.search(query)

                                            Log.d(
                                                "FlibustaButton",
                                                "📡 SEARCH RETURNED"
                                            )

                                            if (searchResult.isFailure) {
                                                val error = searchResult.exceptionOrNull()

                                                Log.e(
                                                    "FlibustaButton",
                                                    "❌ SEARCH ERROR",
                                                    error
                                                )

                                                dialogError =
                                                    "Ошибка поиска: ${error?.message}"

                                                isLoadingLinks = false
                                                return@launch
                                            }

                                            val results =
                                                searchResult.getOrNull().orEmpty()

                                            Log.d(
                                                "FlibustaButton",
                                                "📚 RESULTS = ${results.size}"
                                            )

                                            results.forEach {
                                                Log.d(
                                                    "FlibustaButton",
                                                    "📖 ${it.title} | ${it.author} | ${it.downloadUrl}"
                                                )
                                            }

                                            if (results.isEmpty()) {
                                                dialogError =
                                                    "Книга не найдена на Флибусте"

                                                isLoadingLinks = false
                                                return@launch
                                            }

                                            val normalizedTitle =
                                                currentBook.title
                                                    .lowercase()
                                                    .trim()

                                            val selectedBook =
                                                results.firstOrNull {
                                                    it.title
                                                        .lowercase()
                                                        .contains(normalizedTitle)
                                                } ?: results.first()

                                            Log.d(
                                                "FlibustaButton",
                                                "✅ SELECTED = ${selectedBook.title}"
                                            )

                                            Log.d(
                                                "FlibustaButton",
                                                "🔗 URL = ${selectedBook.downloadUrl}"
                                            )

                                            val links =
                                                flibustaPlugin.findAllDownloadLinks(
                                                    selectedBook.downloadUrl
                                                )

                                            Log.d(
                                                "FlibustaButton",
                                                "📥 LINKS = ${links.size}"
                                            )

                                            links.forEach {
                                                Log.d(
                                                    "FlibustaButton",
                                                    "📄 ${it.format}: ${it.url}"
                                                )
                                            }

                                            val filteredLinks =
                                                links.filter {
                                                    !it.url.contains("archive.org")
                                                }

                                            if (filteredLinks.isEmpty()) {
                                                dialogError =
                                                    "Для книги не найдены ссылки на скачивание"
                                            } else {
                                                downloadLinks =
                                                    filteredLinks
                                            }

                                            isLoadingLinks = false

                                        } catch (e: Exception) {

                                            Log.e(
                                                "FlibustaButton",
                                                "💥 EXCEPTION",
                                                e
                                            )

                                            dialogError =
                                                "Ошибка: ${e.message}"

                                            isLoadingLinks = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDownloading && plugins.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Найти ссылки на Флибусте")
                            }

                            // Альтернативный вариант - OpenLibrary
                            if (!isDownloading && currentBook.olKey != null) {
                                OutlinedButton(
                                    onClick = {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        val downloadUrl = "https://openlibrary.org/works/${currentBook.olKey}/download?format=pdf"
                                        viewModel.downloadBook(
                                            pluginId = "openlibrary",
                                            url = downloadUrl,
                                            fileName = "${currentBook.id}_${currentBook.title}.pdf",
                                            bookId = currentBook.id,
                                            onProgress = { progress -> downloadProgress = progress },
                                            onComplete = { success ->
                                                isDownloading = false
                                                if (success) {
                                                    book = currentBook.copy(isDownloaded = true)
                                                    Toast.makeText(
                                                        context,
                                                        "Download complete!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Download failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isDownloading
                                ) {
                                    Text("Скачать с Open Library")
                                }
                            }

                            // Индикатор загрузки
                            if (isDownloading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Диалог выбора ссылок
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = {
                showDownloadDialog = false
                isLoadingLinks = false
            },
            title = { Text("Выберите ссылку для скачивания") },
            text = {
                Column {
                    if (isLoadingLinks) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            "Поиск ссылок...",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else if (dialogError != null) {
                        Text(
                            text = dialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else if (downloadLinks.isEmpty()) {
                        Text("Ссылки не найдены. Попробуйте другую книгу.")
                    } else {
                        Text(
                            text = "Найдено ${downloadLinks.size} ссылок:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            downloadLinks.forEach { link ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showDownloadDialog = false
                                            // Начинаем скачивание
                                            if (book != null && link.url.isNotEmpty()) {
                                                isDownloading = true
                                                downloadProgress = 0f
                                                viewModel.downloadBook(
                                                    pluginId = "flibusta",
                                                    url = link.url,
                                                    fileName = "${book!!.id}_${book!!.title}.${link.format.lowercase()}",
                                                    bookId = book!!.id,
                                                    onProgress = { progress -> downloadProgress = progress },
                                                    onComplete = { success ->
                                                        isDownloading = false
                                                        if (success) {
                                                            book = book!!.copy(isDownloaded = true)
                                                            Toast.makeText(
                                                                context,
                                                                "Download complete!",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "Download failed",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = link.format.uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Text(
                                                text = link.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Download",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadDialog = false
                        isLoadingLinks = false
                    }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun InteractiveRatingStars(
    rating: Float,
    onRatingChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        repeat(5) { index ->
            val starValue = index + 1
            val isSelected = rating >= starValue
            IconButton(
                onClick = { onRatingChanged(starValue.toFloat()) },
                modifier = Modifier.size(36.dp)
            ) {
                Text(
                    text = if (isSelected) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}