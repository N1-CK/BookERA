package com.example.bookera.data.network

import com.example.bookera.data.network.dto.BookSearchResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface BookApiService {

    @GET("search.json")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i,isbn,subject,description,edition_count,language,has_fulltext,ia,publisher,publish_date,number_of_pages_median"
    ): BookSearchResponse

    @GET("search.json")
    suspend fun getPopularBooks(
        @Query("q") query: String = "subject:fiction",
        @Query("sort") sort: String = "edition_count desc",
        @Query("limit") limit: Int = 40,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i,isbn,subject,description,edition_count,language,has_fulltext,ia,publisher,publish_date,number_of_pages_median"
    ): BookSearchResponse

    @GET("search.json")
    suspend fun getBooksByGenre(
        @Query("q") genreQuery: String,
        @Query("limit") limit: Int = 30,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i,isbn,subject,description,edition_count,language,has_fulltext,ia,publisher,publish_date,number_of_pages_median"
    ): BookSearchResponse

    @GET("search.json")
    suspend fun searchBooksAdvanced(
        @Query("q") query: String,
        @Query("sort") sort: String? = null,
        @Query("lang") lang: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i,isbn,subject,description,edition_count,language,has_fulltext,ia,publisher,publish_date,number_of_pages_median"
    ): BookSearchResponse

    @Streaming
    @GET
    suspend fun downloadBook(@Url url: String): ResponseBody
}