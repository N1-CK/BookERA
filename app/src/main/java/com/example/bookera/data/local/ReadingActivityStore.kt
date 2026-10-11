package com.example.bookera.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class ReadingVisit(val bookId: Long, val timestamp: Long, val progress: Float)

/** Existing reader anchors stay authoritative; this index only supplies the home screen. */
class ReadingActivityStore private constructor(context: Context) {
    private val activity = context.getSharedPreferences("reading_activity", 0)
    private val settings = context.getSharedPreferences("bookera_settings", 0)
    private val reader = context.getSharedPreferences("reader_settings", 0)
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _revision.value++ }
    init { activity.registerOnSharedPreferenceChangeListener(listener); reader.registerOnSharedPreferenceChangeListener(listener) }
    fun visits(): List<ReadingVisit> = activity.all.keys.filter { it.startsWith("visit_") }.mapNotNull { key ->
        key.removePrefix("visit_").toLongOrNull()?.let { ReadingVisit(it, activity.getLong(key, 0), activity.getFloat("progress_$it", 0f)) }
    }.sortedByDescending { it.timestamp }
    fun remember(bookId: Long, progress: Float) {
        settings.edit().putLong("last_read", bookId).apply()
        activity.edit().putLong("visit_$bookId", System.currentTimeMillis()).putFloat("progress_$bookId", progress.coerceIn(0f, 1f)).apply()
    }
    fun markReadingDay() {
        val today = LocalDate.now().toString()
        val days = activity.getStringSet("days", emptySet()).orEmpty() + today
        activity.edit().putStringSet("days", days.filter { runCatching { LocalDate.parse(it) >= LocalDate.now().minusDays(370) }.getOrDefault(false) }.toSet()).apply()
    }
    fun days(): Set<LocalDate> = activity.getStringSet("days", emptySet()).orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    fun streak(): Int {
        val dates = days()
        var date = LocalDate.now().let { if (it in dates) it else it.minusDays(1) }
        var count = 0
        while (date in dates) { count++; date = date.minusDays(1) }
        return count
    }
    fun forget(bookId: Long) { activity.edit().remove("visit_$bookId").remove("progress_$bookId").apply() }
    companion object {
        @Volatile private var instance: ReadingActivityStore? = null
        fun get(context: Context): ReadingActivityStore = instance ?: synchronized(this) {
            instance ?: ReadingActivityStore(context.applicationContext).also { instance = it }
        }
    }
}
