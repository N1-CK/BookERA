package com.example.bookera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.safeDrawingPadding
import com.example.bookera.data.local.LocalSettings
import androidx.lifecycle.ViewModelProvider
import com.example.bookera.ui.viewmodel.BookViewModelFactory
import androidx.navigation.compose.rememberNavController
import com.example.bookera.di.AppModule
import com.example.bookera.ui.navigation.BookNavGraph
import com.example.bookera.ui.theme.BookERATheme
import java.io.File

class MainActivity : ComponentActivity() {

    private var localSettings: LocalSettings? = null

    override fun onDestroy() { localSettings?.close(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppModule.provideDatabase(applicationContext)
        val dao = AppModule.provideBookDao(database)
        val repository = AppModule.provideBookRepository(dao, applicationContext)
        val viewModel = ViewModelProvider(this, BookViewModelFactory(repository))[com.example.bookera.ui.viewmodel.BookViewModel::class.java]
        val settings = LocalSettings(applicationContext).also { localSettings = it }


        val pluginsDir = File(filesDir, "plugins")
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        setContent {
            val theme by settings.theme.collectAsState()
            val dark = when (theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            BookERATheme(darkTheme = dark) {
                com.example.bookera.ui.theme.SystemChrome {
                    Surface(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        BookNavGraph(
                            navController = navController,
                            viewModel = viewModel,
                            settings = settings
                        )
                    }
                }
            }
        }
    }
}
