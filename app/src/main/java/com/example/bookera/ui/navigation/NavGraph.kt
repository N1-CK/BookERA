package com.example.bookera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.bookera.ui.screens.BookDetailScreen
import com.example.bookera.ui.screens.BookListScreen
import com.example.bookera.ui.viewmodel.BookViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.bookera.data.model.Book

object Routes {
    const val BOOK_LIST = "book_list"
    const val BOOK_DETAIL = "book_detail/{bookId}"

    const val READER = "reader/{bookId}"

    fun bookDetail(bookId: Long) = "book_detail/$bookId"

    fun reader(bookId: Long) = "reader/$bookId"
}

@Composable
fun BookNavGraph(
    navController: NavHostController,
    viewModel: BookViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.BOOK_LIST
    ) {
        composable(Routes.BOOK_LIST) {
            BookListScreen(
                viewModel = viewModel,
                navController = navController
            )
        }

        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onReadBook = { id ->
                    navController.navigate(
                        Routes.reader(id)
                    )
                }
            )
        }

        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("bookId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->

            val bookId =
                backStackEntry.arguments
                    ?.getLong("bookId")
                    ?: return@composable

            var book by remember {
                mutableStateOf<Book?>(null)
            }

            var isLoading by remember {
                mutableStateOf(true)
            }

            LaunchedEffect(bookId) {

                isLoading = true

                book =
                    viewModel.getBookById(bookId)

                isLoading = false
            }

            when {

                isLoading -> {

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        CircularProgressIndicator()
                    }
                }

                book != null &&
                        book!!.isDownloaded &&
                        book!!.localFilePath != null -> {

                    viewModel.ReaderScreen(
                        filePath =
                            book!!.localFilePath!!,

                        bookId =
                            bookId,

                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                else -> {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                text =
                                    "Book not downloaded"
                            )

                            Button(
                                onClick = {
                                    navController.popBackStack()
                                }
                            ) {

                                Text(
                                    text = "Go Back"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


