package com.example.bookera

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.example.bookera.data.db.AppDatabase
import com.example.bookera.data.local.reader.ReaderNote
import com.example.bookera.data.model.Book
import com.example.bookera.data.reader.ReaderFont
import com.example.bookera.data.reader.paginate
import com.example.bookera.data.reader.reflow
import com.example.bookera.data.reader.pageAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderRegressionTest {
    @Test fun reflowPreservesEveryCharacterAndQuotePosition() = runBlocking {
        val text = ("Русский текст, emoji 🌿, длинная строка и новая глава.\n\n").repeat(200)
        val sections = paginate("Глава", text)
        val original = sections.single().text
        val quoteStart = original.indexOf("emoji", 1200)
        for (font in ReaderFont.entries) {
            for (size in listOf(16f, 32f)) {
                val pages = reflow(sections, 320, 440, size, font)
                assertEquals(original, pages.joinToString("") { it.text })
                val page = pages[pageAt(pages, quoteStart)]
                assertTrue(quoteStart >= page.position && quoteStart < page.position + page.text.length)
                assertTrue(page.text.substring(quoteStart - page.position).startsWith("emoji"))
            }
        }
    }

    @Test fun databaseUpgradePreservesLibraryAndStoresQuotes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "reader_migration_test.db"
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().let { db ->
                db.bookDao().insertOrUpdate(Book(42, "Книга", "Автор", isFavorite = true))
                db.close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.execSQL("DROP TABLE reader_notes")
                old.version = 5
            }
            Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_5_6).build().let { db ->
                try {
                    assertTrue(db.bookDao().getBookById(42)!!.isFavorite)
                    db.readerNoteDao().save(ReaderNote("quote", 42, "Книга", 12, 18, "Цитата"))
                    assertEquals("Цитата", db.readerNoteDao().observeBook(42).first().single().quote)
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
