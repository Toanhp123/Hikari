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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
        .build()

    suspend fun execute(
        request: PluginHttpRequest,
        policy: PluginRequestPolicy,
    ): PluginCallResult<PluginHttpResponse> = try {
        withTimeout(policy.timeoutMillis) {
            executeRedirectChain(request, policy)
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: HttpCapabilityFailure) {
        PluginCallResult.Failure(failure.code, failure.retryable)
    } catch (_: ResponseBudgetExceeded) {
        PluginCallResult.Failure("plugin.http_response_too_large", retryable = false)
    } catch (_: IOException) {
        PluginCallResult.Failure("plugin.http_request_failed", retryable = true)
    }

    internal fun validateTarget(
        url: String,
        allowedHosts: Set<String>,
    ): PluginCallResult<Unit> = try {
        requireAllowedUrl(url, allowedHosts)
        PluginCallResult.Success(Unit)
    } catch (failure: HttpCapabilityFailure) {
        PluginCallResult.Failure(failure.code, failure.retryable)
    }

    private suspend fun executeRedirectChain(
        initial: PluginHttpRequest,
        policy: PluginRequestPolicy,
    ): PluginCallResult<PluginHttpResponse> = withContext(Dispatchers.IO) {
        var current = initial
        var requests = 0
        var redirects = 0
        while (true) {
            requests++
            if (requests > policy.maxRequests) throw HttpCapabilityFailure("plugin.http_request_budget_exceeded")
            val uri = requireAllowedUrl(current.url, policy.allowedHosts)
            val response = client.newCall(buildRequest(current, policy, uri.host.lowercase())).execute()
            response.use {
                if (it.isRedirect) {
                    redirects++
                    if (redirects > policy.maxRedirects) {
                        throw HttpCapabilityFailure("plugin.http_redirect_budget_exceeded")
                    }
                    val location = it.header("Location")
                        ?: throw HttpCapabilityFailure("plugin.http_redirect_invalid")
                    val resolved = uri.resolve(location).toString()
                    requireAllowedUrl(resolved, policy.allowedHosts)
                    current = PluginHttpRequest(url = resolved)
                } else {
                    val length = it.body.contentLength()
                    if (length > policy.maxCompressedResponseBytes) throw ResponseBudgetExceeded()
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

internal class HttpCapabilityFailure(
    val code: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : IOException(code, cause)
