package com.example.bookera.data.model

enum class SearchField(val label: String) { ALL("Везде"), TITLE("Название"), AUTHOR("Автор") }

data class BookSearchQuery(
    val text: String = "",
    val field: SearchField = SearchField.ALL,
    val author: String = "",
    val year: Int? = null
) {
    val isActive get() = text.isNotBlank() || author.isNotBlank() || year != null
    val lookupText get() = listOf(text.trim(), author.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun authorMatches(value: String, query: String): Boolean {
        val author = value.lowercase().replace('ё', 'е')
        return query.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }.all { it in author }
    }

    fun matches(title: String, authorName: String, publishedYear: Int?): Boolean {
        val q = text.trim()
        val textMatches = q.isEmpty() || when (field) {
            SearchField.ALL -> title.contains(q, true) || authorMatches(authorName, q)
            SearchField.TITLE -> title.contains(q, true)
            SearchField.AUTHOR -> authorMatches(authorName, q)
        }
        return textMatches && authorMatches(authorName, author) && (year == null || year == publishedYear)
    }

    fun matches(book: Book) = matches(book.title, book.author, book.publishedYear)

    fun openLibraryQuery(): String {
        fun quoted(value: String) = "\"${value.trim().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        return buildList {
            if (text.isNotBlank()) add(when (field) {
                SearchField.ALL -> quoted(text)
                SearchField.TITLE -> "title:${quoted(text)}"
                SearchField.AUTHOR -> "author:${quoted(text)}"
            })
            if (author.isNotBlank()) add("author:${quoted(author)}")
            year?.let { add("first_publish_year:$it") }
        }.joinToString(" AND ")
    }
}
