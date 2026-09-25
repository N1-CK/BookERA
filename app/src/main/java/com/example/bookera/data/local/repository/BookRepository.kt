package com.example.bookera.data.local.repository

import android.content.Context
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
        query: String,
        enabledIds: Set<String>
    ): Result<List<BookSearchResult>> =
        withContext(Dispatchers.IO) {

            runCatching {

                pluginManager.searchAllPlugins(query, enabledIds)
            }
        }

    suspend fun saveSearchResult(result: BookSearchResult): Long {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest((result.sourceId + ":" + result.id).toByteArray(Charsets.UTF_8))
        val id = java.nio.ByteBuffer.wrap(bytes).long
        if (bookDao.getBookById(id) == null) {
            bookDao.insertOrUpdate(Book(
                id = id, title = result.title, author = result.author,
                description = result.description, coverUrl = result.coverUrl,
                downloadUrl = result.downloadUrl
            ))
        }
        return id
    }

    suspend fun deleteDownloadedFile(bookId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val book = bookDao.getBookById(bookId) ?: error("Книга не найдена")
            val booksDir = File(context.getExternalFilesDir(null), "books").canonicalFile
            val bookFile = book.localFilePath?.let(::File)?.canonicalFile
            if (bookFile != null) {
                require(bookFile.parentFile == booksDir) { "Недопустимый путь файла" }
                if (bookFile.exists() && !bookFile.delete()) error("Не удалось удалить файл")
            }
            File(booksDir, "book_${bookId}_extracted").deleteRecursively()
            booksDir.listFiles()?.filter { it.isFile && it.name.startsWith("${bookId}_") }?.forEach {
                if (!it.delete()) error("Не удалось удалить ${it.name}")
            }
            bookDao.insertOrUpdate(book.copy(localFilePath = null, isDownloaded = false))
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

            if (downloadedFile.canonicalPath != finalFile.canonicalPath) downloadedFile.delete()
            File(downloadedFile.parentFile, "book_${bookId}_extracted").deleteRecursively()

            android.util.Log.d(
                "BookRepository",
                "Book saved successfully"
            )

            Result.success(Unit)

        } catch (e: Exception) {

            val booksDir = File(context.getExternalFilesDir(null), "books")
            File(booksDir, fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")).delete()
            File(booksDir, "book_${bookId}_extracted").deleteRecursively()

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
