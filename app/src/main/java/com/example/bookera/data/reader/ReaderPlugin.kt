package com.example.bookera.data.reader

import androidx.compose.runtime.Composable
import java.io.File

interface ReaderPlugin {

    val supportedFormats: List<String>

    val name: String

    @Composable
    fun ReaderScreen(
        filePath: String,
        bookId: Long,
        onBack: () -> Unit
    )

    fun canHandle(filePath: String): Boolean {

        val file = File(filePath)

        val extension = file.extension
            .lowercase()
            .trim()

        return extension in supportedFormats.map {
            it.lowercase().trimStart('.')
        }
    }
}