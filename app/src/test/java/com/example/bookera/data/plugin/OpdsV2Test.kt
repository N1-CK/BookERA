package com.example.bookera.data.plugin

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class OpdsV2Test {
    @Test fun jsonCatalogKeepsFullAuthorsAndDoesNotOfferBorrowAsDownload() {
        val root = JsonParser.parseString("""{"publications":[{"metadata":{"title":"Книга","author":[{"name":"Павел Алексеевич Астахов"},{"name":"Соавтор"}],"published":"2020-05-01"},
            "links":[{"rel":"self","href":"/book/1"},{"rel":"http://opds-spec.org/acquisition/borrow","type":"application/epub+zip","href":"/borrow/1"}],"images":[{"href":"/covers/1.jpg"}]},
            {"metadata":{"title":"Открытая"},"links":[{"rel":"http://opds-spec.org/acquisition/open-access","type":"application/pdf","href":"/files/2.pdf"}]}]}""").asJsonObject
        val books = OpdsPlugin("fixture", "Fixture", "https://example.org/opds").parseJson(root, "https://example.org/opds")
        assertEquals("", books.first().downloadUrl)
        assertEquals("Павел Алексеевич Астахов, Соавтор", books.first().author)
        assertEquals(2020, books.first().publishedYear)
        assertEquals("https://example.org/files/2.pdf", books.last().downloadUrl)
    }
    @Test fun rfc6570SearchTemplateUsesQueryParameter() {
        val plugin = OpdsPlugin("fixture", "Fixture", "https://example.org/opds")
        assertEquals("https://example.org/search?query=%D0%90%D1%81%D1%82%D0%B0%D1%85%D0%BE%D0%B2",
            plugin.expandTemplate("https://example.org/search{?query,language,mode}", "Астахов"))
    }
    @Test fun gutenbergPerBookFeedsAreDiscoveredWithoutCrawlingEveryBook() {
        val doc = Jsoup.parse("""<feed><entry><id>urn:1</id><title>War and Peace</title><content>Leo Tolstoy</content>
            <link rel="subsection" type="application/atom+xml" href="/ebooks/2600.opds"/></entry></feed>""", "https://www.gutenberg.org/ebooks/search.opds/", Parser.xmlParser())
        val books = OpdsPlugin("gutenberg-opds", "Gutenberg", "https://www.gutenberg.org/ebooks/search.opds/")
            .parseEntries(doc, "https://www.gutenberg.org/ebooks/search.opds/")
        assertEquals("Leo Tolstoy", books.single().author)
        assertEquals("https://www.gutenberg.org/ebooks/2600.opds", books.single().downloadUrl)
    }
}
