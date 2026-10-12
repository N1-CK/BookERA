package com.example.bookera.data.plugin

import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI

/** OPDS Atom and JSON, including OpenSearch and catalog entries that expose files on a detail feed. */
class OpdsPlugin(override val id: String, override val name: String, val feedUrl: String, context: android.content.Context? = null) : DownloadPlugin {
    override val version = "1.2"
    override val author = "OPDS catalog"
    override val description = "OPDS 1/2 · $feedUrl"
    private val savedFormats = context?.applicationContext?.getSharedPreferences("opds_formats", 0)
    private val formats = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val acquiredHosts = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var searchTemplate: String? = null
    private var discoveryComplete = false
    private val discovery = kotlinx.coroutines.sync.Mutex()
    override suspend fun canHandle(url: String) = url.toHttpUrlOrNull()?.let {
        it.isHttps && (it.host == feedUrl.toHttpUrlOrNull()?.host || it.host in acquiredHosts || savedFormats?.getBoolean("host_${it.host}", false) == true)
    } ?: false
    internal fun absolute(base: String, href: String): String = runCatching {
        secure(URI(base).resolve(href.replace("{", "%7B").replace("}", "%7D")).toString()
            .replace("%7B", "{").replace("%7D", "}"))
    }.getOrDefault("")
    private fun rememberFormat(url: String, format: String) {
        val host = url.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host ?: return
        formats[url] = format; acquiredHosts.add(host)
        savedFormats?.edit()?.putString("format_$url", format)?.putBoolean("host_$host", true)?.apply()
    }
    private fun typeFor(url: String) = formats[url] ?: savedFormats?.getString("format_$url", null)
    internal fun expandTemplate(template: String, query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return template.replace(Regex("\\{\\?([^}]+)\\}")) { match ->
            val key = match.groupValues[1].split(',').firstOrNull { it in setOf("query", "q", "searchTerms") } ?: "query"
            "?$key=$encoded"
        }.replace("{searchTerms}", encoded).replace("{query}", encoded).replace(Regex("\\{[^}]+\\}"), "")
            .replace("http://m.gutenberg.org/", "https://www.gutenberg.org/")
            .replace("https://m.gutenberg.org/", "https://www.gutenberg.org/")
    }
    override suspend fun search(query: String) = catalogResult {
        require(feedUrl.toHttpUrlOrNull()?.isHttps == true) { "Используйте HTTPS" }
        discovery.lock()
        try {
            if (!discoveryComplete) {
                val body = CatalogHttp.text(feedUrl)
                val href = if (body.trimStart().startsWith("{")) {
                    JsonParser.parseString(body).asJsonObject.array("links").map { it.asJsonObject }
                        .firstOrNull { it.string("rel") == "search" }?.string("href")
                } else {
                    val doc = Jsoup.parse(body, feedUrl, Parser.xmlParser())
                    require(doc.select("feed").isNotEmpty()) { "Каталог вернул страницу вместо OPDS" }
                    doc.select("feed > link").firstOrNull { it.attr("rel") == "search" }?.attr("href")
                }
                var template = href?.let { absolute(feedUrl, it) }
                if (template != null && !template.contains('{')) {
                    val descriptor = Jsoup.parse(CatalogHttp.text(template), template, Parser.xmlParser())
                    template = descriptor.getElementsByTag("Url").firstOrNull { it.attr("type").contains("atom") || it.attr("type").contains("opds") }
                        ?.attr("template")?.let { absolute(template!!, it) }
                }
                searchTemplate = template
                discoveryComplete = true
            }
        } finally { discovery.unlock() }
        val target = searchTemplate?.let { expandTemplate(it, query) } ?: feedUrl
        val results = mutableListOf<BookSearchResult>()
        var next: String? = target
        val seen = mutableSetOf<String>()
        repeat(3) {
            val url = next?.takeIf { seen.add(it) } ?: return@repeat
            val body = CatalogHttp.text(url)
            if (body.trimStart().startsWith("{")) {
                val root = JsonParser.parseString(body).asJsonObject
                results += parseJson(root, url)
                next = root.array("links").map { it.asJsonObject }.firstOrNull { it.string("rel") == "next" }
                    ?.string("href")?.let { absolute(url, it) }
            } else {
                val doc = Jsoup.parse(body, url, Parser.xmlParser())
                results += parseEntries(doc, url)
                next = doc.select("feed > link[rel=next]").firstOrNull()?.attr("href")?.let { absolute(url, it) }
            }
            if (results.size >= 60) next = null
        }
        results.distinctBy { it.id }.filter { searchTemplate != null || it.title.contains(query, true) || it.author.contains(query, true) }.take(60)
    }
    internal fun parseEntries(doc: Document, base: String): List<BookSearchResult> = doc.select("entry").mapNotNull { entry ->
        val links = entry.select("link")
        val acquisitions = links.filter { isFreeAcquisition(it.attr("rel")) && format(it.attr("type")) != null }
        val acquisition = acquisitions.firstOrNull { format(it.attr("type")) == "epub" } ?: acquisitions.firstOrNull()
        val detail = links.firstOrNull { (it.attr("rel") in setOf("subsection", "alternate") || it.attr("rel").endsWith("/subsection")) && it.attr("type").contains("atom") }
        val authors = entry.select("author name").joinToString(", ") { it.text() }
            .ifBlank { if (base.contains("gutenberg.org")) entry.selectFirst("content")?.text().orEmpty() else "" }
        // Navigation categories are not books. Gutenberg's search uses per-book detail feeds.
        if (acquisition == null && (detail == null || authors.isBlank())) return@mapNotNull null
        val file = absolute(base, (acquisition ?: detail!!).attr("href"))
        if (file.toHttpUrlOrNull()?.isHttps != true) return@mapNotNull null
        if (acquisition == null) rememberFormat(file, "catalog")
        else acquisitions.forEach { rememberFormat(absolute(base, it.attr("href")), format(it.attr("type"))!!) }
        BookSearchResult(entry.selectFirst("id")?.text().orEmpty().ifBlank { file }, entry.selectFirst("title")?.text().orEmpty(),
            authors.ifBlank { "Автор не указан" }, links.firstOrNull { it.attr("rel").contains("image") && !it.attr("href").startsWith("data:") }
                ?.attr("href")?.let { absolute(base, it) }, entry.selectFirst("summary, content")?.text(), file, id,
            entry.getAllElements().firstOrNull { it.tagName().substringAfter(':') in setOf("issued", "date") }?.text()?.take(4)?.toIntOrNull())
    }
    private fun names(root: JsonObject, key: String): String = root.get(key)?.takeUnless { it.isJsonNull }?.let { item ->
        val values = if (item.isJsonArray) item.asJsonArray.toList() else listOf(item)
        values.joinToString(", ") { if (it.isJsonObject) it.asJsonObject.string("name") else it.asString }
    }.orEmpty()
    internal fun parseJson(root: JsonObject, base: String): List<BookSearchResult> {
        val publications = root.array("publications").toList() + root.array("groups").flatMap { it.asJsonObject.array("publications").toList() }
        return publications.mapNotNull { value ->
            val publication = value.asJsonObject
            val metadata = publication.getAsJsonObject("metadata") ?: return@mapNotNull null
            val links = publication.array("links").map { it.asJsonObject }
            val available = links.filter { isFreeAcquisition(it.string("rel")) && format(it.string("type")) != null }
            val selected = available.firstOrNull { format(it.string("type")) == "epub" } ?: available.firstOrNull()
            val file = selected?.string("href")?.let { absolute(base, it) }.orEmpty()
            available.forEach { rememberFormat(absolute(base, it.string("href")), format(it.string("type"))!!) }
            BookSearchResult(metadata.string("identifier").ifBlank { links.firstOrNull { it.string("rel") == "self" }?.string("href") ?: metadata.string("title") },
                metadata.string("title"), names(metadata, "author").ifBlank { "Автор не указан" },
                publication.array("images").firstOrNull()?.asJsonObject?.string("href")?.let { absolute(base, it) },
                metadata.string("description").takeIf { it.isNotBlank() }, file, id, metadata.string("published").take(4).toIntOrNull())
        }
    }
    private fun isFreeAcquisition(rel: String) = rel.split(", ").any { it == "http://opds-spec.org/acquisition" || it == "http://opds-spec.org/acquisition/open-access" }
    private fun format(mime: String): String? = when {
        mime.contains("epub") -> "epub"
        mime.contains("fictionbook") || mime.contains("fb2") -> "fb2"
        mime.contains("pdf") -> "pdf"
        mime.startsWith("text/plain") -> "txt"
        else -> null
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> {
        if (!canHandle(url)) return emptyList()
        if (typeFor(url) == "catalog") {
            val body = CatalogHttp.text(url)
            val doc = Jsoup.parse(body, url, Parser.xmlParser())
            return doc.select("link").mapNotNull { link ->
                if (!isFreeAcquisition(link.attr("rel"))) return@mapNotNull null
                val format = format(link.attr("type")) ?: return@mapNotNull null
                val file = absolute(url, link.attr("href"))
                rememberFormat(file, format)
                DownloadLink(file, format)
            }.distinctBy { it.url }
        }
        val ext = url.toHttpUrlOrNull()?.encodedPath?.substringAfterLast('.')?.lowercase()
        val format = typeFor(url) ?: ext?.takeIf { it in setOf("epub", "fb2", "pdf", "txt") } ?: return emptyList()
        return listOf(DownloadLink(url, format))
    }
    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
        if (!canHandle(url)) return Result.failure(IllegalArgumentException("Неверный источник файла"))
        return CatalogHttp.download(url, destination, onProgress)
    }
}
