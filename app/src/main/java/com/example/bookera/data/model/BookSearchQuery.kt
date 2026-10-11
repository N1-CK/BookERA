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

    fun matches(title: String, authorName: String, publishedYear: Int?): Boolean {
        val q = text.trim()
        val textMatches = q.isEmpty() || when (field) {
            SearchField.ALL -> title.contains(q, true) || authorName.contains(q, true)
            SearchField.TITLE -> title.contains(q, true)
            SearchField.AUTHOR -> authorName.contains(q, true)
        }
        return textMatches && authorName.contains(author.trim(), true) && (year == null || year == publishedYear)
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
