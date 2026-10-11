package com.example.bookera.data.plugin

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class CatalogParserTest {
    private fun feed(body: String) = Jsoup.parse("""<feed xmlns="http://www.w3.org/2005/Atom">$body</feed>""", "https://example.org/opds/index.xml", Parser.xmlParser())
    @Test fun relativeAcquisitionAndCoverPreserveSourceIdentity() {
        val plugin = OpdsPlugin("fixture", "Fixture", "https://example.org/opds/index.xml")
        val books = plugin.parseEntries(feed("""
            <entry><id>edition:42</id><title>Книга</title><author><name>Автор</name></author>
            <link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="../files/42.epub"/>
            <link rel="http://opds-spec.org/image/thumbnail" type="image/jpeg" href="../covers/42.jpg"/></entry>
        """), "https://example.org/opds/index.xml")
        assertEquals("https://example.org/files/42.epub", books.single().downloadUrl)
        assertEquals("https://example.org/covers/42.jpg", books.single().coverUrl)
        assertEquals("Автор", books.single().author)
        assertEquals("fixture", books.single().pluginId)
    }
    @Test fun purchaseBorrowAndUnsupportedFormatsAreExcluded() {
        val entries = listOf("buy", "borrow", "sample").joinToString("") { suffix -> """
            <entry><title>Locked</title><link rel="http://opds-spec.org/acquisition/$suffix" type="application/epub+zip" href="book.epub"/></entry>
        """ } + """<entry><title>Audio</title><link rel="http://opds-spec.org/acquisition" type="audio/mpeg" href="book.mp3"/></entry>"""
        assertTrue(OpdsPlugin("fixture", "Fixture", "https://example.org/").parseEntries(feed(entries), "https://example.org/").isEmpty())
    }
    @Test fun epubIsPreferredAndPlainTextRemainsDiscoverable() {
        val entry = """<entry><title>Work</title><link rel="http://opds-spec.org/acquisition" type="application/pdf" href="work.pdf"/>
            <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="work.epub"/></entry>"""
        val plugin = OpdsPlugin("fixture", "Fixture", "https://example.org/")
        assertEquals("https://example.org/work.epub", plugin.parseEntries(feed(entry), "https://example.org/").single().downloadUrl)
    }
    @Test fun publicationYearUsesDublinCoreIssuedAndNeverFeedUpdate() {
        val plugin = OpdsPlugin("fixture", "Fixture", "https://example.org/")
        val entries = """<entry xmlns:dc="http://purl.org/dc/terms/"><title>История</title><dc:issued>1869-01-01</dc:issued>
            <updated>2026-10-11T00:00:00Z</updated><link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" href="story.epub"/></entry>
            <entry><title>Unknown year</title><updated>2026-10-11T00:00:00Z</updated>
            <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="unknown.pdf"/></entry>"""
        val results = plugin.parseEntries(feed(entries), "https://example.org/")
        assertEquals(1869, results.first().publishedYear)
        assertNull(results.last().publishedYear)
        val query = com.example.bookera.data.model.BookSearchQuery(year = 1869)
        assertTrue(query.matches(results.first().title, results.first().author, results.first().publishedYear))
        assertFalse(query.matches(results.last().title, results.last().author, results.last().publishedYear))
    }

    @Test fun relativeOpenSearchTemplateRetainsPlaceholders() {
        val plugin = OpdsPlugin("fixture", "Fixture", "https://example.org/opds/index.xml")
        assertEquals("https://example.org/search?q={searchTerms}&count={count?}",
            plugin.absolute("https://example.org/opds/index.xml", "../search?q={searchTerms}&count={count?}"))
    }

}
