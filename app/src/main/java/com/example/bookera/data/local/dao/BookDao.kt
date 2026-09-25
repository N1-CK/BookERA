package com.example.bookera.data.local.dao

import androidx.room.*
import com.example.bookera.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteBooks(): Flow<List<Book>>

    @Query("""
        SELECT * FROM books 
        WHERE title LIKE '%' || :query || '%' 
           OR author LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchBooks(query: String): Flow<List<Book>>

    @Query("""
        SELECT * FROM books 
        WHERE (:genre IS NULL OR genre = :genre)
          AND (:mood IS NULL OR mood = :mood)
        ORDER BY title ASC
    """)
    fun filterByGenreAndMood(genre: String?, mood: String?): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE rating >= :minRating ORDER BY rating DESC")
    fun getBooksAboveRating(minRating: Float): Flow<List<Book>>

    @Query("""
        SELECT * FROM books 
        WHERE (genre = :genre OR mood = :mood)
          AND id != :excludeBookId
        ORDER BY rating DESC
        LIMIT :limit
    """)
    fun getRecommendations(
        genre: String?,
        mood: String?,
        excludeBookId: Long,
        limit: Int = 10
    ): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(book: Book)

    @Query("UPDATE books SET rating = :rating WHERE id = :bookId")
    suspend fun updateRating(bookId: Long, rating: Float)

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun setFavorite(bookId: Long, isFavorite: Boolean)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM books WHERE genre = :genre ORDER BY title ASC")
    fun getBooksByGenre(genre: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE mood = :mood ORDER BY title ASC")
    fun getBooksByMood(mood: String): Flow<List<Book>>
}
