package com.example.bookera.data.local.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.bookera.data.local.dao.BookDao
import com.example.bookera.data.model.Book
import com.example.bookera.data.plugin.BookSearchResult
import com.example.bookera.data.plugin.DownloadPlugin
import com.example.bookera.data.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import com.example.bookera.data.network.catalogResult
import com.example.bookera.data.plugin.CatalogSearch
import com.example.bookera.data.plugin.AuthorSearchResult
import com.example.bookera.data.db.AppDatabase
import com.example.bookera.data.local.reader.importLegacyNotes
import com.example.bookera.data.model.BookSearchQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class BookRepository(
    private val bookDao: BookDao,
    private val context: Context
) {
    val notes = AppDatabase.getDatabase(context).readerNoteDao()
    suspend fun migrateNotes() = importLegacyNotes(context, bookDao.getAllBooks().first(), notes)

    private val coverSlots = Semaphore(2)
    private val sourcePrefs = context.getSharedPreferences("catalog_books", 0)
    private var coverUnavailableUntil = 0L
    private val attemptedCovers = mutableSetOf<Long>()

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

    suspend fun setRead(bookId: Long, read: Boolean) =
        bookDao.setRead(bookId, read, if (read) System.currentTimeMillis() else null)

    suspend fun delete(book: Book) {
        bookDao.delete(book)
        notes.deleteBook(book.id)
        sourcePrefs.edit().remove("source_${book.id}").apply()
        com.example.bookera.data.local.ReadingActivityStore.get(context).forget(book.id)
    }

    suspend fun removeFromLibrary(book: Book) = withContext(Dispatchers.IO) {
        book.localFilePath?.let { path ->
            val ownedDir = File(context.filesDir, "imported_books").canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == ownedDir) file.delete()
        }
        book.coverUrl?.let { path ->
            val coversDir = File(context.filesDir, "covers").canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == coversDir) file.delete()
        }
        delete(book)
    }

    suspend fun importBook(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: "book"
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            require(extension in setOf("fb2", "epub", "pdf", "txt")) { "Supported: FB2, EPUB, PDF, TXT" }
            val directory = File(context.filesDir, "imported_books").apply { mkdirs() }
            val destination = File(directory, "${UUID.randomUUID()}.$extension")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            copied += n
                            val limit = if (extension == "txt") 10L else 80L
                            require(copied <= limit * 1024 * 1024) { "File exceeds $limit MB" }
                            output.write(buffer, 0, n)
                        }
                    }
                } ?: error("Cannot open document")
                require(destination.length() > 0) { "Empty document" }
                if (extension == "pdf") require(destination.inputStream().use { input ->
                    val header = ByteArray(5)
                    input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
                }) { "Invalid PDF" }
                if (extension == "epub") require(java.util.zip.ZipFile(destination).use { zip ->
                    zip.getEntry("META-INF/container.xml") != null
                }) { "Invalid EPUB" }
                if (extension == "fb2") require(destination.inputStream().bufferedReader().use { reader ->
                    val chars = CharArray(4096)
                    val n = reader.read(chars)
                    if (n > 0) String(chars, 0, n) else ""
                }
                    .contains("FictionBook", true)) { "Invalid FB2" }
                val title = name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Untitled" }
                val metadata = readImportedMetadata(destination)
                val book = Book(
                    id = -((System.currentTimeMillis() shl 12) + (UUID.randomUUID().leastSignificantBits and 4095)),
                    title = metadata?.title?.takeIf { it.isNotBlank() } ?: title,
                    author = metadata?.author ?: "My files", publishedYear = metadata?.year,
                    coverUrl = if (extension == "epub") extractEpubCover(destination) else null,
                    localFilePath = destination.absolutePath,
                    isDownloaded = true, genre = "My books"
                )
                bookDao.insertOrUpdate(book)
                book
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
        }
    }

    private fun extractEpubCover(file: File): String? = runCatching {
        ZipFile(file).use { zip ->
            val container = zip.getInputStream(zip.getEntry("META-INF/container.xml"))
                .bufferedReader().use { it.readText() }
            val opfPath = Jsoup.parse(container, "", Parser.xmlParser())
                .selectFirst("rootfile")?.attr("full-path") ?: return@use null
            val opfEntry = zip.getEntry(opfPath) ?: return@use null
            val opf = zip.getInputStream(opfEntry).bufferedReader().use { Jsoup.parse(it.readText(), "", Parser.xmlParser()) }
            val coverId = opf.selectFirst("meta[name=cover]")?.attr("content")
            val item = opf.select("manifest item").firstOrNull {
                it.attr("id") == coverId || it.attr("properties").split(' ').contains("cover-image")
            } ?: return@use null
            if (!item.attr("media-type").startsWith("image/")) return@use null
            val href = item.attr("href")
            val base = opfPath.substringBeforeLast('/', "")
            val path = java.net.URI(null, null, if (base.isBlank()) href else "$base/$href", null).normalize().path
            val entry = zip.getEntry(path) ?: return@use null
            if (entry.size < 1L || entry.size > 5_000_000L) return@use null
            val ext = if (item.attr("media-type") == "image/png") "png" else "jpg"
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            File(dir, "${UUID.randomUUID()}.$ext").also { cover ->
                zip.getInputStream(entry).use { input -> cover.outputStream().use { input.copyTo(it) } }
            }.absolutePath
        }
    }.getOrNull()

    /** Enrich a small batch, then persist successful matches; never crawl the cover service. */
    suspend fun enrichMissingCovers() = withContext(Dispatchers.IO) {
        bookDao.getAllBooks().first().filter { it.coverUrl.isNullOrBlank() }.take(12).forEach { book ->
            resolveCover(book)
        }
    }

    suspend fun resolveCover(book: Book) = withContext(Dispatchers.IO) {
        val source = sourceForBook(book.id)
        val flibusta = source == "flibusta" || book.downloadUrl?.startsWith("https://flibusta.is/b/") == true
        if (!book.coverUrl.isNullOrBlank() || (!flibusta && android.os.SystemClock.elapsedRealtime() < coverUnavailableUntil)) return@withContext
        val shouldLookup = synchronized(attemptedCovers) { attemptedCovers.add(book.id) }
        if (!shouldLookup) return@withContext
        try {
            coverSlots.withPermit {
                if (flibusta && !book.downloadUrl.isNullOrBlank()) {
                    val details = pluginManager.getPlugin("flibusta")?.bookDetails(book.downloadUrl)?.getOrThrow()
                    bookDao.getBookById(book.id)?.let { current ->
                        val updated = current.copy(coverUrl = current.coverUrl ?: details?.coverUrl,
                            author = details?.author ?: current.author, description = current.description ?: details?.description)
                        if (updated != current) bookDao.insertOrUpdate(updated)
                    }
                } else if (book.isDownloaded) {
                    val cover = lookupCover(book.title, book.author)
                    if (cover != null) bookDao.getBookById(book.id)?.let { current ->
                        if (current.coverUrl.isNullOrBlank()) bookDao.insertOrUpdate(current.copy(coverUrl = cover))
                    }
                }
            }
        } catch (e: CancellationException) { synchronized(attemptedCovers) { attemptedCovers.remove(book.id) }; throw e }
        catch (_: Exception) { /* Covers are optional; one failed page must not break search. */ }
    }

    private suspend fun lookupCover(title: String, author: String): String? {
        if (android.os.SystemClock.elapsedRealtime() < coverUnavailableUntil) return null
        val query = "$title ${author.takeUnless { it == "My files" || it == "Unknown Author" }.orEmpty()}".trim()
        val result = pluginManager.getPlugin("googlebooks")?.search(query) ?: return null
        if (result.isFailure) {
            coverUnavailableUntil = android.os.SystemClock.elapsedRealtime() + 60_000L
            return null
        }
        return result.getOrNull()?.firstOrNull { it.title.equals(title, true) }?.coverUrl
    }


    // ============================================================
    // ПОИСК ЧЕРЕЗ ПЛАГИНЫ
    // ============================================================

    suspend fun searchWithPlugins(query: BookSearchQuery,
        onUpdate: suspend (CatalogSearch) -> Unit = {}): Result<List<BookSearchResult>> = catalogResult {
        withContext(Dispatchers.IO) {
            val persisted = mutableSetOf<String>()
            pluginManager.searchCatalogs(query) { snapshot ->
                coroutineContext.ensureActive()
                val fresh = snapshot.books.filter { persisted.add("${it.pluginId}:${it.id}") }
                persistResults(fresh)
                onUpdate(snapshot)
            }.books
        }
    }

    private suspend fun persistResults(results: List<BookSearchResult>) {
        if (results.isEmpty()) return
        val ids = results.map { "${it.pluginId}:${it.id}".hashCode().toLong() }
        val existing = ids.chunked(500).flatMap { bookDao.getByIds(it) }.associateBy { it.id }
        val books = results.mapIndexed { i, result ->
            val old = existing[ids[i]]
            old?.copy(author = result.author.takeUnless { it == "Автор не указан" } ?: old.author,
                coverUrl = old.coverUrl ?: result.coverUrl, description = old.description ?: result.description,
                publishedYear = result.publishedYear ?: old.publishedYear,
                downloadUrl = result.downloadUrl.ifBlank { old.downloadUrl.orEmpty() })
                ?: Book(id = ids[i], title = result.title, author = result.author, description = result.description,
                    coverUrl = result.coverUrl, downloadUrl = result.downloadUrl, publishedYear = result.publishedYear)
        }
        val changed = books.filter { it != existing[it.id] }
        if (changed.isNotEmpty()) bookDao.insertAll(changed)
        sourcePrefs.edit().apply { results.forEachIndexed { i, r -> putString("source_${ids[i]}", r.pluginId) } }.apply()
    }

    suspend fun booksByAuthor(author: AuthorSearchResult): Result<List<BookSearchResult>> = catalogResult {
        withContext(Dispatchers.IO) {
            val plugin = pluginManager.getPlugin(author.pluginId) ?: error("Источник не найден")
            val results = plugin.booksByAuthor(author).getOrThrow().map { it.copy(pluginId = plugin.id) }
            persistResults(results)
            results
        }
    }

    fun retryUnavailable() {
        pluginManager.retryUnavailable()
        synchronized(attemptedCovers) { attemptedCovers.clear() }
        coverUnavailableUntil = 0L
    }
    fun close() = pluginManager.close()
    fun sourceForBook(bookId: Long): String? = sourcePrefs.getString("source_$bookId", null)
    suspend fun downloadLinks(book: Book): Pair<String, List<com.example.bookera.data.plugin.DownloadLink>>? {
        val url = book.downloadUrl?.takeIf { it.isNotBlank() } ?: return null
        val plugin = sourceForBook(book.id)?.let { pluginManager.getPlugin(it) }
            ?: pluginManager.getAllPlugins().firstOrNull { it.canHandle(url) } ?: return null
        return plugin.id to plugin.findAllDownloadLinks(url)
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
