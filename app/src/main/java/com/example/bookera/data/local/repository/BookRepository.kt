package com.example.bookera.data.local.repository

import android.content.Context
import com.example.bookera.data.file.ArchiveExtractor
import com.example.bookera.data.local.dao.BookDao
import com.example.bookera.data.model.Book
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.data.plugin.DownloadPlugin
import com.example.bookera.data.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val bookDao: BookDao,
    private val context: Context
) {

    private val pluginManager by lazy {
        PluginManager(context)
    }

    // ============================================================
    // ЛОКАЛЬНЫЕ КНИГИ
    // ============================================================

    fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks()

    fun getFavoriteBooks(): Flow<List<Book>> =
        bookDao.getFavoriteBooks()

    fun searchBooks(query: String): Flow<List<Book>> =
        bookDao.searchBooks(query)

    fun getBooksByGenre(genre: String): Flow<List<Book>> =
        bookDao.getBooksByGenre(genre)

    fun getBooksByMood(mood: String): Flow<List<Book>> =
        bookDao.getBooksByMood(mood)

    suspend fun getBookById(id: Long): Book? =
        bookDao.getBookById(id)

    suspend fun insertOrUpdate(book: Book) =
        bookDao.insertOrUpdate(book)

    suspend fun updateRating(
        bookId: Long,
        rating: Float
    ) =
        bookDao.updateRating(bookId, rating)

    suspend fun setFavorite(
        bookId: Long,
        isFavorite: Boolean
    ) =
        bookDao.setFavorite(bookId, isFavorite)

    suspend fun delete(book: Book) =
        bookDao.delete(book)


    // ============================================================
    // ПОИСК ЧЕРЕЗ ПЛАГИНЫ
    // ============================================================

    suspend fun searchWithPlugins(
        query: String
    ): Result<List<BookSearchResult>> =
        withContext(Dispatchers.IO) {

            runCatching {

                val results =
                    pluginManager.searchAllPlugins(query)

                results.forEach { result ->

                    val id =
                        result.id.toLongOrNull()
                            ?: result.id.hashCode().toLong()

                    val existing =
                        bookDao.getBookById(id)

                    if (existing == null) {

                        val book =
                            Book(
                                id = id,
                                title = result.title,
                                author = result.author,
                                description = result.description,
                                coverUrl = result.coverUrl,
                                downloadUrl = result.downloadUrl
                            )

                        bookDao.insertOrUpdate(book)
                    }
                }

                results
            }
        }


    // ============================================================
    // СКАЧИВАНИЕ КНИГИ
    // ============================================================

    suspend fun downloadWithPlugin(
        pluginId: String,
        url: String,
        fileName: String,
        bookId: Long,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {

        try {

            android.util.Log.d(
                "BookRepository",
                "Starting download: $url"
            )

            val result =
                pluginManager.downloadWithPlugin(
                    pluginId = pluginId,
                    url = url,
                    fileName = fileName,
                    onProgress = onProgress
                )

            if (result.isFailure) {

                return@withContext Result.failure(
                    result.exceptionOrNull()
                        ?: Exception("Download failed")
                )
            }

            val downloadedFile =
                result.getOrNull()
                    ?: return@withContext Result.failure(
                        Exception("Downloaded file is null")
                    )

            android.util.Log.d(
                "BookRepository",
                "Downloaded file: ${downloadedFile.absolutePath}"
            )

            android.util.Log.d(
                "BookRepository",
                "Downloaded file name: ${downloadedFile.name}"
            )

            android.util.Log.d(
                "BookRepository",
                "Downloaded file size: ${downloadedFile.length()} bytes"
            )

            // ----------------------------------------
            // Распаковываем и ищем настоящий FB2
            // ----------------------------------------

            val finalFile =
                BookFileExtractor.prepareBookFile(
                    downloadedFile = downloadedFile,
                    bookId = bookId
                )

            android.util.Log.d(
                "BookRepository",
                "Final book file: ${finalFile.absolutePath}"
            )

            android.util.Log.d(
                "BookRepository",
                "Final book size: ${finalFile.length()} bytes"
            )

            // ----------------------------------------
            // Обновляем книгу
            // ----------------------------------------

            val book =
                bookDao.getBookById(bookId)
                    ?: return@withContext Result.failure(
                        Exception("Book not found: $bookId")
                    )

            val updated =
                book.copy(
                    localFilePath = finalFile.absolutePath,
                    isDownloaded = true
                )

            bookDao.insertOrUpdate(updated)

            android.util.Log.d(
                "BookRepository",
                "Book saved successfully"
            )

            Result.success(Unit)

        } catch (e: Exception) {

            android.util.Log.e(
                "BookRepository",
                "Download/process failed",
                e
            )

            Result.failure(e)
        }
    }


    // ============================================================
    // ПЛАГИНЫ
    // ============================================================

    fun getAvailablePlugins(): List<DownloadPlugin> =
        pluginManager.getAllPlugins()
}
