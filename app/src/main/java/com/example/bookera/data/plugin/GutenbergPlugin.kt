package com.example.bookera.data.plugin

import com.example.bookera.data.network.CatalogHttp
import com.example.bookera.data.network.catalogResult
import com.google.gson.JsonParser

class GutenbergPlugin : PublicCatalogPlugin() {
    override val id = "gutenberg"
    override val name = "Project Gutenberg"
    override val description = "Свободная классика, EPUB и TXT через Gutendex"
    override val hosts = setOf("gutenberg.org", "gutendex.com")
    override suspend fun search(query: String) = catalogResult {
        val root = JsonParser.parseString(CatalogHttp.text(CatalogHttp.url("https://gutendex.com/books/",
            "search" to query, "copyright" to "false"))).asJsonObject
        root.array("results").mapNotNull { value ->
            val book = value.asJsonObject
            if (book.get("copyright")?.isJsonNull != false || book.get("copyright").asBoolean) return@mapNotNull null
            val formats = book.getAsJsonObject("formats")
            val epub = formats.string("application/epub+zip")
            val txt = formats.entrySet().firstOrNull { it.key.startsWith("text/plain") }?.value?.asString.orEmpty()
            val file = secure(epub.ifBlank { txt }).takeIf { it.startsWith("https:") } ?: return@mapNotNull null
            BookSearchResult("gutenberg:${book.string("id")}", book.string("title"),
                book.array("authors").joinToString(", ") { it.asJsonObject.string("name") }.ifBlank { "Автор не указан" },
                formats.string("image/jpeg").takeIf { it.isNotBlank() }?.let(::secure),
                book.array("summaries").firstOrNull()?.asString, file, id)
        }
    }
    override suspend fun findAllDownloadLinks(url: String) = if (canHandle(url))
        listOf(DownloadLink(url, if (url.contains("epub", true)) "epub" else "txt")) else emptyList()
}
