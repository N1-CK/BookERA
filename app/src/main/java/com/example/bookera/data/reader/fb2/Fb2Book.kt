package com.example.bookera.data.reader.fb2

data class Fb2Book(
    val title: String,
    val authors: List<String>,
    val annotation: String?,
    val chapters: List<Fb2Chapter>,
    val images: Map<String, Fb2Image>
)