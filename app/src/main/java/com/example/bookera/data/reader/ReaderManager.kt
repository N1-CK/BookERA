@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bookera.data.reader

import androidx.compose.material3.ExperimentalMaterial3Api
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookera.data.reader.fb2.Fb2ReaderPlugin


class ReaderManager {

    private val readers =
        mutableListOf<ReaderPlugin>()

    init {
        registerReader(
            Fb2ReaderPlugin()
        )
        registerReader(EpubReaderPlugin())
        registerReader(PdfReaderPlugin())
        registerReader(TxtReaderPlugin())
    }

    fun registerReader(
        reader: ReaderPlugin
    ) {
        readers.add(reader)
    }

    fun getReaderForFile(
        filePath: String
    ): ReaderPlugin? {

        Log.d(
            "ReaderManager",
            "Looking for reader. filePath = $filePath"
        )

        val reader = readers.firstOrNull {
            val canHandle = it.canHandle(filePath)

            Log.d(
                "ReaderManager",
                "${it.name}: canHandle=$canHandle"
            )

            canHandle
        }

        Log.d(
            "ReaderManager",
            "Selected reader = ${reader?.name}"
        )

        return reader
    }

    @Composable
    fun ReaderScreen(
        filePath: String,
        bookId: Long,
        onBack: () -> Unit
    ) {

        val reader =
            getReaderForFile(filePath)

        if (reader != null) {

            reader.ReaderScreen(
                filePath = filePath,
                bookId = bookId,
                onBack = onBack
            )

        } else {

            UnsupportedFormatScreen(
                onBack = onBack
            )
        }
    }
}

@Composable
private fun UnsupportedFormatScreen(
    onBack: () -> Unit
) {

    Scaffold(

        topBar = {

            TopAppBar(
                title = {
                    Text("Reader")
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        androidx.compose.material3.Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }

    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                "Unsupported file format"
            )
        }
    }
}
