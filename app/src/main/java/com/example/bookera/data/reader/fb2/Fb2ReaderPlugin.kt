package com.example.bookera.data.reader.fb2

import androidx.compose.runtime.Composable
import com.example.bookera.data.reader.ReaderPlugin

class Fb2ReaderPlugin : ReaderPlugin {

    override val supportedFormats =
        listOf("fb2")

    override val name =
        "FB2 Reader"

    @Composable
    override fun ReaderScreen(
        filePath: String,
        bookId: Long,
        onBack: () -> Unit
    ) {
        Fb2ReaderScreen(
            filePath = filePath,
            bookId = bookId,
            onBack = onBack
        )
    }
}