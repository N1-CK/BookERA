package com.example.bookera.data.reader.fb2

data class Fb2Chapter(
    val id: String?,
    val title: String?,
    val blocks: List<Fb2Block>,
    val children: List<Fb2Chapter> = emptyList(),
    val content: List<Fb2Part> = emptyList()
)

sealed class Fb2Part {
    data class Text(val block: Fb2Block) : Fb2Part()
    data class Child(val chapter: Fb2Chapter) : Fb2Part()
}
