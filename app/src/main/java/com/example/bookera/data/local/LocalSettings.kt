package com.example.bookera.data.local

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class LocalSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bookera_settings", Context.MODE_PRIVATE)
    private val _name = MutableStateFlow(prefs.getString("name", "Читатель") ?: "Читатель")
    val name = _name.asStateFlow()
    private val defaults = setOf("openlibrary", "flibusta", "gutenberg", "googlebooks", "archive", "wikisource")
    init {
        if (!prefs.getBoolean("catalogs_v2", false)) {
            prefs.edit().putStringSet("sources", prefs.getStringSet("sources", defaults).orEmpty() +
                setOf("gutenberg", "googlebooks", "archive", "wikisource")).putBoolean("catalogs_v2", true).apply()
        }
    }
    private val _enabled = MutableStateFlow(prefs.getStringSet("sources", defaults).orEmpty().toSet())
    val enabled = _enabled.asStateFlow()
    private val _lastRead = MutableStateFlow(prefs.getLong("last_read", 0L))
    val lastRead = _lastRead.asStateFlow()
    private val _theme = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val theme = _theme.asStateFlow()

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sources" -> _enabled.value = prefs.getStringSet("sources", defaults).orEmpty().toSet()
            "last_read" -> _lastRead.value = prefs.getLong("last_read", 0L)
            "theme" -> _theme.value = prefs.getString("theme", "system") ?: "system"
            "name" -> _name.value = prefs.getString("name", "Читатель") ?: "Читатель"
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun setTheme(value: String) {
        require(value in setOf("system", "light", "dark"))
        _theme.value = value
        prefs.edit().putString("theme", value).apply()
    }

    fun rememberReading(bookId: Long) {
        _lastRead.value = bookId
        prefs.edit().putLong("last_read", bookId).apply()
    }

    fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    fun rename(value: String) { _name.value = value.take(40); prefs.edit().putString("name", _name.value).apply() }
    fun setSource(id: String, enabled: Boolean) {
        val old = prefs.getStringSet("sources", defaults).orEmpty()
        val next = if (enabled) old + id else old - id
        _enabled.value = next; prefs.edit().putStringSet("sources", next).apply()
    }
    fun googleApiKey(): String = prefs.getString("google_api_key", "").orEmpty()
    fun setGoogleApiKey(value: String) { prefs.edit().putString("google_api_key", value.trim()).apply() }
    fun customCatalogs(): List<Triple<String, String, String>> = runCatching {
        JsonParser.parseString(prefs.getString("opds_catalogs", "[]")).asJsonArray.map { e ->
            val o = e.asJsonObject
            Triple(o.get("id").asString, o.get("name").asString, o.get("url").asString)
        }
    }.getOrDefault(emptyList())
    fun addCatalog(name: String, url: String) {
        require(name.isNotBlank() && url.toHttpUrlOrNull()?.isHttps == true) { "Укажите название и HTTPS-адрес OPDS" }
        val id = "opds_${java.util.UUID.randomUUID()}"
        val catalogs = customCatalogs() + Triple(id, name.trim().take(60), url.trim())
        val json = JsonArray().apply { catalogs.forEach { (key, title, feed) -> add(JsonObject().apply {
            addProperty("id", key); addProperty("name", title); addProperty("url", feed)
        }) } }
        prefs.edit().putString("opds_catalogs", json.toString()).apply()
        setSource(id, true)
    }
}
