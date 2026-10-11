package com.example.bookera.data.local.repository

import android.util.Xml
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

internal data class ImportedMetadata(val title: String?, val author: String?, val year: Int?)

/** Read only metadata; FB2 body text is never loaded during import. */
internal fun readImportedMetadata(file: File): ImportedMetadata? = runCatching {
    if (file.extension == "epub") ZipFile(file).use { zip ->
        fun read(path: String): String {
            val entry = zip.getEntry(path) ?: error("Нет ресурса EPUB")
            require(entry.size in 0L..2_000_000L)
            return zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val container = Jsoup.parse(read("META-INF/container.xml"), "", Parser.xmlParser())
        val path = container.selectFirst("rootfile")?.attr("full-path") ?: error("Нет манифеста")
        val metadata = Jsoup.parse(read(path), "", Parser.xmlParser()).selectFirst("metadata")
        fun values(name: String) = metadata?.getAllElements()?.filter { it.tagName().substringAfter(':') == name }?.map { it.text() }.orEmpty()
        ImportedMetadata(values("title").firstOrNull(), values("creator").joinToString(", ").ifBlank { null },
            values("date").firstOrNull()?.take(4)?.toIntOrNull())
    } else if (file.extension == "fb2") file.inputStream().use { input ->
        val parser = Xml.newPullParser().apply { setInput(input, null) }
        var title: String? = null
        var titleInfo = false
        var publishInfo = false
        var author = false
        var year: Int? = null
        val names = mutableListOf<String>()
        var parts = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (parser.name.substringAfter(':')) {
                "body" -> break
                "title-info" -> titleInfo = true
                "publish-info" -> publishInfo = true
                "book-title" -> if (titleInfo) title = parser.nextText()
                "author" -> if (titleInfo) { author = true; parts = mutableListOf() }
                "first-name", "middle-name", "last-name", "nickname" -> if (author) parts.add(parser.nextText())
                "year" -> if (publishInfo) year = parser.nextText().trim().toIntOrNull()
            }
            if (event == XmlPullParser.END_TAG) when (parser.name.substringAfter(':')) {
                "title-info" -> titleInfo = false
                "publish-info" -> publishInfo = false
                "author" -> if (author) { names.add(parts.filter { it.isNotBlank() }.joinToString(" ")); author = false }
            }
            event = parser.next()
        }
        ImportedMetadata(title, names.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }, year)
    } else null
}.getOrNull()
