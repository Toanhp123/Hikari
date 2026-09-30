package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** All IO stays host-owned. Redirects disabled; HTTPS origin and public DNS required. */
class LnReaderHost(context: Context, sourceId: String, private val origins: Set<String>) {
    init { require(sourceId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
    private val storage = context.getSharedPreferences("lnreader.$sourceId", Context.MODE_PRIVATE)
    fun callBytes(input: ByteArray): ByteArray = call(input.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
    private val deadline = SystemClock.elapsedRealtime() + 5000
    private var requests = 0
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(2, TimeUnit.SECONDS).dns { hostname ->
            InetAddress.getAllByName(hostname).toList().also { addresses ->
                require(addresses.isNotEmpty() && addresses.all {
                    !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
                    !it.isSiteLocalAddress && !it.isMulticastAddress
                }) { "Private network denied" }
            }
        }.build()

    fun call(input: String): String {
        check(SystemClock.elapsedRealtime() < deadline)
        require(input.toByteArray().size <= 1024 * 1024)
        val request = JSONObject(input)
        val result: Any? = when (request.getString("op")) {
            "fetch", "resource" -> {
                require(++requests <= 16)
                val url = request.getString("url").toHttpUrl()
                require(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty())
                require("${url.scheme}://${url.host}:${url.port}" in origins)
                val init = request.optJSONObject("init") ?: JSONObject()
                val method = init.optString("method", "GET").uppercase()
                require(method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"))
                val data = init.optString("body", "").toByteArray(Charsets.UTF_8)
                require(data.size <= 65536)
                val builder = Request.Builder().url(url)
                if (method in setOf("GET", "HEAD")) { require(data.isEmpty()); builder.method(method, null) }
                else builder.method(method, data.toRequestBody(null))
                init.optJSONObject("headers")?.let { headers -> headers.keys().forEach { key ->
                    require(key.lowercase() in setOf("accept", "accept-language", "user-agent", "referer", "content-type", "authorization", "cookie", "origin", "x-requested-with"))
                    builder.header(key, headers.getString(key))
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
        // A stuck OS DNS lookup cannot grow an unbounded worker pool.
        private val network = java.util.concurrent.ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            java.util.concurrent.ArrayBlockingQueue<Runnable>(4), java.util.concurrent.ThreadPoolExecutor.AbortPolicy())
    }
    private fun key(request: JSONObject): String = request.getString("key").also { require(it.length in 1..256) }
}
