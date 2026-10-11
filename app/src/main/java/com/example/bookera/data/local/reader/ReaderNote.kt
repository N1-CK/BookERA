package com.example.bookera.data.local.reader

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Ignore

/** Text positions are source character offsets; PDF positions are zero-based page numbers. */
@Entity(tableName = "reader_notes", indices = [Index("bookId")])
data class ReaderNote(
    @PrimaryKey val id: String,
    val bookId: Long,
    val bookTitle: String,
    val position: Int,
    val endPosition: Int = position,
    val quote: String = "",
    val chapter: String = "",
    val locationType: String = "text",
    val createdAt: Long = System.currentTimeMillis()
) {
    @get:Ignore
    val isQuote: Boolean get() = quote.isNotEmpty()
    companion object {
        fun bookmarkId(bookId: Long, position: Int, type: String = "text") = "$bookId:$type:bookmark:$position"
    }
}
