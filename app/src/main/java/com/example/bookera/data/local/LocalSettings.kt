package com.example.bookera.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local settings; no account or network service. */
class LocalSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bookera_settings", Context.MODE_PRIVATE)
    private val _name = MutableStateFlow(prefs.getString("name", "Читатель") ?: "Читатель")
    val name = _name.asStateFlow()
    private val _enabled = MutableStateFlow(prefs.getStringSet("sources", setOf("openlibrary", "flibusta"))!!.toSet())
    val enabled = _enabled.asStateFlow()
    private val _lastRead = MutableStateFlow(prefs.getLong("last_read", 0L))
    val lastRead = _lastRead.asStateFlow()

    fun rename(value: String) {
        _name.value = value.take(40)
        prefs.edit().putString("name", _name.value).apply()
    }

    fun setSource(id: String, enabled: Boolean) {
        val next = if (enabled) _enabled.value + id else _enabled.value - id
        _enabled.value = next
        prefs.edit().putStringSet("sources", next).apply()
    }

    fun rememberReading(bookId: Long) {
        _lastRead.value = bookId
        prefs.edit().putLong("last_read", bookId).apply()
    }
}
