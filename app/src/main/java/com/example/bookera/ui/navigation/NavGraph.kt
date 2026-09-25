package com.example.bookera.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.bookera.ui.screens.BookDetailScreen
import com.example.bookera.ui.screens.BookListScreen
import com.example.bookera.ui.screens.ProfileScreen
import com.example.bookera.ui.screens.SourcesScreen
import com.example.bookera.ui.viewmodel.BookViewModel

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val READ_TAB = "read_tab"
    const val PROFILE = "profile"
    const val SOURCES = "sources"
    const val BOOK_DETAIL = "book_detail/{bookId}"
    const val READER = "reader/{bookId}"
    fun bookDetail(bookId: Long) = "book_detail/$bookId"
    fun reader(bookId: Long) = "reader/$bookId"
}

@Composable
fun BookNavGraph(navController: NavHostController, viewModel: BookViewModel) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(Routes.HOME, Routes.LIBRARY, Routes.READ_TAB, Routes.PROFILE)
    Scaffold(bottomBar = {
        if (route in tabs) NavigationBar {
            listOf(
                Triple(Routes.HOME, "Главная", Icons.Default.Home),
                Triple(Routes.LIBRARY, "Библиотека", Icons.Default.List),
                Triple(Routes.READ_TAB, "Читалка", Icons.Default.MenuBook),
                Triple(Routes.PROFILE, "Профиль", Icons.Default.Person)
            ).forEach { (destination, label, icon) ->
                NavigationBarItem(selected = route == destination, onClick = {
                    navController.navigate(destination) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }, icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { BookListScreen(viewModel, navController) }
            composable(Routes.LIBRARY) { BookListScreen(viewModel, navController, library = true) }
            composable(Routes.READ_TAB) {
                val books by viewModel.books.collectAsState()
                val last by viewModel.lastRead.collectAsState()
                val downloaded = books.filter { it.isDownloaded && it.localFilePath != null }
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Читалка", style = MaterialTheme.typography.headlineLarge)
                    if (downloaded.isEmpty()) Text("Скачай FB2, чтобы начать читать")
                    downloaded.sortedByDescending { it.id == last }.forEach { book ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.rememberReading(book.id); navController.navigate(Routes.reader(book.id)) }) {
                            Text(book.title, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            composable(Routes.PROFILE) { ProfileScreen(viewModel, onSources = { navController.navigate(Routes.SOURCES) }) }
            composable(Routes.SOURCES) { SourcesScreen(viewModel, onBack = { navController.popBackStack() }) }
            composable(Routes.BOOK_DETAIL, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { back ->
                val id = back.arguments?.getLong("bookId") ?: return@composable
                BookDetailScreen(id, viewModel, onNavigateBack = { navController.popBackStack() }, onReadBook = {
                    viewModel.rememberReading(it)
                    navController.navigate(Routes.reader(it))
                })
            }
            composable(Routes.READER, arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { back ->
                val id = back.arguments?.getLong("bookId") ?: return@composable
                var book by remember(id) { mutableStateOf<com.example.bookera.data.model.Book?>(null) }
                LaunchedEffect(id) { book = viewModel.getBookById(id) }
                val path = book?.localFilePath
                if (path != null && book?.isDownloaded == true) {
                    viewModel.ReaderScreen(path, id) { navController.popBackStack() }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { navController.popBackStack() }) { Text("Книга не скачана · Назад") }
                    }
                }
            }
        }
    }
}
