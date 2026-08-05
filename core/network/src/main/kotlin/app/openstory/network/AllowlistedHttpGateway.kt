package app.openstory.network

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

class AllowlistedHttpGateway private constructor(
    client: OkHttpClient,
    private val pluginId: String,
    allowedHosts: Set<String>,
    private val sessionStore: PluginSessionStore,
    private val logger: RedactingNetworkLogger,
    private val rateLimiter: PluginRequestRateLimiter,
    private val allowCleartextForTesting: Boolean,
) : PluginHttpGateway {
    constructor(
        client: OkHttpClient,
        pluginId: String,
        allowedHosts: Set<String>,
        sessionStore: PluginSessionStore,
        rateLimiter: PluginRequestRateLimiter,
        logger: RedactingNetworkLogger =
            RedactingNetworkLogger {},
    ) : this(
        client = client,
        pluginId = pluginId,
        allowedHosts = allowedHosts,
        sessionStore = sessionStore,
        logger = logger,
        rateLimiter = rateLimiter,
        allowCleartextForTesting = false,
    )

    companion object {
        internal fun forTesting(
            client: OkHttpClient,
            pluginId: String,
            allowedHosts: Set<String>,
            sessionStore: PluginSessionStore,
            logger: RedactingNetworkLogger =
                RedactingNetworkLogger {},
            rateLimiter: PluginRequestRateLimiter =
                UnlimitedPluginRequestRateLimiter,
        ): AllowlistedHttpGateway =
            AllowlistedHttpGateway(
                client = client,
                pluginId = pluginId,
                allowedHosts = allowedHosts,
                sessionStore = sessionStore,
                logger = logger,
                rateLimiter = rateLimiter,
                allowCleartextForTesting = true,
            )
    }
    private val client =
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addNetworkInterceptor(
                CompressedLimitInterceptor(),
            )
            .build()

    private val normalizedAllowedHosts =
        allowedHosts.map(String::lowercase).toSet()

    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> =
        request.url
            .toHttpUrlOrNull()
            ?.let { initialUrl ->
                when {
                    !isSecure(initialUrl) ->
                        httpsRequired()

                    !isAllowed(initialUrl) ->
                        domainDenied()

                    else ->
                        executeAllowed(
                            request = request,
                            budget = budget,
                            initialUrl = initialUrl,
                        )
                }
            }
            ?: invalidUrl()

    private fun executeAllowed(
        request: PluginHttpRequest,
        budget: RequestBudget,
        initialUrl: HttpUrl,
    ): AppResult<PluginHttpResponse> {
        val deadline =
            OperationDeadline(
                maxDurationMillis =
                    budget.maxDurationMillis,
            )

        var currentUrl = initialUrl
        var requestCount = 0
        var terminalResult:
            AppResult<PluginHttpResponse>? = null

        while (
            requestCount < budget.maxRequests &&
            terminalResult == null
        ) {
            requestCount += 1

            when (
                val step = executeAttempt(
                    request = request,
                    budget = budget,
                    url = currentUrl,
                    deadline = deadline,
                )
            ) {
                is NetworkStep.Completed ->
                    terminalResult = step.result

                is NetworkStep.Redirect ->
                    when {
                        !isSecure(step.url) ->
                            terminalResult =
                                httpsRequired()

                        isAllowed(step.url) ->
                            currentUrl = step.url

                        else ->
                            terminalResult =
                                domainDenied()
                    }
            }
        }

        return terminalResult
            ?: requestBudgetExceeded()
    }

    private fun executeAttempt(
        request: PluginHttpRequest,
        budget: RequestBudget,
        url: HttpUrl,
        deadline: OperationDeadline,
    ): NetworkStep =
        when {
            deadline.isExpired() ->
                NetworkStep.Completed(
                    networkTimeout(),
                )

            rateLimiter.tryAcquire(pluginId) ->
                executeNetworkAttempt(
                    request = request,
                    budget = budget,
                    url = url,
                    deadline = deadline,
                )

            else ->
                NetworkStep.Completed(
                    rateLimited(),
                )
        }

    private fun executeNetworkAttempt(
        request: PluginHttpRequest,
        budget: RequestBudget,
        url: HttpUrl,
        deadline: OperationDeadline,
    ): NetworkStep {
        val remainingMillis =
            deadline.remainingMillis()

        return if (remainingMillis <= 0L) {
            NetworkStep.Completed(
                networkTimeout(),
            )
        } else {
            try {
                val networkRequest =
                    buildRequest(
                        request = request,
                        url = url,
                        budget = budget,
                    )

                logger.logRequest(
                    pluginId = pluginId,
                    url = url,
                )

                val call =
                    client.newCall(networkRequest)

                call.timeout().timeout(
                    remainingMillis,
                    TimeUnit.MILLISECONDS,
                )

                val response = call.execute()

                logger.logResponse(
                    pluginId = pluginId,
                    url = url,
                    status = response.code,
                )

                response.use {
                    responseStep(
                        response = response,
                        currentUrl = url,
                        budget = budget,
                        request = request,
                    )
                }
            } catch (
                _: CompressedBodyTooLargeException,
            ) {
                NetworkStep.Completed(
                    compressedBodyTooLarge(),
                )
            } catch (_: InterruptedIOException) {
                NetworkStep.Completed(
                    networkTimeout(),
                )
            } catch (_: IOException) {
                NetworkStep.Completed(
                    networkFailure(),
                )
            }
        }
    }
    private fun responseStep(
        response: Response,
        currentUrl: HttpUrl,
        budget: RequestBudget,
        request: PluginHttpRequest,
    ): NetworkStep =
        if (response.isRedirect) {
            response
                .header("Location")
                ?.let(currentUrl::resolve)
                ?.let(NetworkStep::Redirect)
                ?: NetworkStep.Completed(
                    invalidRedirect(),
                )
        } else {
            NetworkStep.Completed(
                responseResult(
                    response = response,
                    budget = budget,
                    request = request,
                ),
            )
        }

    private fun buildRequest(
        request: PluginHttpRequest,
        url: HttpUrl,
        budget: RequestBudget,
    ): Request =
        Request.Builder()
            .url(url)
            .tag(
                CompressedByteLimit::class.java,
                CompressedByteLimit(
                    maxBytes =
                        budget.maxCompressedBytes,
                ),
            )
            .apply {
                request.headers.forEach {
                        (name, value) ->
                    if (
                        !isHostOwnedHeader(name)
                    ) {
                        header(name, value)
                    }
                }

                val sessionCookies =
                    sessionStore.load(
                        pluginId = pluginId,
                        host = url.host.lowercase(),
                    )

                if (sessionCookies.isNotEmpty()) {
                    header(
                        COOKIE_HEADER,
                        sessionCookies.joinToString(
                            separator = "; ",
                        ) { cookie ->
                            "${cookie.name}=${cookie.value}"
                        },
                    )
                }

                header(
                    USER_AGENT_HEADER,
                    HOST_USER_AGENT,
                )
            }
            .get()
            .build()

    private fun responseResult(
        response: Response,
        budget: RequestBudget,
        request: PluginHttpRequest,
    ): AppResult<PluginHttpResponse> {
        val contentType =
            response.body.contentType()

        return when (
            val bodyResult =
                readBoundedBody(
                    body = response.body,
                    maxBytes =
                        budget.maxDecompressedBytes,
                )
        ) {
            is AppResult.Success -> {
                val decodedText =
                    decodeResponseText(
                        body = bodyResult.value,
                        declaredCharset =
                            request.responseCharset,
                        contentType = contentType,
                    )

                if (
                    decodedText != null &&
                    decodedText.length >
                    budget.maxDecodedCharacters
                ) {
                    networkError(
                        code =
                            "network.response_decoded_text_too_large",
                        retryable = false,
                    )
                } else {
                    AppResult.Success(
                        PluginHttpResponse(
                            status = response.code,
                            headers =
                                response.headers.toMap(),
                            body = bodyResult.value,
                            decodedText = decodedText,
                        ),
                    )
                }
            }

            is AppResult.Failure ->
                bodyResult
        }
    }
    private fun readBoundedBody(
        body: ResponseBody,
        maxBytes: Long,
    ): AppResult<ByteArray> =
        try {
            body.byteStream().use { input ->
                val output =
                    ByteArrayOutputStream()

                val buffer =
                    ByteArray(DEFAULT_BUFFER_SIZE)

                var totalBytes = 0L

                while (true) {
                    val read = input.read(buffer)

                    if (read == -1) {
                        break
                    }

                    totalBytes += read

                    if (totalBytes > maxBytes) {
                        return decompressedBodyTooLarge()
                    }

                    output.write(
                        buffer,
                        0,
                        read,
                    )
                }

                AppResult.Success(
                    output.toByteArray(),
                )
            }
        } catch (
            _: CompressedBodyTooLargeException,
        ) {
            compressedBodyTooLarge()
        } catch (_: IOException) {
            networkFailure()
        }

    private fun isSecure(
        url: HttpUrl,
    ): Boolean =
        url.isHttps || allowCleartextForTesting

    private fun isAllowed(
        url: HttpUrl,
    ): Boolean =
        url.host.lowercase() in
            normalizedAllowedHosts
}

private class OperationDeadline(
    private val maxDurationMillis: Long,
) {
    private val startedAtNanos =
        System.nanoTime()

    fun isExpired(): Boolean =
        remainingMillis() <= 0L

    fun remainingMillis(): Long {
        val elapsedNanos =
            (
                System.nanoTime() -
                    startedAtNanos
            ).coerceAtLeast(0L)

        val elapsedMillis =
            TimeUnit.NANOSECONDS.toMillis(
                elapsedNanos,
            )

        return (
            maxDurationMillis -
                elapsedMillis
        ).coerceAtLeast(0L)
    }
}
private sealed interface NetworkStep {
    data class Redirect(
        val url: HttpUrl,
    ) : NetworkStep

    data class Completed(
        val result:
            AppResult<PluginHttpResponse>,
    ) : NetworkStep
}

private data class CompressedByteLimit(
    val maxBytes: Long,
)

private class CompressedLimitInterceptor :
    Interceptor {
    override fun intercept(
        chain: Interceptor.Chain,
    ): Response {
        val response =
            chain.proceed(chain.request())

        val limit =
            chain.request()
                .tag(
                    CompressedByteLimit::class.java,
                )
                ?.maxBytes
                ?: return response

        val body = response.body

        if (body.contentLength() > limit) {
            response.close()
            throw CompressedBodyTooLargeException()
        }

        return response.newBuilder()
            .body(
                CompressedLimitResponseBody(
                    delegate = body,
                    maxBytes = limit,
                ),
            )
            .build()
    }
}

private class CompressedLimitResponseBody(
    private val delegate: ResponseBody,
    private val maxBytes: Long,
) : ResponseBody() {
    private val limitedSource: BufferedSource by lazy {
        object :
            ForwardingSource(delegate.source()) {
            private var totalBytes = 0L

            override fun read(
                sink: Buffer,
                byteCount: Long,
            ): Long {
                val read =
                    super.read(
                        sink,
                        byteCount,
                    )

                if (read > 0) {
                    totalBytes += read

                    if (totalBytes > maxBytes) {
                        throw CompressedBodyTooLargeException()
                    }
                }

                return read
            }
        }.buffer()
    }

    override fun contentType(): MediaType? =
        delegate.contentType()

    override fun contentLength(): Long =
        delegate.contentLength()

    override fun source(): BufferedSource =
        limitedSource
}

private class CompressedBodyTooLargeException :
    IOException(
        "Compressed response exceeds its byte budget.",
    )

private fun domainDenied(): AppResult.Failure =
    networkError(
        code = "plugin.domain_denied",
        retryable = false,
    )

private fun invalidUrl(): AppResult.Failure =
    networkError(
        code = "plugin.invalid_url",
        retryable = false,
    )

private fun httpsRequired(): AppResult.Failure =
    networkError(
        code = "plugin.https_required",
        retryable = false,
    )

private fun invalidRedirect(): AppResult.Failure =
    networkError(
        code = "plugin.invalid_redirect",
        retryable = false,
    )

private fun networkFailure(): AppResult.Failure =
    networkError(
        code = "network.request_failed",
        retryable = true,
    )

private fun networkTimeout(): AppResult.Failure =
    networkError(
        code = "network.timeout",
        retryable = true,
    )

private fun rateLimited(): AppResult.Failure =
    networkError(
        code = "network.rate_limited",
        retryable = true,
    )

private fun requestBudgetExceeded():
    AppResult.Failure =
    networkError(
        code =
            "network.request_budget_exceeded",
        retryable = false,
    )

private fun compressedBodyTooLarge():
    AppResult.Failure =
    networkError(
        code =
            "network.response_compressed_too_large",
        retryable = false,
    )

private fun decompressedBodyTooLarge():
    AppResult.Failure =
    networkError(
        code =
            "network.response_decompressed_too_large",
        retryable = false,
    )

private fun networkError(
    code: String,
    retryable: Boolean,
): AppResult.Failure =
    AppResult.Failure(
        AppError.Network(
            code = code,
            retryable = retryable,
        ),
    )
