package com.example.bookera.data.plugin

import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.File

internal fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.let {
    if (it.isJsonArray) it.asJsonArray.joinToString(", ") { v -> v.asString } else it.asString
}.orEmpty()
internal fun JsonObject.array(key: String): JsonArray = getAsJsonArray(key) ?: JsonArray()
internal fun secure(url: String): String = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url

abstract class PublicCatalogPlugin : DownloadPlugin {
    override val version = "1.1"
    override val author = "BookERA / public catalogs"
    protected abstract val hosts: Set<String>
    override suspend fun canHandle(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return hosts.any { host == it || host.endsWith(".$it") }
    }
    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
        if (!canHandle(url)) return Result.failure(IllegalArgumentException("Неверный источник файла"))
        return CatalogHttp.download(url, destination, onProgress)
    }
}

class GoogleBooksPlugin(private val apiKey: () -> String = { "" }) : PublicCatalogPlugin() {
    override val id = "googlebooks"
    override val name = "Google Books"
    override val description = "Карточки, аннотации, обложки; файлы только для свободных изданий"
    override val hosts = setOf("googleapis.com", "google.com", "googleusercontent.com")
    override suspend fun search(query: String) = catalogResult {
        val params = mutableListOf("q" to query, "maxResults" to "20", "printType" to "books")
        apiKey().takeIf { it.isNotBlank() }?.let { params.add("key" to it) }
        val root = JsonParser.parseString(CatalogHttp.text(CatalogHttp.url("https://www.googleapis.com/books/v1/volumes", *params.toTypedArray()))).asJsonObject
        root.array("items").mapNotNull { element ->
            val item = element.asJsonObject
            val info = item.getAsJsonObject("volumeInfo") ?: return@mapNotNull null
            val access = item.getAsJsonObject("accessInfo")
            // DRM/ACSM tokens and paid editions are never offered as ordinary files.
            val free = access?.get("publicDomain")?.takeUnless { it.isJsonNull }?.asBoolean == true
            val epub = if (free) access?.getAsJsonObject("epub")?.string("downloadLink").orEmpty() else ""
            val pdf = if (free) access?.getAsJsonObject("pdf")?.string("downloadLink").orEmpty() else ""
            BookSearchResult(item.string("id"), info.string("title"), info.string("authors").ifBlank { "Автор не указан" },
                info.getAsJsonObject("imageLinks")?.string("thumbnail")?.let(::secure),
                info.string("description").takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text() },
                secure(epub.ifBlank { pdf }), id, info.string("publishedDate").take(4).toIntOrNull())
        }
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> =
        if (url.isNotBlank() && canHandle(url)) listOf(DownloadLink(url, if (url.contains("epub", true)) "epub" else "pdf")) else emptyList()
}

class InternetArchivePlugin : PublicCatalogPlugin() {
    override val id = "archive"
    override val name = "Internet Archive"
    override val description = "Открытые тексты, EPUB, PDF и TXT; без выдачи закрытых книг напрокат"
    override val hosts = setOf("archive.org")
    override suspend fun search(query: String) = catalogResult {
        val words = query.replace(Regex("[+\\-!(){}\\[\\]^\"~*?:\\\\/]"), " ").trim()
        val q = "mediatype:texts AND NOT collection:inlibrary AND NOT collection:printdisabled AND (title:($words) OR creator:($words))"
        val root = JsonParser.parseString(CatalogHttp.text(CatalogHttp.url("https://archive.org/advancedsearch.php",
            "q" to q, "fl[]" to "identifier", "fl[]" to "title", "fl[]" to "creator", "fl[]" to "description", "fl[]" to "year", "fl[]" to "date",
            "rows" to "20", "page" to "1", "output" to "json"))).asJsonObject
        root.getAsJsonObject("response").array("docs").map { value ->
            val item = value.asJsonObject
            val key = item.string("identifier")
            BookSearchResult(key, item.string("title"), item.string("creator").ifBlank { "Автор не указан" },
                "https://archive.org/services/img/$key", Jsoup.parse(item.string("description")).text(),
                "https://archive.org/details/$key", id, item.string("year").take(4).toIntOrNull() ?: item.string("date").take(4).toIntOrNull())
        }
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> {
        require(canHandle(url))
        val key = url.toHttpUrlOrNull()?.pathSegments?.getOrNull(1) ?: return emptyList()
        val root = JsonParser.parseString(CatalogHttp.text("https://archive.org/metadata/$key")).asJsonObject
        val metadata = root.getAsJsonObject("metadata") ?: return emptyList()
        if (metadata.string("access-restricted-item") == "true" || metadata.string("collection").contains("inlibrary")) return emptyList()
        return root.array("files").mapNotNull { element ->
            val file = element.asJsonObject
            if (file.string("private") == "true") return@mapNotNull null
            val fileName = file.string("name")
            val ext = fileName.substringAfterLast('.').lowercase()
            if (ext !in setOf("epub", "pdf", "txt")) return@mapNotNull null
            val link = "https://archive.org/download/".toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment(key).addPathSegment(fileName).build().toString()
            DownloadLink(link, ext)
        }.distinctBy { it.url }.sortedBy { listOf("epub", "pdf", "txt").indexOf(it.format) }.take(8)
    }
}

class WikisourcePlugin : PublicCatalogPlugin() {
    override val id = "wikisource"
    override val name = "Викитека"
    override val description = "Русские свободные тексты; загрузка выбранной страницы в TXT с указанием источника"
    override val hosts = setOf("ru.wikisource.org")
    override suspend fun search(query: String) = catalogResult {
        val root = JsonParser.parseString(CatalogHttp.text(CatalogHttp.url("https://ru.wikisource.org/w/api.php",
            "action" to "query", "list" to "search", "srsearch" to query, "srnamespace" to "0",
            "srlimit" to "20", "format" to "json"))).asJsonObject
        root.getAsJsonObject("query")?.array("search")?.map { value ->
            val page = value.asJsonObject
            BookSearchResult(page.string("pageid"), page.string("title"), "Викитека", null,
                Jsoup.parse(page.string("snippet")).text(), CatalogHttp.url("https://ru.wikisource.org/w/index.php",
                    "curid" to page.string("pageid")), id)
        }.orEmpty()
    }
    override suspend fun findAllDownloadLinks(url: String) = listOf(DownloadLink(url, "txt"))
    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> = catalogResult {
        require(canHandle(url))
        val pageId = url.toHttpUrlOrNull()?.queryParameter("curid") ?: error("Не найдена страница")
        val root = JsonParser.parseString(CatalogHttp.text(CatalogHttp.url("https://ru.wikisource.org/w/api.php",
            "action" to "parse", "pageid" to pageId, "prop" to "text", "format" to "json"))).asJsonObject
        val parsed = root.getAsJsonObject("parse") ?: error("Текст недоступен")
        val doc = Jsoup.parse(parsed.getAsJsonObject("text").string("*"))
        doc.select(".mw-editsection, .noprint, .navbox, .metadata, script, style").remove()
        doc.select("p, br, div, h1, h2, h3, li").forEach { it.appendText("\n") }
        val text = doc.wholeText().trim()
        require(text.length > 50) { "На странице нет текста произведения" }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            destination.writeText("${parsed.string("title")}\nИсточник: $url\nЛицензии и авторство: см. страницу источника\n\n$text")
        }
        onProgress(1f)
        destination
    }
}

class WolneLekturyPlugin : PublicCatalogPlugin() {
    override val id = "wolnelektury"
    override val name = "Wolne Lektury"
    override val description = "Открытая польская классика, EPUB, PDF, FB2 и TXT"
    override val hosts = setOf("wolnelektury.pl")
    private val gate = kotlinx.coroutines.sync.Mutex()
    private var index: List<BookSearchResult>? = null
    override suspend fun search(query: String) = catalogResult {
        gate.lock()
        try {
            if (index == null) {
                index = JsonParser.parseString(CatalogHttp.text("https://wolnelektury.pl/api/books/")).asJsonArray.map { v ->
                    val b = v.asJsonObject
                    BookSearchResult(b.string("slug"), b.string("title"), b.string("author"), b.string("simple_thumb"),
                        null, b.string("href"), id)
                }
            }
            val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            index.orEmpty().filter { b -> terms.all { it in "${b.title} ${b.author}".lowercase() } }.take(20)
        } finally { gate.unlock() }
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> {
        require(canHandle(url))
        val b = JsonParser.parseString(CatalogHttp.text(url)).asJsonObject
        return listOf("epub", "fb2", "pdf", "txt").mapNotNull { ext ->
            b.string(ext).takeIf { it.startsWith("https:") }?.let { DownloadLink(it, ext) }
        }
    }
}
