// BookViewModel.kt - ОБНОВЛЕННАЯ ВЕРСИЯ
package com.example.bookera.ui.viewmodel

import androidx.lifecycle.ViewModel
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.example.bookera.data.model.Book
import com.example.bookera.data.local.repository.BookRepository
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.data.plugin.DownloadPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import com.example.bookera.data.reader.ReaderManager
import com.example.bookera.data.reader.ReaderPlugin

class BookViewModel(private val repository: BookRepository) : ViewModel() {

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
    private var searchJob: Job? = null

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

    // Книги из локальной БД с фильтрацией по поиску
    val allBooks: StateFlow<List<Book>> = repository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<Book>> = combine(
        repository.getAllBooks(),
        _searchQuery
    ) { allBooks, query ->
        if (query.isBlank()) {
            allBooks
        } else {
            allBooks.filter { book ->
                book.title.contains(query, ignoreCase = true) ||
                        book.author.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favoriteBooks: StateFlow<List<Book>> = repository.getFavoriteBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Просто загружаем плагины - книги уже есть в БД
        loadPlugins()
        viewModelScope.launch { repository.enrichMissingCovers() }
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
                delay(450)
                _isLoading.value = true
                _error.value = null
                try {
                    val pluginResult = repository.searchWithPlugins(query)
                    pluginResult.onSuccess { results ->
                        _searchResults.value = results
                    }.onFailure { error ->
                        _error.value = "Search failed: ${error.message}"
                    }
                } catch (e: Exception) {
                    _error.value = "Search failed: ${e.message}"
                }
                _isLoading.value = false
            }
        } else {
            _searchResults.value = emptyList()
            _isLoading.value = false
        }
    }

    fun importBook(uri: Uri, onComplete: (Result<Book>) -> Unit) {
        viewModelScope.launch { onComplete(repository.importBook(uri)) }
    }

    fun removeBook(book: Book) {
        viewModelScope.launch { repository.removeFromLibrary(book) }
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

                    val result =
                        repository.downloadWithPlugin(
                            pluginId = pluginId,
                            url = url,
                            fileName = fileName,
                            bookId = bookId,
                            onProgress = { value -> Handler(Looper.getMainLooper()).post { onProgress(value) } }
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
