package com.example.bookera.data.plugin

import com.example.bookera.data.network.ApiClient
import com.example.bookera.data.network.dto.BookDoc
import com.example.bookera.data.network.toBook
import java.io.File

class OpenLibraryPlugin : DownloadPlugin {
    override val id = "openlibrary"
    override val name = "Open Library"
    override val version = "1.0.0"
    override val author = "Open Library Team"
    override val description = "Каталог книг и обложек Open Library"

    override suspend fun canHandle(url: String): Boolean {
        return false
    }

    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
        return Result.failure(UnsupportedOperationException("Open Library предоставляет только метаданные в этом приложении"))
    }

    override suspend fun search(query: String): Result<List<BookSearchResult>> {
        return try {
            val response = ApiClient.bookApiService.searchBooks(query)
            val results = response.docs.map { doc ->
                BookSearchResult(
                    id = doc.getOLWorkKey(),
                    title = doc.title,
                    author = doc.authorName?.joinToString(", ") ?: "Unknown Author",
                    coverUrl = doc.getCoverUrl("M"),
                    description = doc.getDescriptionText(),
                    downloadUrl = ""
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
