package com.example.bookera.data.reader

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EpubTextTest {
    @Test fun nestedParagraphsAreNotDuplicatedAndNavigationIsExcluded() {
        val text = EpubReaderPlugin().extractEpubText(Jsoup.parse("""<body><nav>Навигация</nav><h1>Глава первая</h1>
            <blockquote><p>Первое <em>важное</em> предложение.</p></blockquote><p>Второе предложение.<br/>Новая строка.</p></body>"""))
        assertEquals(1, Regex("Первое важное предложение").findAll(text).count())
        assertFalse(text.contains("Навигация"))
        assertTrue(text.contains("Второе предложение.\nНовая строка."))
        assertTrue(text.startsWith("Глава первая"))
    }
}
