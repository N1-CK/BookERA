package com.example.bookera.data.plugin

import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI

/** OPDS 1.x Atom acquisition feeds, with OpenSearch discovery and relative URLs. */
class OpdsPlugin(override val id: String, override val name: String, val feedUrl: String, context: android.content.Context? = null) : DownloadPlugin {
    override val version = "1.0"
    override val author = "OPDS catalog"
    override val description = "OPDS · $feedUrl"
    private val savedFormats = context?.applicationContext?.getSharedPreferences("opds_formats", 0)
    private val formats = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val acquiredHosts = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    override suspend fun canHandle(url: String) = url.toHttpUrlOrNull()?.let {
        it.isHttps && (it.host == feedUrl.toHttpUrlOrNull()?.host || it.host in acquiredHosts || savedFormats?.getBoolean("host_${it.host}", false) == true)
    } ?: false
    internal fun absolute(base: String, href: String): String = runCatching {
        // URI rejects literal braces, which are valid in OpenSearch URL templates.
        URI(base).resolve(href.replace("{", "%7B").replace("}", "%7D")).toString()
            .replace("%7B", "{").replace("%7D", "}")
    }.getOrDefault("")
    override suspend fun search(query: String) = catalogResult {
        require(feedUrl.toHttpUrlOrNull()?.isHttps == true) { "Используйте HTTPS" }
        val initial = Jsoup.parse(CatalogHttp.text(feedUrl), feedUrl, Parser.xmlParser())
        require(initial.select("feed").isNotEmpty()) { "Каталог вернул страницу вместо OPDS" }
        val searchLink = initial.select("feed > link").firstOrNull { it.attr("rel") == "search" }
        var searchUrl = searchLink?.attr("href")?.let { absolute(feedUrl, it) }
        if (searchUrl != null && !searchUrl.contains("{searchTerms}")) {
            val description = Jsoup.parse(CatalogHttp.text(searchUrl), searchUrl, Parser.xmlParser())
            searchUrl = description.getElementsByTag("Url").firstOrNull {
                it.attr("type").contains("atom")
            }?.attr("template")?.let { absolute(searchUrl!!, it) }
        }
        val target = searchUrl?.replace("{searchTerms}", java.net.URLEncoder.encode(query, "UTF-8"))
            ?.replace(Regex("\\{[^}]+\\}"), "")
        val doc = if (target != null) Jsoup.parse(CatalogHttp.text(target), target, Parser.xmlParser()) else initial
        parseEntries(doc, target ?: feedUrl).filter {
            target != null || it.title.contains(query, true) || it.author.contains(query, true)
        }.take(40)
    }
    internal fun parseEntries(doc: org.jsoup.nodes.Document, base: String): List<BookSearchResult> =
        doc.select("entry").mapNotNull { entry ->
            val links = entry.select("link")
            val acquisition = links.filter { isFreeAcquisition(it.attr("rel")) }
                .firstOrNull { it.attr("type") == "application/epub+zip" }
                ?: links.firstOrNull { isFreeAcquisition(it.attr("rel")) && format(it.attr("type")) != null }
                ?: return@mapNotNull null
            val file = absolute(base, acquisition.attr("href"))
            val host = file.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host ?: return@mapNotNull null
            acquiredHosts.add(host)
            val fileFormat = format(acquisition.attr("type")) ?: return@mapNotNull null
            formats[file] = fileFormat
            savedFormats?.edit()?.putString("format_$file", fileFormat)?.putBoolean("host_$host", true)?.apply()
            BookSearchResult(entry.selectFirst("id")?.text().orEmpty().ifBlank { file }, entry.selectFirst("title")?.text().orEmpty(),
                entry.select("author name").joinToString(", ") { it.text() }.ifBlank { "Автор не указан" },
                links.firstOrNull { it.attr("rel").contains("image") }?.attr("href")?.let { absolute(base, it) },
                entry.selectFirst("summary, content")?.text(), file, id,
                entry.getAllElements().firstOrNull { it.tagName().substringAfter(':') in setOf("issued", "date") }
                    ?.text()?.take(4)?.toIntOrNull())
        }
    private fun isFreeAcquisition(rel: String) = rel == "http://opds-spec.org/acquisition" ||
        rel == "http://opds-spec.org/acquisition/open-access"
    private fun format(mime: String): String? = when {
        mime.contains("epub") -> "epub"
        mime.contains("fictionbook") || mime.contains("fb2") -> "fb2"
        mime.contains("pdf") -> "pdf"
        mime.startsWith("text/plain") -> "txt"
        else -> null
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> {
        val host = url.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host ?: return emptyList()
        // A persisted acquisition URL remains usable after app restart.
        acquiredHosts.add(host)
        val ext = url.toHttpUrlOrNull()?.encodedPath?.substringAfterLast('.')?.lowercase()
        return listOf(DownloadLink(url, formats[url] ?: savedFormats?.getString("format_$url", null) ?: ext?.takeIf { it in setOf("epub", "fb2", "pdf", "txt") } ?: "epub"))
    }
    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
        if (!canHandle(url)) return Result.failure(IllegalArgumentException("Неверный источник файла"))
        return CatalogHttp.download(url, destination, onProgress)
    }
}
