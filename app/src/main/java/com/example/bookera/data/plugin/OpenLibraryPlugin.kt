package com.example.bookera.data.plugin

import com.example.bookera.data.model.BookSearchQuery
import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import com.google.gson.JsonParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

class OpenLibraryPlugin : PublicCatalogPlugin() {
    override val id = "openlibrary"
    override val name = "Open Library"
    override val description = "Метаданные и обложки; свободные файлы ищутся в других каталогах"
    override val hosts = setOf("openlibrary.org")
    companion object {
        private val gate = Mutex()
        private var lastRequest = 0L
    }
    override suspend fun search(query: String) = search(BookSearchQuery(text = query))
    override suspend fun search(query: BookSearchQuery) = catalogResult {
        gate.withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            delay((1100L - (now - lastRequest)).coerceAtLeast(0))
            lastRequest = android.os.SystemClock.elapsedRealtime()
            val root = JsonParser.parseString(CatalogHttp.text(CatalogHttp.url("https://openlibrary.org/search.json",
                "q" to query.openLibraryQuery(), "limit" to "20", "fields" to "key,title,author_name,cover_i,isbn,first_publish_year"))).asJsonObject
            root.array("docs").map { value ->
                val doc = value.asJsonObject
                val cover = doc.string("cover_i").takeIf { it.isNotBlank() }
                BookSearchResult(doc.string("key").substringAfterLast('/'), doc.string("title"),
                    doc.string("author_name").ifBlank { "Автор не указан" },
                    cover?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }, null, "", id, doc.string("first_publish_year").toIntOrNull())
            }.filter { query.matches(it.title, it.author, it.publishedYear) }
        }
    }
}
