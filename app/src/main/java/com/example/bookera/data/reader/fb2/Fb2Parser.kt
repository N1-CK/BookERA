package com.example.bookera.data.reader.fb2

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream

class Fb2Parser {

    fun parse(file: File): Fb2Book {

        require(file.exists()) {
            "FB2 file does not exist: ${file.absolutePath}"
        }

        /*
         * Некоторые FB2-файлы из интернета содержат
         * некорректные XML entity references.
         *
         * Например:
         *
         *   "Он сказал & пошел дальше"
         *
         * Вместо:
         *
         *   "Он сказал &amp; пошел дальше"
         *
         * Поэтому сначала нормализуем XML.
         */
        val xml = FileInputStream(file).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }

        val fixedXml = sanitizeXml(xml)

        val parser = Xml.newPullParser()

        parser.setInput(
            fixedXml.reader()
        )

        return parseBook(parser)
    }

    /**
     * Исправляет наиболее распространённые проблемы
     * с XML entity references в FB2.
     */
    private fun sanitizeXml(xml: String): String {

        /*
         * Сначала исправляем одиночные &.
         *
         * Оставляем:
         *
         * &amp;
         * &lt;
         * &gt;
         * &quot;
         * &apos;
         * &#123;
         * &#x1F600;
         */
        val result = StringBuilder(xml.length)

        var i = 0

        while (i < xml.length) {

            val char = xml[i]

            if (char != '&') {
                result.append(char)
                i++
                continue
            }

            /*
             * Ищем ближайший ;
             */
            val semicolon = xml.indexOf(';', i + 1)

            if (semicolon == -1) {

                // '&' без ';'
                result.append("&amp;")
                i++

                continue
            }

            val entityBody =
                xml.substring(
                    i + 1,
                    semicolon
                )

            val isValidEntity =
                entityBody == "amp" ||
                        entityBody == "lt" ||
                        entityBody == "gt" ||
                        entityBody == "quot" ||
                        entityBody == "apos" ||
                        entityBody.matches(
                            Regex("#[0-9]+")
                        ) ||
                        entityBody.matches(
                            Regex("#x[0-9a-fA-F]+")
                        )

            if (isValidEntity) {

                /*
                 * Нормальная XML entity.
                 */
                result.append(
                    xml,
                    i,
                    semicolon + 1
                )

            } else {

                /*
                 * Неизвестная entity.
                 *
                 * Например:
                 *
                 * &nbsp;
                 * &mdash;
                 * &hellip;
                 *
                 * Android XmlPullParser может упасть
                 * на таких entity.
                 *
                 * Оставляем её как обычный текст.
                 */
                result.append("&amp;")
                result.append(entityBody)
                result.append(";")
            }

            i = semicolon + 1
        }

        return result.toString()
    }

    private fun parseBook(
        parser: XmlPullParser
    ): Fb2Book {

        var title = "Unknown"

        val authors = mutableListOf<String>()

        var annotation: String? = null

        val chapters = mutableListOf<Fb2Chapter>()

        val images = mutableMapOf<String, Fb2Image>()

        while (
            parser.next() != XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType != XmlPullParser.START_TAG
            ) {
                continue
            }

            when (parser.name) {

                "book-title" -> {

                    title =
                        readElementText(parser)
                }

                "author" -> {

                    val author =
                        readAuthor(parser)

                    if (author.isNotBlank()) {
                        authors.add(author)
                    }
                }

                "annotation" -> {

                    annotation =
                        readElementText(parser)
                }

                "body" -> {

                    chapters +=
                        parseBody(parser)
                }

                "binary" -> {

                    val image =
                        parseBinary(parser)

                    if (image != null) {

                        images[image.id] =
                            image
                    }
                }
            }
        }

        return Fb2Book(
            title = title,
            authors = authors,
            annotation = annotation,
            chapters = chapters,
            images = images
        )
    }

    private fun parseBody(
        parser: XmlPullParser
    ): List<Fb2Chapter> {

        val result =
            mutableListOf<Fb2Chapter>()

        while (
            parser.next() != XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType ==
                XmlPullParser.END_TAG &&
                parser.name == "body"
            ) {
                break
            }

            if (
                parser.eventType ==
                XmlPullParser.START_TAG &&
                parser.name == "section"
            ) {

                result +=
                    parseSection(parser)
            }
        }

        return result
    }

    private fun parseSection(
        parser: XmlPullParser
    ): Fb2Chapter {

        var title: String? = null

        val id =
            parser.getAttributeValue(
                null,
                "id"
            )

        val blocks =
            mutableListOf<Fb2Block>()

        val children =
            mutableListOf<Fb2Chapter>()

        while (
            parser.next() != XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType ==
                XmlPullParser.END_TAG &&
                parser.name == "section"
            ) {
                break
            }

            if (
                parser.eventType !=
                XmlPullParser.START_TAG
            ) {
                continue
            }

            when (parser.name) {

                "title" -> {

                    title =
                        readTitle(parser)

                    if (!title.isNullOrBlank()) {

                        blocks.add(
                            Fb2Block.SectionTitle(
                                title
                            )
                        )
                    }
                }

                "p" -> {

                    val text =
                        readElementText(parser)

                    if (text.isNotBlank()) {

                        blocks.add(
                            Fb2Block.Paragraph(text)
                        )
                    }
                }

                "subtitle" -> {

                    val text =
                        readElementText(parser)

                    if (text.isNotBlank()) {

                        blocks.add(
                            Fb2Block.Subtitle(text)
                        )
                    }
                }

                "epigraph" -> {

                    val text =
                        readElementText(parser)

                    if (text.isNotBlank()) {

                        blocks.add(
                            Fb2Block.Epigraph(text)
                        )
                    }
                }

                "poem" -> {

                    blocks.add(
                        parsePoem(parser)
                    )
                }

                "image" -> {

                    val imageId =
                        getImageId(parser)

                    if (imageId != null) {

                        blocks.add(
                            Fb2Block.Image(imageId)
                        )
                    }
                }

                "empty-line" -> {

                    blocks.add(
                        Fb2Block.EmptyLine()
                    )

                    skipCurrentElement(parser)
                }

                "section" -> {

                    children.add(
                        parseSection(parser)
                    )
                }
            }
        }

        return Fb2Chapter(
            id = id,
            title = title,
            blocks = blocks,
            children = children
        )
    }

    private fun parsePoem(
        parser: XmlPullParser
    ): Fb2Block.Poem {

        var title: String? = null

        val verses =
            mutableListOf<String>()

        while (
            parser.next() != XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType ==
                XmlPullParser.END_TAG &&
                parser.name == "poem"
            ) {
                break
            }

            if (
                parser.eventType !=
                XmlPullParser.START_TAG
            ) {
                continue
            }

            when (parser.name) {

                "title" -> {

                    title =
                        readTitle(parser)
                }

                "stanza" -> {

                    verses +=
                        parseStanza(parser)
                }
            }
        }

        return Fb2Block.Poem(
            title = title,
            verses = verses
        )
    }

    private fun parseStanza(
        parser: XmlPullParser
    ): List<String> {

        val result =
            mutableListOf<String>()

        while (
            parser.next() != XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType ==
                XmlPullParser.END_TAG &&
                parser.name == "stanza"
            ) {
                break
            }

            if (
                parser.eventType ==
                XmlPullParser.START_TAG &&
                parser.name == "v"
            ) {

                val text =
                    readElementText(parser)

                if (text.isNotBlank()) {
                    result.add(text)
                }
            }
        }

        return result
    }

    private fun readTitle(
        parser: XmlPullParser
    ): String {

        return readElementText(parser)
    }

    private fun readAuthor(
        parser: XmlPullParser
    ): String {

        var firstName = ""
        var middleName = ""
        var lastName = ""

        while (
            parser.next() != XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType ==
                XmlPullParser.END_TAG &&
                parser.name == "author"
            ) {
                break
            }

            if (
                parser.eventType !=
                XmlPullParser.START_TAG
            ) {
                continue
            }

            when (parser.name) {

                "first-name" -> {

                    firstName =
                        readElementText(parser)
                }

                "middle-name" -> {

                    middleName =
                        readElementText(parser)
                }

                "last-name" -> {

                    lastName =
                        readElementText(parser)
                }
            }
        }

        return listOf(
            firstName,
            middleName,
            lastName
        )
            .filter {
                it.isNotBlank()
            }
            .joinToString(" ")
    }

    private fun readElementText(
        parser: XmlPullParser
    ): String {

        val result =
            StringBuilder()

        var depth = 1

        while (
            depth > 0 &&
            parser.next() !=
            XmlPullParser.END_DOCUMENT
        ) {

            when (parser.eventType) {

                XmlPullParser.TEXT -> {

                    result.append(
                        parser.text
                    )
                }

                XmlPullParser.START_TAG -> {

                    depth++
                }

                XmlPullParser.END_TAG -> {

                    depth--
                }
            }
        }

        return result
            .toString()
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun getImageId(
        parser: XmlPullParser
    ): String? {

        val href =
            parser.getAttributeValue(
                "http://www.w3.org/1999/xlink",
                "href"
            )
                ?: parser.getAttributeValue(
                    null,
                    "l:href"
                )

        return href
            ?.removePrefix("#")
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }

    private fun parseBinary(
        parser: XmlPullParser
    ): Fb2Image? {

        val id =
            parser.getAttributeValue(
                null,
                "id"
            )
                ?: return null

        val contentType =
            parser.getAttributeValue(
                null,
                "content-type"
            )
                ?: "image/jpeg"

        val base64 =
            StringBuilder()

        while (
            parser.next() !=
            XmlPullParser.END_DOCUMENT
        ) {

            if (
                parser.eventType ==
                XmlPullParser.END_TAG &&
                parser.name == "binary"
            ) {
                break
            }

            if (
                parser.eventType ==
                XmlPullParser.TEXT
            ) {

                base64.append(
                    parser.text
                )
            }
        }

        return try {

            val data =
                Base64.decode(
                    base64
                        .toString()
                        .replace(
                            Regex("\\s+"),
                            ""
                        ),
                    Base64.DEFAULT
                )

            Fb2Image(
                id = id,
                contentType = contentType,
                data = data
            )

        } catch (
            e: Exception
        ) {

            null
        }
    }

    private fun skipCurrentElement(
        parser: XmlPullParser
    ) {

        var depth = 1

        while (
            depth > 0 &&
            parser.next() !=
            XmlPullParser.END_DOCUMENT
        ) {

            when (parser.eventType) {

                XmlPullParser.START_TAG -> {
                    depth++
                }

                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }
    }
}