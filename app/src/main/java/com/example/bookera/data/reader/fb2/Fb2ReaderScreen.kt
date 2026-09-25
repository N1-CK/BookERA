@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bookera.data.reader.fb2

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bookera.data.local.ReaderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.bookera.data.local.ReadingProgressEntity
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun Fb2ReaderScreen(
    filePath: String,
    bookId: Long,
    onBack: () -> Unit
) {

    var book by remember {
        mutableStateOf<Fb2Book?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val context =
        androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(filePath) {

        try {

            book = withContext(
                Dispatchers.IO
            ) {

                Fb2Parser().parse(
                    File(filePath)
                )
            }

        } catch (e: Exception) {

            error =
                e.message
                    ?: "Unknown error"
        }
    }

    when {

        error != null -> {

            ErrorReaderScreen(
                error = error!!,
                onBack = onBack
            )
        }

        book == null -> {

            LoadingReaderScreen()
        }

        else -> {

            Fb2BookReader(
                book = book!!,
                bookId = bookId,
                database = ReaderDatabase.getInstance(context),
                onBack = onBack
            )
        }
    }
}

@Composable
private fun LoadingReaderScreen() {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.Center
    ) {

        CircularProgressIndicator(
            modifier =
                Modifier.align(
                    androidx.compose.ui.Alignment.CenterHorizontally
                )
        )
    }
}

@Composable
private fun ErrorReaderScreen(
    error: String,
    onBack: () -> Unit
) {

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text("Ошибка")
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }

    ) { paddingValues ->

        Text(
            text = error,
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        )
    }
}

@Composable
private fun Fb2BookReader(
    book: Fb2Book,
    bookId: Long,
    database: ReaderDatabase,
    onBack: () -> Unit
) {

    val listState =
        rememberLazyListState()

    val scope =
        rememberCoroutineScope()

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("reader_appearance", android.content.Context.MODE_PRIVATE) }
    var fontSize by remember { mutableStateOf(prefs.getFloat("size", 20f)) }
    var palette by remember { mutableStateOf(prefs.getInt("palette", 0)) }
    var showSettings by remember { mutableStateOf(false) }
    val pageColor = when (palette) { 1 -> Color(0xFFF4EAD5); 2 -> Color(0xFF20242B); else -> Color(0xFFFCFBFF) }
    val inkColor = if (palette == 2) Color(0xFFF0EEE8) else Color(0xFF22242A)

    var restored by remember {
        mutableStateOf(false)
    }

    val progress by remember {

        derivedStateOf {

            val total =
                listState.layoutInfo.totalItemsCount

            val current =
                listState.firstVisibleItemIndex

            if (total <= 1) {
                0f
            } else {

                (
                        current.toFloat() /
                                (total - 1)
                        )
                    .coerceIn(
                        0f,
                        1f
                    )
            }
        }
    }

    /*
     * Восстанавливаем последнюю позицию
     */
    LaunchedEffect(bookId) {

        val saved =
            withContext(Dispatchers.IO) {

                database
                    .readingProgressDao()
                    .getProgress(bookId)
            }

        if (saved != null) {

            val maxIndex =
                (book.chapters.size + 1)
                    .coerceAtLeast(0)

            val safeIndex =
                saved.firstVisibleItemIndex
                    .coerceIn(
                        0,
                        maxIndex
                    )

            listState.scrollToItem(
                index = safeIndex,
                scrollOffset =
                    saved.firstVisibleItemOffset
            )
        }

        restored = true
    }

    /*
     * Автоматически сохраняем позицию
     */
    LaunchedEffect(
        listState,
        restored
    ) {

        if (!restored) {
            return@LaunchedEffect
        }

        snapshotFlow {

            Pair(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )

        }.collectLatest {

            delay(700)

            val index =
                listState.firstVisibleItemIndex

            val offset =
                listState.firstVisibleItemScrollOffset

            val currentProgress =
                progress

            withContext(Dispatchers.IO) {

                database
                    .readingProgressDao()
                    .saveProgress(

                        ReadingProgressEntity(

                            bookId = bookId,

                            firstVisibleItemIndex =
                                index,

                            firstVisibleItemOffset =
                                offset,

                            progress =
                                currentProgress,

                            updatedAt =
                                System.currentTimeMillis()
                        )
                    )
            }
        }
    }

    Scaffold(

        containerColor = pageColor,

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        text =
                            book.title.ifBlank {
                                "FB2 Reader"
                            }
                                .take(30)
                    )
                },

                colors = TopAppBarDefaults.topAppBarColors(containerColor = pageColor, titleContentColor = inkColor, navigationIconContentColor = inkColor, actionIconContentColor = inkColor),

                actions = {
                    TextButton(onClick = { showSettings = true }) { Text("aA", color = inkColor) }
                },

                navigationIcon = {

                    IconButton(
                        onClick = {

                            scope.launch {

                                withContext(
                                    Dispatchers.IO
                                ) {

                                    database
                                        .readingProgressDao()
                                        .saveProgress(

                                            ReadingProgressEntity(
                                                bookId = bookId,

                                                firstVisibleItemIndex =
                                                    listState.firstVisibleItemIndex,

                                                firstVisibleItemOffset =
                                                    listState.firstVisibleItemScrollOffset,

                                                progress = progress,

                                                updatedAt =
                                                    System.currentTimeMillis()
                                            )
                                        )
                                }

                                onBack()
                            }
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        },

        bottomBar = {

            Column {
                Text("${(progress * 100).toInt()}% прочитано", Modifier.padding(horizontal = 20.dp, vertical = 5.dp), color = inkColor, style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }

    ) { paddingValues ->

        CompositionLocalProvider(LocalContentColor provides inkColor) { LazyColumn(
            state = listState,

            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageColor)
                .padding(
                    horizontal = 20.dp
                )
        ) {

            item {

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text =
                        book.authors.joinToString(
                            ", "
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    color = inkColor.copy(alpha = 0.7f)
                )

                Spacer(
                    modifier =
                        Modifier.height(24.dp)
                )
            }

            items(
                count = book.chapters.size
            ) { index ->

                Fb2ChapterView(
                    chapter =
                        book.chapters[index],

                    fontSize = fontSize,
                    inkColor = inkColor
                )
            }

            item {

                Spacer(
                    modifier =
                        Modifier.height(80.dp)
                )
            }
        } }
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Оформление страницы") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Размер шрифта: ${fontSize.toInt()}")
            Slider(value = fontSize, onValueChange = { fontSize = it; prefs.edit().putFloat("size", it).apply() }, valueRange = 14f..32f)
            Text("Цвет фона")
            listOf("Светлый", "Сепия", "Тёмный").forEachIndexed { index, label ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = palette == index, onClick = { palette = index; prefs.edit().putInt("palette", index).apply() })
                    Text(label)
                }
            }
        } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Готово") } }
    )
}

@Composable
private fun Fb2ChapterView(
    chapter: Fb2Chapter,
    fontSize: Float,
    inkColor: Color
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp
            )
    ) {

        chapter.title?.let { title ->

            Text(
                text = title,

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                modifier =
                    Modifier.padding(
                        bottom = 16.dp
                    )
            )
        }

        chapter.blocks.forEach { block ->

            when (block) {

                is Fb2Block.SectionTitle -> {

                    Text(
                        text = block.text,

                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,

                        modifier =
                            Modifier.padding(
                                top = 16.dp,
                                bottom = 12.dp
                            )
                    )
                }

                is Fb2Block.Paragraph -> {

                    Text(
                        text = block.text,

                        fontSize =
                            fontSize.sp,

                        fontFamily = FontFamily.Serif,

                        lineHeight =
                            (fontSize * 1.6f).sp,

                        modifier =
                            Modifier.padding(
                                vertical = 6.dp
                            )
                    )
                }

                is Fb2Block.Subtitle -> {

                    Text(
                        text = block.text,

                        fontSize =
                            (fontSize + 2).sp,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        modifier =
                            Modifier.padding(
                                vertical = 10.dp
                            )
                    )
                }

                is Fb2Block.Epigraph -> {

                    Text(
                        text = block.text,

                        fontSize =
                            (fontSize - 1).sp,

                        lineHeight =
                            (fontSize * 1.5f).sp,

                        color = inkColor.copy(alpha = 0.72f),

                        modifier =
                            Modifier.padding(
                                start = 32.dp,
                                top = 8.dp,
                                bottom = 12.dp
                            )
                    )
                }

                is Fb2Block.Poem -> {

                    PoemView(
                        poem = block,
                        fontSize = fontSize
                    )
                }

                is Fb2Block.Image -> {

                    Text(
                        text = "[Изображение]",

                        modifier =
                            Modifier.padding(
                                vertical = 12.dp
                            )
                    )
                }

                is Fb2Block.EmptyLine -> {

                    Spacer(
                        modifier =
                            Modifier.height(
                                block.height.dp
                            )
                    )
                }
            }
        }

        chapter.children.forEach { child ->

            Fb2ChapterView(
                chapter = child,
                fontSize = fontSize,
                inkColor = inkColor
            )
        }
    }
}

@Composable
private fun PoemView(
    poem: Fb2Block.Poem,
    fontSize: Float
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 16.dp
            )
    ) {

        poem.title?.let {

            Text(
                text = it,

                style =
                    MaterialTheme
                        .typography
                        .titleMedium,

                modifier =
                    Modifier.padding(
                        bottom = 12.dp
                    )
            )
        }

        poem.verses.forEach { verse ->

            Text(
                text = verse,

                fontSize =
                    fontSize.sp,

                lineHeight =
                    (fontSize * 1.5f).sp,

                modifier =
                    Modifier.padding(
                        vertical = 2.dp
                    )
            )
        }
    }
}
