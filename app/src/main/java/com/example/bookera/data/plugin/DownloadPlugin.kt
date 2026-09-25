package com.example.bookera.data.plugin

import java.io.File

interface DownloadPlugin {
    val id: String
    val name: String
    val version: String
    val author: String
    val description: String

    suspend fun canHandle(url: String): Boolean
    suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File>
    suspend fun search(query: String): Result<List<BookSearchResult>>

    suspend fun findAllDownloadLinks(url: String): List<DownloadLink> = emptyList()
}

data class BookSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val description: String?,
    val downloadUrl: String,
    val sourceId: String = ""
)

data class DownloadLink(
    val url: String,
    val format: String
)
