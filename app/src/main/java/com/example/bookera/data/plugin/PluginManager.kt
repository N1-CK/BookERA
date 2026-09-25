// PluginManager.kt - обновленный
package com.example.bookera.data.plugin

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PluginManager(private val context: Context) {

    private val plugins = mutableMapOf<String, DownloadPlugin>()
    private val TAG = "PluginManager"

    init {
        loadPlugins()
    }

    fun registerPlugin(plugin: DownloadPlugin) {
        plugins[plugin.id] = plugin
        Log.d(TAG, "Plugin registered: ${plugin.name} v${plugin.version}")
    }

    fun getPlugin(id: String): DownloadPlugin? = plugins[id]

    fun getAllPlugins(): List<DownloadPlugin> = plugins.values.toList()

    suspend fun searchAllPlugins(query: String, enabledIds: Set<String>): List<BookSearchResult> = coroutineScope {
        val active = plugins.values.filter { it.id in enabledIds }
        val responses = active.map { plugin -> async {
            try {
                plugin.search(query).onFailure { Log.e(TAG, "${plugin.name}: ${it.message}") }
                    .map { books -> books.map { it.copy(sourceId = plugin.id) } }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "${plugin.name}: ${e.message}")
                Result.failure<List<BookSearchResult>>(e)
            }
        } }.awaitAll()
        if (active.isNotEmpty() && responses.none { it.isSuccess }) {
            error("Источники недоступны. Проверь подключение и настройки.")
        }
        responses.flatMap { it.getOrNull().orEmpty() }.distinctBy { it.sourceId to it.id }
    }

    suspend fun downloadWithPlugin(
        pluginId: String,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<File> {
        val plugin = plugins[pluginId]
            ?: return Result.failure(Exception("Plugin not found: $pluginId"))

        val booksDir = File(context.getExternalFilesDir(null), "books")
        if (!booksDir.exists()) booksDir.mkdirs()

        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(booksDir, safeName)
        val result = plugin.download(url, file, onProgress)
        if (result.isFailure) file.delete()
        return result
    }

    private fun loadPlugins() {
        // Регистрируем плагины
        registerPlugin(OpenLibraryPlugin())
        registerPlugin(FlibustaPlugin())

        // Third-party executable code needs a reviewed API and sandbox; do not advertise inert JARs.
    }
}
