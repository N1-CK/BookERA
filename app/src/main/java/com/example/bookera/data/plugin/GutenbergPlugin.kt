package com.example.bookera.data.plugin

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Gutendex indexes Project Gutenberg's freely accessible editions. No API key. */
class GutenbergPlugin : DownloadPlugin {
    override val id = "gutenberg"
    override val name = "Project Gutenberg"
    override val version = "1.0"
    override val author = "Gutendex / Project Gutenberg"
    override val description = "Public-domain EPUB and TXT editions; check local copyright rules"

    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).followRedirects(true).build()

    override suspend fun canHandle(url: String) = url.startsWith("https://www.gutenberg.org/") ||
        url.startsWith("https://gutenberg.org/") || url.startsWith("https://gutendex.com/")

    override suspend fun search(query: String): Result<List<BookSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://gutendex.com/books?search=${URLEncoder.encode(query, "UTF-8")}" 
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Gutendex HTTP ${response.code}" }
                val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                root.getAsJsonArray("results").mapNotNull { element ->
                    val book = element.asJsonObject
                    if (book.get("copyright")?.isJsonNull != false || book.get("copyright").asBoolean) return@mapNotNull null
                    val formats = book.getAsJsonObject("formats")
                    val epub = formats.get("application/epub+zip")?.asString
                    val txt = formats.entrySet().firstOrNull { it.key.startsWith("text/plain") && it.value.asString.startsWith("https:") }?.value?.asString
                    val fileUrl = epub?.takeIf { it.startsWith("https:") } ?: txt ?: return@mapNotNull null
                    val authors = book.getAsJsonArray("authors").map { it.asJsonObject.get("name").asString }
                    BookSearchResult("gutenberg:${book.get("id").asLong}", book.get("title").asString,
                        authors.joinToString(", ").ifBlank { "Unknown author" },
                        formats.get("image/jpeg")?.asString, null, fileUrl, id)
                }
            }
        }
    }

    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(canHandle(url)) { "Unexpected download host" }
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "Download HTTP ${response.code}" }
                    check(!response.header("Content-Type").orEmpty().contains("text/html")) { "Book file unavailable" }
                    val body = response.body ?: error("Empty download")
                    try {
                        body.byteStream().use { input ->
                            destination.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var count: Int
                                var total = 0L
                                while (input.read(buffer).also { count = it } != -1) {
                                    output.write(buffer, 0, count)
                                    total += count
                                    if (body.contentLength() > 0) onProgress(total.toFloat() / body.contentLength())
                                }
                            }
                        }
                        check(destination.length() > 0) { "Empty book file" }
                        destination
                    } catch (e: Exception) {
                        destination.delete()
                        throw e
                    }
                }
            }
        }
}
