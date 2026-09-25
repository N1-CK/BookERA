// FlibustaPlugin.kt - ИСПРАВЛЕННАЯ ВЕРСИЯ (с правильными корутинами)
package com.example.bookera.data.plugin

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.http.Streaming
import java.io.File
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import retrofit2.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FlibustaApiService {
    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}

class FlibustaPlugin : DownloadPlugin {
    override val id = "flibusta"
    override val name = "Флибуста"
    override val version = "1.0.0"
    override val author = "Flibusta Team"
    override val description = "Поиск и скачивание книг с Флибусты"

    private val TAG = "FlibustaPlugin"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .build()
            chain.proceed(request)
        }
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val apiService: FlibustaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://flibusta.is/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlibustaApiService::class.java)
    }

    override suspend fun canHandle(url: String): Boolean {
        return url.contains("flibusta.is") ||
                url.contains(".fb2") ||
                url.contains(".epub") ||
                url.contains(".mobi") ||
                url.contains(".pdf")
    }

    // Используем withContext(Dispatchers.IO) для сетевых запросов
    private suspend fun fetchHtml(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Upgrade-Insecure-Requests", "1")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string()
                    else { Log.e(TAG, "Failed to fetch HTML: ${response.code}"); null }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching HTML: ${e.message}", e)
                null
            }
        }
    }

    override suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading from: $url")

                val response = apiService.downloadFile(url)

                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val body = response.body()
                if (body == null) {
                    return@withContext Result.failure(Exception("Response body is null"))
                }

                destination.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        val contentLength = body.contentLength()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            if (contentLength > 0) {
                                onProgress(totalBytes.toFloat() / contentLength)
                            }
                        }
                    }
                }

                Log.d(TAG, "Download complete: ${destination.absolutePath}")
                Result.success(destination)

            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }


    override suspend fun search(query: String): Result<List<BookSearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 SEARCH START: $query")

                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://flibusta.is/booksearch?ask=$encodedQuery"

                Log.d(TAG, "📡 URL: $searchUrl")

                val html = fetchHtml(searchUrl)

                if (html == null) {
                    Log.e(TAG, "❌ Failed to get HTML")
                    return@withContext Result.failure(
                        Exception("Failed to get HTML")
                    )
                }

                Log.d(TAG, "📄 HTML length: ${html.length}")

                // ВАЖНО: передаём searchUrl как baseUri
                val doc = Jsoup.parse(html, searchUrl)

                val results = mutableListOf<BookSearchResult>()

                val bookItems = doc.select("a[href]")
                    .filter { Regex("""^/b/\d+/?$""").matches(it.attr("href")) }
                    .mapNotNull { it.closest("li") ?: it.closest("tr") ?: it.parent() }
                    .distinct()

                Log.d(
                    TAG,
                    "📚 Found ${bookItems.size} book items in list"
                )

                for (item in bookItems) {
                    try {
                        val link = item.selectFirst("a[href*=/b/]")
                            ?: continue

                        // Исходный href
                        val rawHref = link.attr("href").trim()

                        // Абсолютный URL
                        val bookUrl = link.absUrl("href").trim()

                        Log.d(TAG, "🔗 raw href = $rawHref")
                        Log.d(TAG, "🔗 abs href = $bookUrl")

                        if (bookUrl.isBlank()) {
                            Log.e(
                                TAG,
                                "❌ Empty book URL for: ${link.text()}"
                            )
                            continue
                        }

                        val title = link.text().trim()

                        if (title.isBlank()) {
                            continue
                        }

                        val idMatch = Regex(
                            """/b/(\d+)"""
                        ).find(rawHref)

                        val id = idMatch?.groupValues?.get(1)

                        if (id.isNullOrBlank()) {
                            Log.e(
                                TAG,
                                "❌ Cannot extract ID from href: $rawHref"
                            )
                            continue
                        }

                        val authorLink = item.selectFirst(
                            "a[href*=/a/]"
                        )

                        val author =
                            authorLink?.text()?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?: "Unknown Author"

                        val result = BookSearchResult(
                            id = id,
                            title = title,
                            author = author,
                            coverUrl = null,
                            description = null,
                            downloadUrl = bookUrl
                        )

                        results.add(result)

                        Log.d(
                            TAG,
                            "✅ Found: $title by $author (ID: $id)"
                        )
                        Log.d(
                            TAG,
                            "🔗 URL: $bookUrl"
                        )

                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "⚠️ Error parsing book item",
                            e
                        )
                    }
                }

                // Удаляем только реальные дубликаты
                val uniqueResults = results
                    .distinctBy { it.id }

                Log.d(
                    TAG,
                    "📊 Total results: ${uniqueResults.size} (${results.size} before dedup)"
                )

                Result.success(uniqueResults)

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "❌ Search error: ${e.message}",
                    e
                )

                Result.failure(e)
            }
        }
    }

    override suspend fun findAllDownloadLinks(
        bookUrl: String
    ): List<DownloadLink> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔗 FIND DOWNLOAD LINKS: $bookUrl")

                if (bookUrl.isBlank()) {
                    Log.e(TAG, "❌ Book URL is empty")
                    return@withContext emptyList()
                }

                if (!bookUrl.startsWith("http://") &&
                    !bookUrl.startsWith("https://")
                ) {
                    Log.e(
                        TAG,
                        "❌ Invalid book URL: $bookUrl"
                    )
                    return@withContext emptyList()
                }

                val html = fetchHtml(bookUrl)

                if (html == null) {
                    Log.e(TAG, "❌ Failed to fetch book page")
                    return@withContext emptyList()
                }

                Log.d(
                    TAG,
                    "📄 Book page HTML length: ${html.length}"
                )

                val doc = Jsoup.parse(
                    html,
                    bookUrl
                )

                val result = mutableListOf<DownloadLink>()

                val bookId = Regex(
                    """/b/(\d+)"""
                )
                    .find(bookUrl)
                    ?.groupValues
                    ?.get(1)

                if (bookId == null) {
                    Log.e(
                        TAG,
                        "❌ Cannot determine book ID from: $bookUrl"
                    )
                    return@withContext emptyList()
                }

                Log.d(
                    TAG,
                    "📖 Book ID: $bookId"
                )

                doc.select("a[href]").forEach { link ->

                    val href = link.attr("href")
                        .trim()


                    val match = Regex(
                        """^/b/$bookId/(fb2|epub|mobi|pdf)/?$""",
                        RegexOption.IGNORE_CASE
                    ).find(href)

                    if (match == null) {
                        return@forEach
                    }

                    Log.d(
                        TAG,
                        "🔎 Checking href: $href"
                    )

                    val format =
                        match.groupValues[1].lowercase()

                    val absoluteUrl =
                        link.absUrl("href").trim()

                    Log.d(
                        TAG,
                        "🎯 Found format link: $href"
                    )

                    Log.d(
                        TAG,
                        "🎯 Absolute URL: $absoluteUrl"
                    )

                    if (absoluteUrl.isBlank()) {
                        Log.e(
                            TAG,
                            "❌ Absolute URL is empty"
                        )
                        return@forEach
                    }

                    if (result.none { it.url == absoluteUrl }) {
                        result.add(
                            DownloadLink(
                                url = absoluteUrl,
                                format = format
                            )
                        )

                        Log.d(
                            TAG,
                            "✅ FOUND DOWNLOAD: $format -> $absoluteUrl"
                        )
                    }
                }

                Log.d(
                    TAG,
                    "📚 Total download links: ${result.size}"
                )

                result

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "❌ Error finding download links: ${e.message}",
                    e
                )

                emptyList()
            }
        }
    }
}
