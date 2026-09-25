package com.example.bookera.data.network

import com.example.bookera.data.model.Book
import com.example.bookera.data.network.dto.BookDoc

fun BookDoc.toBook(): Book {
    val numericId = extractId(key)

    // Определяем жанр из subject
    val genre = subject?.firstOrNull {
        !it.contains("fiction", ignoreCase = true) &&
                !it.contains("novel", ignoreCase = true)
    } ?: subject?.firstOrNull()

    // Определяем настроение (примерные ключевые слова)
    val mood = subject?.find { subjectItem ->
        val subjectLower = subjectItem.lowercase()
        listOf("adventure", "romance", "mystery", "thriller", "fantasy",
            "science fiction", "horror", "comedy", "drama", "tragedy")
            .any { moodKeyword ->
                // Проверяем, содержит ли subjectItem ключевое слово настроения
                subjectLower.contains(moodKeyword.lowercase())
            }
    }

    return Book(
        id = numericId,
        title = title,
        author = authorName?.joinToString(", ") ?: "Unknown Author",
        description = getDescriptionText(),
        rating = 0f,
        coverUrl = getCoverUrl(size = "L"),
        fileUrl = null,
        isFavorite = false,
        genre = genre,
        mood = mood,
        pages = pagesMedian ?: 0,
        publishedYear = firstPublishYear,
        downloadUrl = getDownloadUrl(),
        localFilePath = null,
        isDownloaded = false,
        olKey = getOLWorkKey(),
        iaId = ia?.firstOrNull(),
        language = language?.firstOrNull(),
        hasFulltext = hasFulltext ?: false,
        publisher = publisher?.firstOrNull(),
        publishDate = publishDate?.firstOrNull(),
        isbn = isbn?.firstOrNull(),
        editionCount = editionCount ?: 0
    )
}

private fun extractId(key: String): Long {
    return key
        .removePrefix("/works/")
        .removePrefix("/work/")
        .removeSuffix("W")
        .toLongOrNull()
        ?: key.hashCode().toLong()
}