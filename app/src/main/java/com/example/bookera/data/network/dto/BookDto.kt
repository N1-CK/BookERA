package com.example.bookera.data.network.dto

import com.google.gson.annotations.SerializedName

data class BookSearchResponse(
    @SerializedName("numFound") val numFound: Int,
    @SerializedName("start") val start: Int,
    @SerializedName("numFoundExact") val numFoundExact: Boolean? = null,
    @SerializedName("docs") val docs: List<BookDoc>
)

data class BookDoc(
    @SerializedName("key") val key: String,
    @SerializedName("title") val title: String,
    @SerializedName("author_name") val authorName: List<String>?,
    @SerializedName("first_publish_year") val firstPublishYear: Int?,
    @SerializedName("cover_i") val coverId: Long?,
    @SerializedName("isbn") val isbn: List<String>?,
    @SerializedName("subject") val subject: List<String>?,
    @SerializedName("description") val description: Any?,
    @SerializedName("edition_count") val editionCount: Int? = null,
    @SerializedName("language") val language: List<String>? = null,
    @SerializedName("has_fulltext") val hasFulltext: Boolean? = null,
    @SerializedName("ia") val ia: List<String>? = null,
    @SerializedName("publisher") val publisher: List<String>? = null,
    @SerializedName("publish_date") val publishDate: List<String>? = null,
    @SerializedName("number_of_pages_median") val pagesMedian: Int? = null,
    @SerializedName("lccn") val lccn: List<String>? = null,
    @SerializedName("oclc") val oclc: List<String>? = null
) {
    fun getDescriptionText(): String? = when (description) {
        is String -> description
        is Map<*, *> -> description["value"] as? String
        else -> null
    }

    fun getCoverUrl(size: String = "M"): String? =
        coverId?.let { "https://covers.openlibrary.org/b/id/$it-$size.jpg" }

    fun getDownloadUrl(): String? {
        // Сначала пробуем получить PDF из Internet Archive
        ia?.firstOrNull()?.let { iaId ->
            return "https://archive.org/download/$iaId/${iaId}.pdf"
        }
        // Fallback на ISBN
        return isbn?.firstOrNull()?.let {
            "https://archive.org/download/isbn_$it/isbn_$it.pdf"
        }
    }

    fun getOLWorkKey(): String = key.replace("/works/", "")
}