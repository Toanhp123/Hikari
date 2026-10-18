package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import android.os.SystemClock
import io.github.toanhp123.hikari.plugins.NovelPluginIds
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** All IO stays host-owned. Redirects disabled; HTTPS origin and public DNS required. */
class LnReaderHost(context: Context, sourceId: String, private val origins: Set<String>,
    private val admissionOnly: Boolean = false) {
    init { NovelPluginIds.requireValid(sourceId) }
    internal var admissionReason: String? = null
        private set
    internal val admissionDenied get() = admissionReason != null
    private val storage by lazy { context.getSharedPreferences("lnreader.$sourceId", Context.MODE_PRIVATE) }
    fun callBytes(input: ByteArray): ByteArray = call(input.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
    private val deadline = SystemClock.elapsedRealtime() + 5000
    private var requests = 0
    private val client by lazy { io.github.toanhp123.hikari.plugins.PublicHttps.client(2) }

    fun call(input: String): String {
        val request = if (admissionOnly) {
            try {
                require(input.toByteArray().size <= 1024 * 1024)
                JSONObject(input).also { require(it.getString("op") == "absoluteUrl") }
            } catch (_: Exception) {
                if (admissionReason == null) admissionReason = when (runCatching { JSONObject(input).getString("op") }.getOrNull()) {
                    "unsupportedModule" -> "unsupported-module"
                    "missingRequiredMethod" -> "missing-required-method"
                    "invalidMetadata" -> "export-metadata-mismatch"
                    else -> "host-operation-denied"
                }
                error("Admission host operation denied")
            }
        } else {
            require(input.toByteArray().size <= 1024 * 1024)
            JSONObject(input)
        }
        check(SystemClock.elapsedRealtime() < deadline)
        val result: Any? = when (request.getString("op")) {
            "fetch", "resource" -> {
                require(++requests <= 16)
                val url = io.github.toanhp123.hikari.plugins.PublicHttps.url(request.getString("url"))
                require(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty())
                require(canonicalOrigin(url.toString()) in origins.map(::canonicalOrigin))
                val init = request.optJSONObject("init") ?: JSONObject()
                val method = init.optString("method", "GET").uppercase()
                require(method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"))
                val body = init.opt("body")
                // JS FormData is a data-only envelope. Kotlin alone generates
                // multipart boundaries, validates sizes and owns the request.
                val multipart = body as? JSONObject
                val requestBody = requestBody(body)
                val builder = Request.Builder().url(url)
                if (method in setOf("GET", "HEAD")) {
                    require(body == null || body == JSONObject.NULL || body == "")
                    builder.method(method, null)
                } else builder.method(method, requestBody)
                init.optJSONObject("headers")?.let { headers -> headers.keys().forEach { key ->
                    require(key.lowercase() in setOf("accept", "accept-language", "user-agent", "referer", "content-type", "authorization", "cookie", "origin", "x-requested-with", "dnt", "upgrade-insecure-requests"))
                    require(multipart == null || !key.equals("content-type", ignoreCase = true))
                    val value = headers.getString(key)
                    require(value.length <= 4096 && value.none { it == '\r' || it == '\n' })
                    builder.header(key, value)
                } }
                val call = client.newCall(builder.build())
                val future = network.submit<Any> { call.execute().use { response ->
                    val body = response.body
                    val stream = body.source()
                    stream.request(1024 * 1024 + 1L)
                    val bytes = stream.buffer.readByteArray()
                    require(bytes.size <= 1024 * 1024)
                    if (request.getString("op") == "resource") {
                        require(response.isSuccessful)
                        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    } else JSONObject().put("status", response.code).put("body", bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8))
                } }
                try { future.get((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS) }
                finally { call.cancel(); future.cancel(true) }
            }
            "delay" -> {
                require(++requests <= 16)
                val ms = request.getInt("ms")
                require(ms in 0..2000 && request.keys().asSequence().toSet() == setOf("op", "ms"))
                Thread.sleep(ms.toLong()); null
            }
            "keys" -> storage.all.keys.filter { it.startsWith("v3:db:") }.sorted().let { keys ->
                require(keys.size <= 128)
                org.json.JSONArray(keys)
            }
            "get" -> storage.getString(key(request), null)
            "set" -> {
                val key = key(request); val value = request.getString("value")
                val next = storage.all.toMutableMap().apply { put(key, value) }
                require(next.size <= 128 && next.entries.sumOf { it.key.toByteArray().size + it.value.toString().toByteArray().size } <= 65536)
                check(storage.edit().putString(key, value).commit()); null
            }
            "remove" -> { check(storage.edit().remove(key(request)).commit()); null }
            "absoluteUrl" -> request.getString("base").toHttpUrl().resolve(request.getString("path"))?.toString()
            else -> error("Unsupported host operation")
        }
        check(SystemClock.elapsedRealtime() < deadline)
        return org.json.JSONArray().put(result ?: JSONObject.NULL).toString().let { it.substring(1, it.length - 1) }
    }
    companion object {
        /** A small, testable boundary between the JS form envelope and OkHttp. */
        internal fun requestBody(body: Any?): RequestBody {
            val multipart = body as? JSONObject
            return if (multipart != null) {
                require(multipart.getString("__hikariMultipart") == "v1")
                require(multipart.keys().asSequence().toSet() == setOf("__hikariMultipart", "fields"))
                val fields = multipart.getJSONArray("fields")
                require(fields.length() in 1..32)
                var size = 0L
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                repeat(fields.length()) { index ->
                    val field = fields.getJSONObject(index)
                    require(field.keys().asSequence().toSet() == setOf("name", "value"))
                    require(field.get("name") is String && field.get("value") is String)
                    val name = field.getString("name")
                    val value = field.getString("value")
                    require(name.isNotBlank() && name.length <= 256 &&
                        name.none { it < ' ' || it == '\u007f' || it == '"' || it == '\\' })
                    size += name.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size + 256
                    require(size <= 65536)
                    form.addFormDataPart(name, value)
                }
                form.build()
            } else {
                require(body == null || body == JSONObject.NULL || body is String)
                val data = if (body is String) body.toByteArray(Charsets.UTF_8) else byteArrayOf()
                require(data.size <= 65536)
                data.toRequestBody(null)
            }
        }
        internal fun canonicalOrigin(value: String): String {
            val url = io.github.toanhp123.hikari.plugins.PublicHttps.url(value)
            return "https://${url.host.trimEnd('.')}"
        }
        // A stuck OS DNS lookup cannot grow an unbounded worker pool.
        private val network by lazy { java.util.concurrent.ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            java.util.concurrent.ArrayBlockingQueue<Runnable>(4), java.util.concurrent.ThreadPoolExecutor.AbortPolicy()) }
    }
    private fun key(request: JSONObject): String = request.getString("key").also { require(it.length in 1..256) }
}
