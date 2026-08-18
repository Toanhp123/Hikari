package app.openstory.plugins.runtime.capabilities.http

import app.openstory.plugins.runtime.PluginCallResult
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

@Serializable
data class PluginHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Serializable
data class PluginHttpResponse(
    val status: Int,
    val body: String,
)

class PluginHttpCapability(
    client: OkHttpClient,
    private val credentials: ManagedCredentialProvider = ManagedCredentialProvider.NONE,
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addNetworkInterceptor(CompressedLimitInterceptor())
        .build()

    suspend fun execute(
        request: PluginHttpRequest,
        policy: PluginRequestPolicy,
    ): PluginCallResult<PluginHttpResponse> = try {
        withTimeout(policy.timeoutMillis) {
            val initialUri = requireAllowedUrl(request.url, policy.allowedHosts)
            executeRedirectChain(request, initialUri, policy)
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: HttpCapabilityFailure) {
        PluginCallResult.Failure(failure.code, failure.retryable)
    } catch (_: CompressedResponseBudgetExceeded) {
        PluginCallResult.Failure("plugin.http_compressed_response_too_large", retryable = false)
    } catch (_: ResponseBudgetExceeded) {
        PluginCallResult.Failure("plugin.http_response_too_large", retryable = false)
    } catch (_: IOException) {
        PluginCallResult.Failure("plugin.http_request_failed", retryable = true)
    }

    private suspend fun executeRedirectChain(
        initial: PluginHttpRequest,
        initialUri: URI,
        policy: PluginRequestPolicy,
    ): PluginCallResult<PluginHttpResponse> = withContext(Dispatchers.IO) {
        var current = initial
        var currentUri = initialUri
        var requests = 0
        var redirects = 0
        while (true) {
            requests++
            if (requests > policy.maxRequests) throw HttpCapabilityFailure("plugin.http_request_budget_exceeded")
            val response = client.newCall(
                buildRequest(current, policy, currentUri.host.lowercase()) {
                    tag(
                        CompressedByteLimit::class.java,
                        CompressedByteLimit(policy.maxCompressedResponseBytes),
                    )
                },
            ).execute()
            response.use {
                if (it.isRedirect) {
                    redirects++
                    if (redirects > policy.maxRedirects) {
                        throw HttpCapabilityFailure("plugin.http_redirect_budget_exceeded")
                    }
                    val location = it.header("Location")
                        ?: throw HttpCapabilityFailure("plugin.http_redirect_invalid")
                    val resolved = currentUri.resolve(location).toString()
                    currentUri = requireAllowedUrl(resolved, policy.allowedHosts)
                    current = PluginHttpRequest(url = resolved)
                } else {
                    val bytes = BoundedResponseReader.read(it.body, policy.maxDecompressedResponseBytes)
                    return@withContext PluginCallResult.Success(
                        PluginHttpResponse(it.code, bytes.toString(Charsets.UTF_8)),
                    )
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        PluginCallResult.Failure("plugin.http_request_failed", true)
    }

    internal suspend fun buildRequest(
        source: PluginHttpRequest,
        policy: PluginRequestPolicy,
        host: String,
        customize: Request.Builder.() -> Unit = {},
    ): Request {
        val bodyBytes = source.body?.encodeToByteArray()
        if ((bodyBytes?.size ?: 0) > policy.maxRequestBytes) {
            throw HttpCapabilityFailure("plugin.http_request_too_large")
        }
        val builder = Request.Builder().url(source.url)
        source.headers.forEach { (name, value) ->
            if (!FORBIDDEN_SCRIPT_HEADERS.contains(name.lowercase())) builder.header(name, value)
        }
        credentials.headers(policy.pluginId, host).forEach(builder::header)
        val body = bodyBytes?.toRequestBody(source.headers["Content-Type"]?.toMediaTypeOrNull())
        builder.method(source.method.uppercase(), body)
        builder.customize()
        return builder.build()
    }

    private fun requireAllowedUrl(url: String, allowedHosts: Set<String>): URI {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw HttpCapabilityFailure("plugin.http_url_invalid")
        val host = uri.host?.lowercase()
            ?: throw HttpCapabilityFailure("plugin.http_url_invalid")
        requireHttpTarget(uri.scheme == "https", "plugin.http_https_required")
        requireHttpTarget(host in allowedHosts, "plugin.http_domain_denied")
        requireHttpTarget(uri.userInfo == null, "plugin.http_url_invalid")
        return uri
    }

    private fun requireHttpTarget(condition: Boolean, code: String) {
        if (!condition) throw HttpCapabilityFailure(code)
    }

    private companion object {
        val FORBIDDEN_SCRIPT_HEADERS = setOf("authorization", "cookie", "proxy-authorization")
    }
}

private data class CompressedByteLimit(val maxBytes: Long)

private class CompressedLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val limit = chain.request().tag(CompressedByteLimit::class.java)?.maxBytes
        return if (limit == null) response else limitResponse(response, limit)
    }

    private fun limitResponse(response: Response, limit: Long): Response {
        val body = response.body
        if (body.contentLength() > limit) {
            response.close()
            throw CompressedResponseBudgetExceeded()
        }
        return response.newBuilder().body(CompressedLimitResponseBody(body, limit)).build()
    }
}

internal class CompressedLimitResponseBody(
    private val delegate: ResponseBody,
    private val maxBytes: Long,
) : ResponseBody() {
    private val limitedSource: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            private var totalBytes = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) {
                    totalBytes += read
                    if (totalBytes > maxBytes) throw CompressedResponseBudgetExceeded()
                }
                return read
            }
        }.buffer()
    }

    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun source(): BufferedSource = limitedSource
}

internal class CompressedResponseBudgetExceeded : IOException()

internal class HttpCapabilityFailure(
    val code: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : IOException(code, cause)
