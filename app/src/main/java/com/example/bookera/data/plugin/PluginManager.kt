// PluginManager.kt - обновленный
package com.example.bookera.data.plugin

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

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

    suspend fun searchAllPlugins(query: String): List<BookSearchResult> = coroutineScope {
        plugins.values.map { plugin -> async(Dispatchers.IO) {
            withTimeoutOrNull(8_000L) {
                runCatching { plugin.search(query).getOrThrow() }
                    .onFailure { Log.e(TAG, "${plugin.name}: ${it.message}") }
                    .getOrDefault(emptyList()).map { it.copy(pluginId = plugin.id) }
            }.orEmpty()
        } }.awaitAll().flatten()
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

        val file = File(booksDir, fileName)
        return plugin.download(url, file, onProgress)
    }

    private fun loadPlugins() {
        // Регистрируем плагины
        registerPlugin(OpenLibraryPlugin())
        registerPlugin(FlibustaPlugin())
        registerPlugin(GutenbergPlugin())

        // TODO: Добавить другие плагины (OPDS, CoolLib, и т.д.)

        // Загружаем внешние плагины из папки
        val pluginsDir = File(context.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { pluginFile ->
                if (pluginFile.extension == "jar" || pluginFile.extension == "dex") {
                    try {
                        val plugin = loadPluginFromFile(pluginFile)
                        registerPlugin(plugin)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load plugin: ${pluginFile.name}", e)
                    }
                }
            }
        }
    }

    private fun loadPluginFromFile(file: File): DownloadPlugin {
        // TODO: Реализовать динамическую загрузку через DexClassLoader
        return object : DownloadPlugin {
            override val id = "plugin_${file.nameWithoutExtension}"
            override val name = file.nameWithoutExtension
            override val version = "1.0"
            override val author = "Unknown"
            override val description = "Loaded from ${file.name}"

            override suspend fun canHandle(url: String): Boolean = false
            override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
                return Result.failure(Exception("Not implemented"))
            }
            override suspend fun search(query: String): Result<List<BookSearchResult>> {
                return Result.success(emptyList())
            }
        }
    }
}
