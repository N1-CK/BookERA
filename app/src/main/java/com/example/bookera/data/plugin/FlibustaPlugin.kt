package com.example.bookera.data.plugin

import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import org.jsoup.Jsoup

class FlibustaPlugin : PublicCatalogPlugin() {
    override val id = "flibusta"
    override val name = "Флибуста"
    override val description = "Поиск и доступные FB2, EPUB, PDF; доступность зависит от сети"
    override val hosts = setOf("flibusta.is")
    override suspend fun search(query: String) = catalogResult {
        val url = CatalogHttp.url("https://flibusta.is/booksearch", "ask" to query)
        val doc = Jsoup.parse(CatalogHttp.text(url), url)
        val results = doc.select("li").mapNotNull { item ->
            val link = item.select("a[href]").firstOrNull { Regex("/b/\\d+/?$").containsMatchIn(it.attr("href")) }
                ?: return@mapNotNull null
            val key = Regex("/b/(\\d+)").find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            BookSearchResult(key, link.text(), item.select("a[href*=/a/]").joinToString(", ") { it.text() }.ifBlank { "Автор не указан" },
                item.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }, null, link.absUrl("href"), id)
        }.distinctBy { it.id }.take(40)
        check(results.isNotEmpty() || doc.text().contains("не найден", true) || doc.select("#main").isNotEmpty()) {
            "Источник вернул защитную страницу"
        }
        results
    }
    override suspend fun findAllDownloadLinks(url: String): List<DownloadLink> {
        require(canHandle(url))
        val doc = Jsoup.parse(CatalogHttp.text(url), url)
        val key = Regex("/b/(\\d+)").find(url)?.groupValues?.get(1) ?: return emptyList()
        return doc.select("a[href]").mapNotNull { a ->
            val format = Regex("/b/$key/(fb2|epub|pdf)/?$", RegexOption.IGNORE_CASE)
                .find(a.attr("href"))?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
            DownloadLink(a.absUrl("href"), format)
        }.distinctBy { it.url }
    }
}
