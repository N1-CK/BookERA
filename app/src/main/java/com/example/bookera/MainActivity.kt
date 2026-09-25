package com.example.bookera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.bookera.di.AppModule
import com.example.bookera.ui.navigation.BookNavGraph
import com.example.bookera.ui.theme.BookERATheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppModule.provideDatabase(applicationContext)
        val dao = AppModule.provideBookDao(database)
        val repository = AppModule.provideBookRepository(dao, applicationContext)
        val viewModel = AppModule.provideBookViewModel(repository, applicationContext)


        setContent {
            BookERATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    BookNavGraph(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
