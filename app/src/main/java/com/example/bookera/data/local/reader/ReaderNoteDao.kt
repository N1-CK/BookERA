package com.example.bookera.data.local.reader

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderNoteDao {
    @Query("SELECT * FROM reader_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReaderNote>>

    @Query("SELECT * FROM reader_notes WHERE bookId = :bookId ORDER BY position ASC")
    fun observeBook(bookId: Long): Flow<List<ReaderNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(note: ReaderNote)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importLegacy(notes: List<ReaderNote>)

    @Query("DELETE FROM reader_notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reader_notes WHERE bookId = :bookId")
    suspend fun deleteBook(bookId: Long)
}
