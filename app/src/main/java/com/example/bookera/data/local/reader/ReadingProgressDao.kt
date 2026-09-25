package com.example.bookera.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadingProgressDao {

    @Query(
        """
        SELECT *
        FROM reading_progress
        WHERE bookId = :bookId
        LIMIT 1
        """
    )
    suspend fun getProgress(
        bookId: Long
    ): ReadingProgressEntity?

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun saveProgress(
        progress: ReadingProgressEntity
    )

    @Query(
        "DELETE FROM reading_progress WHERE bookId = :bookId"
    )
    suspend fun deleteProgress(
        bookId: Long
    )
}