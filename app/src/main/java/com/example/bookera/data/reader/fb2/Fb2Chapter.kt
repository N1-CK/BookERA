package com.example.bookera.data.reader.fb2

data class Fb2Chapter(
    val id: String?,
    val title: String?,
    val blocks: List<Fb2Block>,
    val children: List<Fb2Chapter> = emptyList()
)