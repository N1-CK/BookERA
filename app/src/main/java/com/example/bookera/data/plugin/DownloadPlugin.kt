package com.example.bookera.data.plugin

import java.io.File
import com.example.bookera.data.model.BookSearchQuery

interface DownloadPlugin {
    val id: String
    val name: String
    val version: String
    val author: String
    val description: String

    suspend fun canHandle(url: String): Boolean
    suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File>
    suspend fun search(query: String): Result<List<BookSearchResult>>
    suspend fun search(query: BookSearchQuery): Result<List<BookSearchResult>> =
        if (query.lookupText.isBlank()) Result.success(emptyList())
        else search(query.lookupText).map { results ->
            results.filter { query.matches(it.title, it.author, it.publishedYear) }
        }

    suspend fun searchCatalog(query: BookSearchQuery): Result<SourceResults> = search(query).map { SourceResults(it) }
    suspend fun booksByAuthor(author: AuthorSearchResult): Result<List<BookSearchResult>> = Result.success(emptyList())
    suspend fun bookDetails(url: String): Result<BookDetails> = Result.success(BookDetails())

    suspend fun findAllDownloadLinks(url: String): List<DownloadLink> = emptyList()
}

data class BookSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val description: String?,
    val downloadUrl: String,
    val pluginId: String = "",
    val publishedYear: Int? = null
)

data class DownloadLink(
    val url: String,
    val format: String
)


data class AuthorSearchResult(val id: String, val name: String, val url: String, val pluginId: String,
    val description: String = "", val bookCount: Int? = null)
data class SourceResults(val books: List<BookSearchResult>, val authors: List<AuthorSearchResult> = emptyList())
data class BookDetails(val coverUrl: String? = null, val author: String? = null, val description: String? = null)
