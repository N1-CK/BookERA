package com.example.bookera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    val id: Long,
    val title: String,
    val author: String,
    val description: String? = null,
    val rating: Float = 0f,
    val coverUrl: String? = null,
    val fileUrl: String? = null,
    val isFavorite: Boolean = false,
    val genre: String? = null,
    val mood: String? = null,
    val pages: Int? = null,
    val publishedYear: Int? = null,
    val downloadUrl: String? = null,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    // Новые поля для дополнительной информации
    val olKey: String? = null,
    val iaId: String? = null,
    val language: String? = null,
    val hasFulltext: Boolean = false,
    val publisher: String? = null,
    val publishDate: String? = null,
    val isbn: String? = null,
    val editionCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)