package com.example.bookera.di

import android.content.Context
import com.example.bookera.data.local.dao.BookDao
import com.example.bookera.data.db.AppDatabase
import com.example.bookera.data.local.repository.BookRepository
import com.example.bookera.ui.viewmodel.BookViewModel

object AppModule {

    fun provideDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    fun provideBookDao(database: AppDatabase): BookDao {
        return database.bookDao()
    }

    fun provideBookRepository(dao: BookDao, context: Context): BookRepository {
        return BookRepository(dao, context)
    }

    fun provideBookViewModel(repository: BookRepository): BookViewModel {
        return BookViewModel(repository)
    }
}
