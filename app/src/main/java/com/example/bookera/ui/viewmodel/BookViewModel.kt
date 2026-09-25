// BookViewModel.kt - ОБНОВЛЕННАЯ ВЕРСИЯ
package com.example.bookera.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookera.data.model.Book
import com.example.bookera.data.local.repository.BookRepository
import com.example.bookera.data.local.LocalSettings
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.data.plugin.DownloadPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.Composable
import com.example.bookera.data.reader.ReaderManager
import com.example.bookera.data.reader.ReaderPlugin

class BookViewModel(private val repository: BookRepository, private val settings: LocalSettings) : ViewModel() {

    val profileName = settings.name
    val enabledSources = settings.enabled
    val lastRead = settings.lastRead
    private val _featured = MutableStateFlow<List<BookSearchResult>>(emptyList())
    val featured = _featured.asStateFlow()
    fun renameProfile(name: String) = settings.rename(name)
    fun setSource(id: String, enabled: Boolean) {
        settings.setSource(id, enabled)
        if (_searchQuery.value.isNotBlank()) searchBooks(_searchQuery.value)
        loadFeatured()
    }
    fun rememberReading(id: Long) = settings.rememberReading(id)

    private var searchJob: Job? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<BookSearchResult>>(emptyList())
    val searchResults: StateFlow<List<BookSearchResult>> = _searchResults.asStateFlow()

    private val _availablePlugins = MutableStateFlow<List<DownloadPlugin>>(emptyList())
    val availablePlugins: StateFlow<List<DownloadPlugin>> = _availablePlugins.asStateFlow()

    private val readerManager by lazy { ReaderManager() }

    fun getReaderForFile(filePath: String): ReaderPlugin? {
        return readerManager.getReaderForFile(filePath)
    }

    @Composable
    fun ReaderScreen(
        filePath: String,
        bookId: Long,
        onBack: () -> Unit
    ) {
        readerManager.ReaderScreen(
            filePath = filePath,
            bookId = bookId,
            onBack = onBack
        )
    }

    val books: StateFlow<List<Book>> = repository.getAllBooks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favoriteBooks: StateFlow<List<Book>> = repository.getFavoriteBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Просто загружаем плагины - книги уже есть в БД
        loadPlugins()
        loadFeatured()
    }

    private fun loadFeatured() {
        if ("openlibrary" !in enabledSources.value) { _featured.value = emptyList(); return }
        viewModelScope.launch {
            repository.searchWithPlugins("classic literature", setOf("openlibrary"))
                .onSuccess { _featured.value = it.filter { book -> book.coverUrl != null }.take(12) }
        }
    }

    private fun loadPlugins() {
        viewModelScope.launch {
            _availablePlugins.value = repository.getAvailablePlugins()
        }
    }

    // УДАЛЯЕМ loadBooks() - он больше не нужен!

    fun searchBooks(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(350)
                _isLoading.value = true
                _error.value = null
                _searchResults.value = emptyList()
                try {
                    val pluginResult = repository.searchWithPlugins(query.trim(), enabledSources.value)
                    currentCoroutineContext().ensureActive()
                    pluginResult.onSuccess { results ->
                        _searchResults.value = results.sortedByDescending { result ->
                            val title = result.title.lowercase()
                            val needle = query.trim().lowercase()
                            when { title == needle -> 3; title.startsWith(needle) -> 2; title.contains(needle) -> 1; else -> 0 }
                        }
                    }.onFailure { error ->
                        _error.value = "Search failed: ${error.message}"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.value = "Search failed: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
            _isLoading.value = false
        }
    }

    fun openSearchResult(result: BookSearchResult, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.saveSearchResult(result) }
                .onSuccess(onReady)
                .onFailure { _error.value = it.message }
        }
    }

    fun deleteDownload(id: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteDownloadedFile(id)
            result.onFailure { _error.value = it.message }
            onComplete(result.isSuccess)
        }
    }

    fun toggleFavorite(bookId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(bookId, isFavorite)
        }
    }

    fun updateRating(bookId: Long, rating: Float) {
        viewModelScope.launch {
            repository.updateRating(bookId, rating)
        }
    }

    fun clearError() {
        _error.value = null
    }

    suspend fun getBookById(id: Long): Book? {
        return repository.getBookById(id)
    }

    fun downloadBook(
        pluginId: String? = null,
        url: String,
        fileName: String,
        bookId: Long,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {

            try {

                if (pluginId != null) {

                    var lastPercent = -1

                    val result =
                        repository.downloadWithPlugin(
                            pluginId = pluginId,
                            url = url,
                            fileName = fileName,
                            bookId = bookId,
                            onProgress = { value ->
                                val percent = (value.coerceIn(0f, 1f) * 100).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    viewModelScope.launch { onProgress(value) }
                                }
                            }
                        )

                    if (result.isSuccess) {

                        onComplete(true)

                    } else {

                        _error.value =
                            result.exceptionOrNull()?.message
                                ?: "Download failed"

                        onComplete(false)
                    }

                } else {

                    _error.value =
                        "No download plugin selected"

                    onComplete(false)
                }

            } catch (e: Exception) {

                _error.value =
                    e.message ?: "Download error"

                onComplete(false)
            }
        }
    }
}
