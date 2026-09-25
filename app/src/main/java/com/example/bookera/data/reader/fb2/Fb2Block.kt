package com.example.bookera.data.reader.fb2

sealed class Fb2Block {

    data class Paragraph(
        val text: String
    ) : Fb2Block()

    data class SectionTitle(
        val text: String
    ) : Fb2Block()

    data class Subtitle(
        val text: String
    ) : Fb2Block()

    data class Epigraph(
        val text: String
    ) : Fb2Block()

    data class Poem(
        val title: String?,
        val verses: List<String>
    ) : Fb2Block()

    data class Image(
        val id: String
    ) : Fb2Block()

    data class EmptyLine(
        val height: Int = 8
    ) : Fb2Block()
}