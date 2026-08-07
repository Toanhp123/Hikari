package app.openstory.network

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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

    private val urlPolicy =
        if (allowCleartextForTesting) {
            PluginUrlPolicy.forTesting(allowedHosts)
        } else {
            PluginUrlPolicy(allowedHosts)
        }

    private val responseReader = BoundedResponseReader()

    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> =
        when (val validated = urlPolicy.resolve(request.url)) {
            is AppResult.Failure -> validated
            is AppResult.Success -> executeAllowed(
                request = request,
                budget = budget,
                initialUrl = validated.value.value.toHttpUrl(),
            )
        }

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
                    when (val validated = urlPolicy.resolve(step.url.toString())) {
                        is AppResult.Failure -> terminalResult = validated
                        is AppResult.Success -> {
                            currentUrl = validated.value.value.toHttpUrl()
                        }
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
                responseReader.read(response, budget)
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
