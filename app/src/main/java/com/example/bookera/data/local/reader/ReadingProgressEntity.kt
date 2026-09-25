package com.example.bookera.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(

    @PrimaryKey
    val bookId: Long,

    val firstVisibleItemIndex: Int = 0,

    val firstVisibleItemOffset: Int = 0,

    val progress: Float = 0f,

    val updatedAt: Long = System.currentTimeMillis()
)