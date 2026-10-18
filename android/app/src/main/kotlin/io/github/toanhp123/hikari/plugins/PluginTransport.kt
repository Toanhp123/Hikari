package io.github.toanhp123.hikari.plugins

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.os.SystemClock

/** Safe machine-readable failure category; never include a download URL or JS text. */
internal class PluginTransferFailure(val code: String, val status: Int? = null) : IllegalStateException(code)

/** Bounded host-owned transport; no ambient headers, auth or cookies. */
internal class PluginTransport(private val client: OkHttpClient = PublicHttps.client(60)) : AutoCloseable {
    internal class AcceptedRequest(val id: String) {
        @Volatile var cancelled = false
        var started = false
        var call: Call? = null
    }
    private val requests = mutableMapOf<String, AcceptedRequest>()
    private var closed = false

    // One active worker plus four queued calls. Unknown/completed cancels retain nothing.
    @Synchronized fun accept(id: String): AcceptedRequest {
        validateId(id)
        check(!closed) { "Plugin manager closed" }
        check(id !in requests) { "Duplicate request ID" }
        check(requests.size < 5) { "Request limit reached" }
        return AcceptedRequest(id).also { requests[id] = it }
    }
    @Synchronized fun cancel(id: String) {
        validateId(id)
        requests[id]?.let { it.cancelled = true; it.call?.cancel() }
    }
    @Synchronized fun finish(request: AcceptedRequest) {
        if (requests[request.id] !== request) return
        requests.remove(request.id)
        request.cancelled = true
        request.call?.cancel()
        request.call = null
    }
    @Synchronized fun checkActive(request: AcceptedRequest) {
        if (closed || requests[request.id] !== request || request.cancelled) throw PluginTransferFailure("plugin_transfer_cancelled")
    }
    private fun validateId(id: String) { require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid request ID" } }
    fun read(url: String, limit: Int, request: AcceptedRequest): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        transfer(url, limit, request, output)
        return output.toByteArray()
    }
    fun transfer(raw: String, limit: Int, request: AcceptedRequest, output: OutputStream) {
        require(limit in 1..APK_LIMIT)
        synchronized(this) {
            checkActive(request)
            check(!request.started) { "Request already started" }
            request.started = true
        }
        var url = try { PublicHttps.url(raw) }
        catch (_: IllegalArgumentException) { throw PluginTransferFailure("plugin_url_rejected") }
        val deadline = SystemClock.elapsedRealtime() + 60_000
        for (redirects in 0..5) {
            checkActive(request)
            if (SystemClock.elapsedRealtime() >= deadline) throw PluginTransferFailure("plugin_network_timeout")
            val call = client.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity").build())
            call.timeout().timeout((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1), java.util.concurrent.TimeUnit.MILLISECONDS)
            synchronized(this) {
                checkActive(request)
                request.call = call
            }
            try {
                call.execute().use { response ->
                    checkActive(request)
                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                        url = try { PublicHttps.redirect(url, requireNotNull(response.header("Location")), redirects + 1) }
                        catch (_: IllegalArgumentException) { throw PluginTransferFailure("plugin_redirect_rejected") }
                    } else {
                        if (!response.isSuccessful) throw PluginTransferFailure("plugin_http_failed", response.code)
                        if (response.body.contentLength() > limit) throw PluginTransferFailure("plugin_download_too_large")
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                checkActive(request)
                                if (SystemClock.elapsedRealtime() >= deadline) throw PluginTransferFailure("plugin_network_timeout")
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > limit) throw PluginTransferFailure("plugin_download_too_large")
                                try { output.write(buffer, 0, count) }
                                catch (_: java.io.IOException) { throw PluginTransferFailure("plugin_stage_storage_failed") }
                            }
                        }
                        checkActive(request)
                        return
                    }
                }
            } catch (error: java.io.InterruptedIOException) {
                if (request.cancelled) throw PluginTransferFailure("plugin_transfer_cancelled")
                throw PluginTransferFailure("plugin_network_timeout")
            } catch (error: java.io.IOException) {
                if (request.cancelled) throw PluginTransferFailure("plugin_transfer_cancelled")
                throw PluginTransferFailure("plugin_network_failed")
            } finally {
                synchronized(this) { if (request.call === call) request.call = null }
                call.cancel()
            }
        }
        throw PluginTransferFailure("plugin_redirect_rejected")
    }
    @Synchronized override fun close() {
        closed = true
        requests.values.forEach { it.cancelled = true; it.call?.cancel(); it.call = null }
        requests.clear()
    }
    companion object { const val METADATA_LIMIT = 8 * 1024 * 1024; const val APK_LIMIT = 64 * 1024 * 1024 }
}

/** Staging publishes only fsynced, platform-inspected bytes. Tokens never contain user paths. */
internal class ApkStages(private val directory: File, private val inspector: ApkInspector,
    private val transport: PluginTransport) {
    init {
        directory.mkdirs()
        // No installer sessions exist yet. Stages are deliberately process-local;
        // startup removes abandoned bytes instead of claiming durable success.
        directory.listFiles().orEmpty().forEach { check(it.delete()) { "Cannot clean abandoned APK stage" } }
    }
    private val stages = mutableMapOf<String, Pair<File, ApkIdentity>>()
    private val reserved = mutableSetOf<String>()
    @Synchronized fun stage(url: String, family: String, pkg: String, code: Long, signer: String, request: PluginTransport.AcceptedRequest): Pair<String, ApkIdentity> {
        require(family in setOf("manga", "anime") && signer.matches(Regex("[a-f0-9]{64}")))
        require(stages.size < 4) { "APK staging limit reached" }
        val token = java.util.UUID.randomUUID().toString()
        val temporary = File(directory, "$token.tmp")
        val final = File(directory, "$token.apk")
        try {
            FileOutputStream(temporary).use { output ->
                transport.transfer(url, PluginTransport.APK_LIMIT, request, output)
                output.fd.sync()
            }
            val identity = try { inspector.inspectArchive(temporary) }
            catch (_: Exception) { throw PluginTransferFailure("plugin_apk_invalid") }
            try { identity.matchExpected(family, pkg, code, signer, inspector.installedForUpdate(pkg)) }
            catch (_: IllegalArgumentException) { throw PluginTransferFailure("plugin_apk_identity_mismatch") }
            transport.checkActive(request)
            check(temporary.renameTo(final)) { "Cannot publish APK stage" }
            stages[token] = final to identity
            return token to identity
        } catch (error: Throwable) { temporary.delete(); final.delete(); throw error }
    }
    @Synchronized fun inspected(token: String): ApkIdentity {
        val (file, expected) = stages[token] ?: error("Unknown or expired APK stage")
        val actual = inspector.inspectArchive(file)
        actual.matchExpected(expected.family, expected.packageName, expected.versionCode, expected.signers.single(), inspector.installedForUpdate(expected.packageName))
        check(actual == expected) { "Staged identity changed" }
        return actual
    }
    @Synchronized fun reserve(token: String): Pair<File, ApkIdentity> {
        check(token !in reserved) { "APK stage already in use" }
        val identity = inspected(token)
        reserved.add(token)
        return stages.getValue(token).first to identity
    }
    @Synchronized fun release(token: String, discard: Boolean) {
        reserved.remove(token)
        if (discard) discard(token)
    }
    @Synchronized fun discard(token: String) {
        check(token !in reserved) { "APK stage in use" }
        val (file, _) = stages[token] ?: error("Unknown or expired APK stage")
        check(!file.exists() || file.delete()) { "Cannot discard APK stage" }
        stages.remove(token)
    }
    fun close() { stages.values.forEach { it.first.delete() }; stages.clear() }
}
