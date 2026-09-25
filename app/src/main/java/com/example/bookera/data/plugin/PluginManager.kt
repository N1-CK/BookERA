// PluginManager.kt - обновленный
package com.example.bookera.data.plugin

import android.content.Context
import android.util.Log
import java.io.File

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

    suspend fun searchAllPlugins(query: String): List<BookSearchResult> {
        val results = mutableListOf<BookSearchResult>()
        plugins.values.forEach { plugin ->
            try {
                val result = plugin.search(query)
                result.onSuccess { books ->
                    results.addAll(books)
                    Log.d(TAG, "Plugin ${plugin.name} found ${books.size} books")
                }.onFailure { error ->
                    Log.e(TAG, "Plugin ${plugin.name} search failed: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Plugin ${plugin.name} error: ${e.message}")
            }
        }
        return results
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