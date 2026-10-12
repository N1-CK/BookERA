package com.example.bookera.data.plugin

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class FlibustaParserTest {
    @Test fun authorOnlySearchReturnsFullNamesAndSeparateIdentities() {
        val doc = Jsoup.parse("""<div id="main"><h2>Авторы</h2><ul>
            <li><a href="/a/25158">Астахов, Павел Алексеевич</a></li>
            <li><a href="/a/42">Павел Андреевич Астахов</a></li>
            <li><a href="/a/9">Иван Петров</a></li></ul></div>""", "https://flibusta.is/booksearch")
        val plugin = FlibustaPlugin()
        assertTrue(plugin.parseBooks(doc).isEmpty())
        assertEquals(2, plugin.parseAuthors(doc, "Астахов").size)
        val author = plugin.parseAuthors(doc, "Павел Алексеевич Астахов").single()
        assertEquals("25158", author.id)
        assertEquals("Астахов, Павел Алексеевич", author.name)
        assertEquals("https://flibusta.is/a/25158", author.url)
    }
    @Test fun authorPageFindsBooksOutsideListItemsWithoutMixingAuthors() {
        val doc = Jsoup.parse("""<div id="main"><h1>Павел Алексеевич Астахов</h1>
            <a href="/b/101">Книга первая</a><a href="/b/101/fb2">скачать</a><br/>
            <a href="/b/102">Книга вторая</a><a href="/b/101">Книга первая</a></div>""", "https://flibusta.is/a/25158")
        val books = FlibustaPlugin().parseBooks(doc, "Павел Алексеевич Астахов")
        assertEquals(listOf("101", "102"), books.map { it.id })
        assertTrue(books.all { it.author == "Павел Алексеевич Астахов" })
    }
    @Test fun realCoverPathAndEveryCoauthorAreResolvedFromBookPage() {
        val doc = Jsoup.parse("""<div id="main"><img src="/images/logo.png"/><img src="/i/12/34/cover.jpg"/>
            <a href="/a/25158">Павел Алексеевич Астахов</a><a href="/a/2">Анна Ивановна Иванова</a>
            <div class="annotation">История книги</div></div>""", "https://flibusta.is/b/101")
        val details = FlibustaPlugin().parseBookDetails(doc)
        assertEquals("https://flibusta.is/i/12/34/cover.jpg", details.coverUrl)
        assertTrue(details.author!!.contains("Анна Ивановна Иванова"))
        assertEquals("История книги", details.description)
    }
}
