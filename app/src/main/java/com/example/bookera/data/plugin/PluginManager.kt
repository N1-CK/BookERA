package com.example.bookera.data.plugin

import android.content.Context
import com.example.bookera.data.model.BookSearchQuery
import com.example.bookera.data.local.LocalSettings
import kotlinx.coroutines.*
import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

 data class SourceStatus(val id: String, val name: String, val message: String, val failed: Boolean = false)
 data class CatalogSearch(val books: List<BookSearchResult>, val statuses: List<SourceStatus>, val authors: List<AuthorSearchResult> = emptyList())

class PluginManager(private val context: Context) {
    private val settings = LocalSettings(context)
    private val plugins = linkedMapOf<String, DownloadPlugin>()
    private val cache = ConcurrentHashMap<String, Pair<Long, SourceResults>>()
    private val failures = ConcurrentHashMap<String, Pair<Long, String>>()
    init {
        listOf(OpenLibraryPlugin(), FlibustaPlugin(), GutenbergPlugin(), GoogleBooksPlugin { settings.googleApiKey() },
            InternetArchivePlugin(), WikisourcePlugin(), WolneLekturyPlugin()).forEach(::registerPlugin)
        registerPlugin(OpdsPlugin("gutenberg-opds", "Gutenberg · прямой OPDS", "https://www.gutenberg.org/ebooks/search.opds/", context))
        registerPlugin(OpdsPlugin("manybooks", "ManyBooks", "https://manybooks.net/opds", context))
        registerPlugin(OpdsPlugin("ebooksgratuits", "Ebooks libres et gratuits", "https://www.ebooksgratuits.com/opds/", context))
        registerPlugin(OpdsPlugin("textos", "textos.info", "https://www.textos.info/catalogo.atom", context))
        registerPlugin(OpdsPlugin("openlibrary-opds", "Open Library · OPDS 2", "https://openlibrary.org/opds/", context))
        registerPlugin(OpdsPlugin("vsenauka", "Всенаука · Дигитека", "https://vsenauka.ru/opds", context))
        registerPlugin(OpdsPlugin("standardebooks", "Standard Ebooks", "https://standardebooks.org/feeds/opds", context))
    }
    fun retryUnavailable() { failures.clear() }
    fun close() { settings.close() }
    fun registerPlugin(plugin: DownloadPlugin) { plugins[plugin.id] = plugin }
    fun getAllPlugins(): List<DownloadPlugin> {
        settings.customCatalogs().forEach { (id, name, url) ->
            if (id !in plugins) registerPlugin(OpdsPlugin(id, name, url, context))
        }
        return plugins.values.toList()
    }
    fun getPlugin(id: String): DownloadPlugin? = getAllPlugins().find { it.id == id }
    suspend fun searchCatalogs(query: BookSearchQuery, onUpdate: suspend (CatalogSearch) -> Unit = {}): CatalogSearch = coroutineScope {
        val selected = getAllPlugins().filter { it.id in settings.enabled.value }
        val found = linkedMapOf<String, SourceResults>()
        val statuses = selected.associate { it.id to SourceStatus(it.id, it.name, "Ищем…") }.toMutableMap()
        val mutex = kotlinx.coroutines.sync.Mutex()
        suspend fun publish(plugin: DownloadPlugin, result: SourceResults, status: SourceStatus) {
            mutex.lock()
            try {
                found[plugin.id] = SourceResults(result.books.map { it.copy(pluginId = plugin.id) }, result.authors.map { it.copy(pluginId = plugin.id) })
                statuses[plugin.id] = status
                onUpdate(CatalogSearch(selected.flatMap { found[it.id]?.books.orEmpty() }, selected.mapNotNull { statuses[it.id] }, selected.flatMap { found[it.id]?.authors.orEmpty() }))
            } finally { mutex.unlock() }
        }
        onUpdate(CatalogSearch(emptyList(), statuses.values.toList()))
        selected.map { plugin -> async {
            val key = "${plugin.id}:$query"
            val now = android.os.SystemClock.elapsedRealtime()
            val cached = cache[key]?.takeIf { now - it.first < 300_000L }
            val unavailable = failures[plugin.id]?.takeIf { now - it.first < 30_000L }
            if (cached != null) publish(plugin, cached.second, SourceStatus(plugin.id, plugin.name, "${cached.second.books.size} книг · ${cached.second.authors.size} авторов · из кэша"))
            else if (unavailable != null) publish(plugin, SourceResults(emptyList()), SourceStatus(plugin.id, plugin.name, unavailable.second, true))
            else {
                val result = withTimeoutOrNull(15_000L) {
                    try { plugin.searchCatalog(query) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
                }
                val error = result?.exceptionOrNull()
                if (result == null || error != null) {
                    if (error is CancellationException) throw error
                    val message = when {
                        result == null -> "Не ответил вовремя"
                        error is UnknownHostException -> "DNS: источник недоступен в этой сети"
                        error?.message?.contains("401") == true -> "Источник требует авторизацию"
                        error?.message?.contains("403") == true -> "Источник ограничил доступ"
                        error?.message?.contains("429") == true -> "Лимит запросов; попробуйте позже"
                        else -> "Источник недоступен; повторите позже"
                    }
                    failures[plugin.id] = now to message
                    publish(plugin, SourceResults(emptyList()), SourceStatus(plugin.id, plugin.name, message, true))
                } else {
                    val foundResult = result.getOrThrow()
                    val books = foundResult.copy(books = foundResult.books.distinctBy { it.id }, authors = foundResult.authors.distinctBy { it.id })
                    if (cache.size >= 60) cache.clear()
                    cache[key] = now to books
                    failures.remove(plugin.id)
                    publish(plugin, books, SourceStatus(plugin.id, plugin.name, "${books.books.size} книг · ${books.authors.size} авторов"))
                }
            }
        } }.awaitAll()
        CatalogSearch(selected.flatMap { found[it.id]?.books.orEmpty() }, selected.mapNotNull { statuses[it.id] }, selected.flatMap { found[it.id]?.authors.orEmpty() })
    }
    suspend fun searchAllPlugins(query: BookSearchQuery): List<BookSearchResult> = searchCatalogs(query).books
    suspend fun downloadWithPlugin(pluginId: String, url: String, fileName: String, onProgress: (Float) -> Unit): Result<File> {
        val plugin = getPlugin(pluginId) ?: return Result.failure(Exception("Источник не найден"))
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "books").apply { mkdirs() }
        return plugin.download(url, File(dir, File(fileName).name), onProgress)
    }
}
