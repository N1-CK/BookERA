package com.example.bookera.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext

/** One cancellable transport for catalogs. Cancelling a search closes the real HTTP call. */
@OptIn(kotlinx.coroutines.InternalCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object CatalogHttp {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
            .header("User-Agent", "BookERA/1.1 (https://github.com/N1-CK/BookERA)").build()) }.build()
    private val downloadClient = client.newBuilder().callTimeout(180, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    fun url(base: String, vararg params: Pair<String, String>): String = base.toHttpUrl().newBuilder()
        .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build().toString()
    suspend fun response(url: String, downloading: Boolean = false): Response = suspendCancellableCoroutine { continuation ->
        require(url.toHttpUrl().isHttps) { "Каталог должен использовать HTTPS" }
        val call = (if (downloading) downloadClient else client).newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }
        })
    }
    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        response(url).use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            val body = r.body ?: error("Пустой ответ")
            val callJob = coroutineContext[kotlinx.coroutines.Job]
            val handle = callJob?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { if (it != null) r.close() }
            try {
                body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(output.size() + n <= 8 * 1024 * 1024) { "Ответ каталога слишком большой" }
                        output.write(buffer, 0, n)
                    }
                    output.toString("UTF-8")
                }
            } finally { handle?.dispose() }
        }
    }
    suspend fun download(url: String, destination: File, onProgress: (Float) -> Unit): Result<File> = catalogResult {
        withContext(Dispatchers.IO) {
            try {
                response(url, downloading = true).use { r ->
                    check(r.isSuccessful) { "HTTP ${r.code}" }
                    val type = r.header("Content-Type").orEmpty()
                    require(!type.contains("text/html") && !type.contains("application/json")) { "Источник вернул страницу вместо книги" }
                    val body = r.body ?: error("Пустой файл")
                    val handle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
                        if (it != null) r.close()
                    }
                    try {
                    body.byteStream().use { input -> destination.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 80L * 1024 * 1024) { "Книга больше 80 МБ" }
                            output.write(buffer, 0, n)
                            if (body.contentLength() > 0) onProgress((total.toFloat() / body.contentLength()).coerceIn(0f, 1f))
                        }
                    } }
                    } finally { handle?.dispose() }
                    require(destination.length() > 0) { "Пустой файл" }
                    onProgress(1f)
                    destination
                }
            } catch (e: Exception) { destination.delete(); throw e }
        }
    }
}
suspend fun <T> catalogResult(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (e: CancellationException) { throw e }
catch (e: Exception) { Result.failure(e) }
