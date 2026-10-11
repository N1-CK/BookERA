package com.example.bookera.data.local.reader

import android.content.Context
import com.example.bookera.data.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One-time import leaves the original keys untouched, preserving older installations. */
suspend fun importLegacyNotes(context: Context, books: List<Book>, dao: ReaderNoteDao) = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("reader_settings", 0)
    if (prefs.getBoolean("notes_migrated", false)) return@withContext
    val notes = books.flatMap { book ->
        val key = if (prefs.contains("bookmark_position_${book.id}")) "bookmark_position_${book.id}" else "bookmark_${book.id}"
        val multiplier = if (key.startsWith("bookmark_position")) 1 else 1250
        val textNotes = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { value ->
            value.toIntOrNull()?.takeIf { it >= 0 }?.let { position ->
                val offset = position * multiplier
                ReaderNote(ReaderNote.bookmarkId(book.id, offset), book.id, book.title, offset)
            }
        }
        val pdfNotes = prefs.getStringSet("pdf_bookmark_${book.id}", emptySet()).orEmpty().mapNotNull { value ->
            value.toIntOrNull()?.takeIf { it >= 0 }?.let { position ->
                ReaderNote(ReaderNote.bookmarkId(book.id, position, "pdf"), book.id, book.title, position, locationType = "pdf")
            }
        }
        textNotes + pdfNotes
    }
    dao.importLegacy(notes)
    prefs.edit().putBoolean("notes_migrated", true).apply()
}
