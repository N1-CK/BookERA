package com.example.bookera.data.plugin

import com.example.bookera.data.model.BookSearchQuery
import com.example.bookera.data.model.SearchField
import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class FlibustaPlugin : PublicCatalogPlugin() {
    override val id = "flibusta"
    override val name = "Флибуста"
    override val description = "Авторы и их книги · FB2, EPUB, PDF · обложки из карточек"
    override val hosts = setOf("flibusta.is")
    private val pages = object : LinkedHashMap<String, Pair<Long, Document>>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Document>>) = size > 40
    }
    private suspend fun page(url: String): Document {
        require(canHandle(url))
        val now = System.nanoTime() / 1_000_000
        synchronized(pages) { pages[url]?.takeIf { now - it.first < 300_000 }?.let { return it.second } }
        val doc = Jsoup.parse(CatalogHttp.text(url), url)
        check(doc.select("#main, main, .book, .books").isNotEmpty() || doc.select("a[href*=/b/], a[href*=/a/]").isNotEmpty()) {
            "Источник вернул защитную страницу"
        }
        synchronized(pages) { pages[url] = now to doc }
        return doc
    }
    override suspend fun search(query: String): Result<List<BookSearchResult>> =
        searchCatalog(BookSearchQuery(text = query)).map { it.books }
    override suspend fun searchCatalog(query: BookSearchQuery): Result<SourceResults> = catalogResult {
        if (query.lookupText.isBlank()) return@catalogResult SourceResults(emptyList())
        val url = CatalogHttp.url("https://flibusta.is/booksearch", "ask" to query.lookupText,
            "chs" to "on", "cha" to "on", "chb" to "on")
        val doc = page(url)
        val books = parseBooks(doc).filter { query.matches(it.title, it.author, it.publishedYear) }
        val authorText = if (query.field == SearchField.TITLE) query.author else query.lookupText
        SourceResults(books, if (authorText.isBlank()) emptyList() else parseAuthors(doc, authorText))
    }
    internal fun parseAuthors(doc: Document, query: String): List<AuthorSearchResult> {
        fun words(value: String) = value.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        val terms = words(query)
        return (doc.selectFirst("#main, main") ?: doc).select("a[href]").mapNotNull { a ->
            val url = a.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val key = Regex("^/a/(\\d+)/?$").find(url.encodedPath)?.groupValues?.get(1) ?: return@mapNotNull null
            val author = a.text().replace(Regex("\\s+"), " ").trim()
            val tokens = words(author)
            if (tokens.isEmpty() || !terms.all { term -> tokens.any { it.contains(term) } }) return@mapNotNull null
            AuthorSearchResult(key, author, url.toString(), id)
        }.distinctBy { it.id }.take(50)
    }
    internal fun parseBooks(doc: Document, author: String? = null): List<BookSearchResult> =
        (doc.selectFirst("#main, main") ?: doc).select("a[href]").mapNotNull { link ->
            val url = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val key = Regex("^/b/(\\d+)/?$").find(url.encodedPath)?.groupValues?.get(1) ?: return@mapNotNull null
            if (link.text().isBlank()) return@mapNotNull null
            val candidate = link.closest("li, tr") ?: link.parent()
            val item = candidate?.takeIf { container -> container.select("a[href]").count {
                Regex("^/b/\\d+/?$").matches(it.absUrl("href").toHttpUrlOrNull()?.encodedPath.orEmpty())
            } <= 1 }
            val names = item?.select("a[href]")?.filter { a ->
                Regex("^/a/\\d+/?$").matches(a.absUrl("href").toHttpUrlOrNull()?.encodedPath.orEmpty())
            }?.map { it.text().trim() }?.filter { it.isNotBlank() }?.distinct().orEmpty()
            val bookAuthor = if (author != null) (listOf(author) + names).distinct().joinToString(", ")
                else names.joinToString(", ").ifBlank { "Автор не указан" }
            val cover = item?.select("img[src]")?.firstOrNull { it.attr("src").contains(Regex("/(i|ib|covers)/")) }?.absUrl("src")
            BookSearchResult(key, link.text().trim(), bookAuthor, cover?.let(::secure), null, url.toString(), id)
        }.distinctBy { it.id }

    override suspend fun booksByAuthor(author: AuthorSearchResult): Result<List<BookSearchResult>> = catalogResult {
        require(author.pluginId == id && Regex("^/a/\\d+/?$").matches(author.url.toHttpUrlOrNull()?.encodedPath.orEmpty()))
        val result = mutableListOf<BookSearchResult>()
        val visited = mutableSetOf<String>()
        var next: String? = author.url
        repeat(10) {
            val url = next?.takeIf { visited.add(it) } ?: return@repeat
            val doc = page(url)
            result += parseBooks(doc, author.name)
            next = doc.select("a[href]").firstOrNull {
                it.attr("rel") == "next" || it.parent()?.hasClass("pager-next") == true
            }?.absUrl("href")?.takeIf { it.toHttpUrlOrNull()?.host == "flibusta.is" }
        }
        result.distinctBy { it.id }
    }
    override suspend fun bookDetails(url: String): Result<BookDetails> = catalogResult {
        parseBookDetails(page(url))
    }
    internal fun parseBookDetails(doc: Document): BookDetails {
        val main = doc.selectFirst("#main, main") ?: doc
        val cover = main.select("img[src]").firstOrNull {
            it.attr("src").contains(Regex("/(i|ib|covers)/")) && !it.attr("src").contains("avatar")
        }?.absUrl("src")?.let(::secure)?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
        val names = main.select("a[href]").filter {
            Regex("^/a/\\d+/?$").matches(it.absUrl("href").toHttpUrlOrNull()?.encodedPath.orEmpty())
        }.map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        return BookDetails(cover, names.joinToString(", ").takeIf { it.isNotBlank() },
            main.selectFirst(".annotation, #annotation")?.text()?.takeIf { it.isNotBlank() })
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> {
        val doc = page(url)
        val key = Regex("/b/(\\d+)").find(url)?.groupValues?.get(1) ?: return emptyList()
        return doc.select("a[href]").mapNotNull { a ->
            val resolved = a.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val format = Regex("^/b/$key/(fb2|epub|pdf)/?$", RegexOption.IGNORE_CASE)
                .find(resolved.encodedPath)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
            DownloadLink(resolved.toString(), format)
        }.distinctBy { it.url }
    }
}
