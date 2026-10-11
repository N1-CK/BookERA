package com.example.bookera

import com.example.bookera.data.model.Book
import com.example.bookera.data.model.BookSearchQuery
import com.example.bookera.data.model.SearchField
import org.junit.Assert.*
import org.junit.Test

class BookSearchQueryTest {
    private val book = Book(1, "Мастер и Маргарита", "Михаил Булгаков", publishedYear = 1967)

    @Test fun authorSearchDoesNotMatchTitle() {
        assertFalse(BookSearchQuery("Мастер", SearchField.AUTHOR).matches(book))
        assertTrue(BookSearchQuery("БУЛГАКОВ", SearchField.AUTHOR).matches(book))
    }

    @Test fun combinedFiltersRequireAllFields() {
        assertTrue(BookSearchQuery("Мастер", SearchField.TITLE, "булгаков", 1967).matches(book))
        assertFalse(BookSearchQuery("Мастер", SearchField.TITLE, "Толстой", 1967).matches(book))
        assertFalse(BookSearchQuery("Мастер", SearchField.TITLE, "булгаков", 1966).matches(book))
    }

    @Test fun unknownYearIsExcludedOnlyWhenFilteringYear() {
        assertTrue(BookSearchQuery("Мастер").matches(book.copy(publishedYear = null)))
        assertFalse(BookSearchQuery(year = 1967).matches(book.copy(publishedYear = null)))
        assertTrue(BookSearchQuery(year = 1967).isActive)
    }

    @Test fun catalogueQueryEscapesUserInput() {
        assertEquals("title:\"a\\\"b\" AND author:\"c\\\\d\" AND first_publish_year:2000",
            BookSearchQuery("a\"b", SearchField.TITLE, "c\\d", 2000).openLibraryQuery())
    }
}
