package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import android.os.SystemClock
import io.github.toanhp123.hikari.plugins.NovelPluginIds
import io.github.toanhp123.hikari.plugins.PublicHttps
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** All IO stays host-owned. Redirects disabled; HTTPS origin and public DNS required. */
class LnReaderHost(context: Context, sourceId: String, private val origins: Set<String>,
    private val admissionOnly: Boolean = false) {
    init { NovelPluginIds.requireValid(sourceId) }
    internal var admissionReason: String? = null
        private set
    internal val admissionDenied get() = admissionReason != null
    private val storage by lazy { context.getSharedPreferences("lnreader.$sourceId", Context.MODE_PRIVATE) }
    private class HostRejection(val code: String) : IllegalStateException("Host rejected operation")
    fun callBytes(input: ByteArray): ByteArray = try {
        call(input.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
    } catch (failure: Exception) {
        val cause = (failure as? java.util.concurrent.ExecutionException)?.cause ?: failure
        val reason = when (cause) {
            is HostRejection -> cause.code
            is java.util.concurrent.TimeoutException -> "network-timeout"
            else -> "network-request-rejected"
        }
        JSONObject().put("__hikariHostError", reason).toString().toByteArray(Charsets.UTF_8)
    }
    private val deadline = SystemClock.elapsedRealtime() + 8000
    private var requests = 0
    private val client by lazy { io.github.toanhp123.hikari.plugins.PublicHttps.client(5) }

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
                LnReaderHttpHeaders.apply(builder, init.optJSONObject("headers"), multipart != null)
                // OkHttp's automatic redirects are intentionally disabled. LNReader's
                // fetch normally follows redirects, so handle each hop explicitly
                // under the same origin/DNS policy; never forward credentials to
                // a different permitted origin without the plugin requesting them.
                val active = AtomicReference<okhttp3.Call>()
                val allowedOrigins = origins.map(::canonicalOrigin).toSet()
                val future = network.submit<Any> {
                    var current = builder.build()
                    for (hop in 0..5) {
                        check(SystemClock.elapsedRealtime() < deadline)
                        val nextCall = client.newCall(current)
                        active.set(nextCall)
                        val response = nextCall.execute()
                        try {
                            val location = response.header("Location")
                            if (response.code in setOf(301, 302, 303, 307, 308) && location != null) {
                                require(hop < 5) { "Redirect limit" }
                                val target = PublicHttps.redirect(current.url, location, hop + 1)
                                require(canonicalOrigin(target.toString()) in allowedOrigins) { "Redirect origin denied" }
                                val changedOrigin = canonicalOrigin(current.url.toString()) != canonicalOrigin(target.toString())
                                val next = current.newBuilder().url(target)
                                if (changedOrigin) {
                                    listOf("Authorization", "Cookie", "Origin", "Referer").forEach(next::removeHeader)
                                }
                                if (response.code == 303 || response.code in setOf(301, 302) && current.method == "POST") {
                                    next.method("GET", null).removeHeader("Content-Type")
                                }
                                current = next.build()
                                continue
                            }
                            val content = response.body
                            val bytes = if (content == null) byteArrayOf() else {
                                val stream = content.source()
                                stream.request(1024 * 1024 + 1L)
                                stream.buffer.readByteArray()
                            }
                            if (bytes.size > 1024 * 1024) throw HostRejection("response-too-large")
                            if (request.getString("op") == "resource") {
                                require(response.isSuccessful)
                                return@submit android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            }
                            // Expose only response metadata needed by public LNReader
                            // fetch API; never include request headers or native errors.
                            val headers = JSONObject()
                            for (name in listOf("content-type", "content-length", "location", "cache-control")) {
                                response.header(name)?.takeIf { it.length <= 4096 }?.let { headers.put(name, it) }
                            }
                            val charset = if (request.has("encoding")) {
                                val name = request.getString("encoding").lowercase(java.util.Locale.ROOT)
                                require(name in setOf("utf-8", "utf8", "gbk", "gb2312", "gb18030", "big5", "shift-jis", "shift_jis", "euc-jp"))
                                java.nio.charset.Charset.forName(name)
                            } else content?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                            return@submit JSONObject()
                                .put("status", response.code)
                                .put("statusText", response.message.take(256))
                                .put("url", response.request.url.toString())
                                .put("redirected", hop > 0)
                                .put("headers", headers)
                                .put("body", bytes.toString(charset))
                        } finally { response.close() }
                    }
                    error("Redirect limit")
                }
                try { future.get((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS) }
                finally { active.get()?.cancel(); future.cancel(true) }
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
