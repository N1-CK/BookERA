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
    override val description = "Book metadata and covers"

    override suspend fun canHandle(url: String): Boolean {
        return url.contains("openlibrary.org") || url.contains("archive.org/download")
    }

    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
        return try {
            val response = ApiClient.bookApiService.downloadBook(url)
            destination.outputStream().use { output ->
                response.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    val contentLength = response.contentLength()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalBytes.toFloat() / contentLength)
                        }
                    }
                }
            }
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
