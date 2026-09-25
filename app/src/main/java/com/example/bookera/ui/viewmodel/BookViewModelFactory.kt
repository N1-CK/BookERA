package com.example.bookera.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bookera.data.local.repository.BookRepository
import com.example.bookera.data.local.LocalSettings

@Suppress("UNCHECKED_CAST")
class BookViewModelFactory(
    private val repository: BookRepository,
    private val settings: LocalSettings
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
            return BookViewModel(repository, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
