package app.openstory.network

import app.openstory.common.AppResult
import app.openstory.common.Clock
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class AllowlistedHttpGatewayTest {
    @Test
    fun redirectToUndeclaredHostIsDenied() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader(
                        "Location",
                        "https://evil.invalid/x",
                    ),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).get(
                url = server.url("/").toString(),
            )

            assertEquals(
                expected = 1,
                actual = server.requestCount,
                message =
                    "The initial allowed host must be requested before the redirect is denied.",
            )
            assertEquals(
                expected = "plugin.domain_denied",
                actual = result.errorCode(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun compressedBodyCeilingIsEnforced() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("x".repeat(128)),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).get(
                url = server.url("/compressed-limit").toString(),
                budget = RequestBudget(
                    maxCompressedBytes = 32,
                    maxDecompressedBytes = 256,
                ),
            )

            assertEquals(
                expected =
                    "network.response_compressed_too_large",
                actual = result.errorCode(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun decompressedBodyCeilingIsEnforced() = runTest {
        val decompressed =
            "bounded-response-".repeat(256)
                .encodeToByteArray()

        val compressed = gzip(decompressed)

        assertTrue(
            compressed.size < decompressed.size,
            "The fixture must actually compress.",
        )

        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader(
                        "Content-Encoding",
                        "gzip",
                    )
                    .setBody(
                        Buffer().write(compressed),
                    ),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).get(
                url =
                    server.url("/decompressed-limit")
                        .toString(),
                budget = RequestBudget(
                    maxCompressedBytes =
                        compressed.size.toLong() + 32L,
                    maxDecompressedBytes = 128,
                ),
            )

            assertEquals(
                expected =
                    "network.response_decompressed_too_large",
                actual = result.errorCode(),
            )
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun cookiesComeOnlyFromExactPluginHostSession() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("ok"),
            )
            server.start()

            val correctCookie =
                Cookie.Builder()
                    .name("session")
                    .value("correct")
                    .hostOnlyDomain(server.hostName)
                    .build()

            val wrongPluginCookie =
                Cookie.Builder()
                    .name("session")
                    .value("wrong-plugin")
                    .hostOnlyDomain(server.hostName)
                    .build()

            val sessionStore =
                TestPluginSessionStore(
                    cookies = mapOf(
                        (
                            "fixture.content" to
                                server.hostName
                        ) to listOf(correctCookie),
                        (
                            "fixture.other" to
                                server.hostName
                        ) to listOf(wrongPluginCookie),
                    ),
                )

            testGateway(
                allowedHosts =
                    setOf(server.hostName),
                pluginId = "fixture.content",
                sessionStore = sessionStore,
            ).get(
                url =
                    server.url("/session")
                        .toString(),
                headers = mapOf(
                    "Cookie" to
                        "plugin-injected=true",
                ),
            )

            val recordedRequest =
                server.takeRequest()

            assertEquals(
                expected = "session=correct",
                actual =
                    recordedRequest
                        .getHeader("Cookie"),
            )
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun logsExcludeQueriesCredentialsCookiesAndBodies() = runTest {
        val server = MockWebServer()
        val logEntries = mutableListOf<String>()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("response-body-secret"),
            )
            server.start()

            val requestUrl = server.url("/chapter")
                .newBuilder()
                .addQueryParameter("token", "query-secret")
                .build()
                .toString()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
                logger = RedactingNetworkLogger(
                    logEntries::add,
                ),
            ).get(
                url = requestUrl,
                headers = mapOf(
                    "Authorization" to "Bearer auth-secret",
                    "Cookie" to "session=cookie-secret",
                ),
            )

            assertTrue(
                result is AppResult.Success,
                "The request fixture must succeed.",
            )

            val combinedLog =
                logEntries.joinToString("\n")

            assertTrue(
                "fixture.content" in combinedLog,
                "Actual log entries: $combinedLog",
            )
            assertTrue(server.hostName in combinedLog)
            assertTrue("/chapter" in combinedLog)
            assertTrue("200" in combinedLog)

            listOf(
                "?token=",
                "query-secret",
                "Authorization",
                "auth-secret",
                "Cookie",
                "cookie-secret",
                "response-body-secret",
            ).forEach { forbiddenValue ->
                assertTrue(
                    forbiddenValue !in combinedLog,
                    "Log leaked forbidden value: $forbiddenValue",
                )
            }
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun cleartextUrlIsDeniedBeforeNetworkCall() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("must-not-be-read"),
            )
            server.start()

            val gateway =
                AllowlistedHttpGateway(
                    client = OkHttpClient.Builder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build(),
                    pluginId = "fixture.content",
                    allowedHosts =
                        setOf(server.hostName),
                    sessionStore =
                        TestPluginSessionStore(),
                    rateLimiter =
                        UnlimitedPluginRequestRateLimiter,
                )

            val result = gateway.get(
                url =
                    server.url("/cleartext")
                        .toString(),
            )

            assertEquals(
                expected = "plugin.https_required",
                actual = result.errorCode(),
            )
            assertEquals(
                expected = 0,
                actual = server.requestCount,
                message =
                    "Cleartext requests must be denied before network access.",
            )
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun tokenBucketIsPartitionedPerPlugin() = runTest {
        val server = MockWebServer()

        try {
            repeat(2) {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("ok"),
                )
            }
            server.start()

            val rateLimiter =
                PerPluginTokenBucket(
                    capacity = 1,
                    refillTokens = 1,
                    refillPeriodMillis = 60_000,
                    clock = Clock { 1_000L },
                )

            val contentGateway = testGateway(
                allowedHosts = setOf(server.hostName),
                pluginId = "fixture.content",
                rateLimiter = rateLimiter,
            )

            val firstResult = contentGateway.get(
                url = server.url("/first").toString(),
            )
            val deniedResult = contentGateway.get(
                url = server.url("/denied").toString(),
            )

            val otherPluginResult = testGateway(
                allowedHosts = setOf(server.hostName),
                pluginId = "fixture.other",
                rateLimiter = rateLimiter,
            ).get(
                url = server.url("/other-plugin").toString(),
            )

            assertTrue(firstResult is AppResult.Success)
            assertEquals(
                expected = "network.rate_limited",
                actual = deniedResult.errorCode(),
            )
            assertTrue(
                otherPluginResult is AppResult.Success,
                "A different plugin must have an independent bucket.",
            )
            assertEquals(
                expected = 2,
                actual = server.requestCount,
                message =
                    "The denied request must not reach the network.",
            )
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun userAgentIsOwnedByHost() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("ok"),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).get(
                url = server.url("/user-agent").toString(),
                headers = mapOf(
                    "User-Agent" to
                        "plugin-controlled-agent",
                ),
            )

            val recordedRequest =
                server.takeRequest()

            assertTrue(result is AppResult.Success)
            assertEquals(
                expected = "OpenStory/1.0",
                actual =
                    recordedRequest
                        .getHeader("User-Agent"),
                message =
                    "Plugins must not override the host-owned User-Agent.",
            )
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun operationTimeBudgetIsEnforced() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(
                        1,
                        TimeUnit.SECONDS,
                    )
                    .setBody("too-late"),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).get(
                url =
                    server.url("/slow")
                        .toString(),
                budget = RequestBudget(
                    maxDurationMillis = 100,
                ),
            )

            assertEquals(
                expected = "network.timeout",
                actual = result.errorCode(),
            )
            assertEquals(
                expected = 1,
                actual = server.requestCount,
            )
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun declaredCharsetOverridesContentTypeCharset() = runTest {
        val server = MockWebServer()

        try {
            val expectedText =
                "Hikari tiếng Việt"

            val encodedBody =
                Buffer().write(
                    expectedText.toByteArray(
                        Charsets.UTF_16LE,
                    ),
                )

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader(
                        "Content-Type",
                        "text/plain; charset=UTF-8",
                    )
                    .setBody(encodedBody),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).execute(
                request = PluginHttpRequest(
                    url =
                        server.url("/declared-charset")
                            .toString(),
                    responseCharset = "UTF-16LE",
                ),
                budget = RequestBudget(),
            )

            val response =
                (
                    result as
                        AppResult.Success<PluginHttpResponse>
                ).value

            assertEquals(
                expected = expectedText,
                actual = response.decodedText,
                message =
                    "Declared response charset must override Content-Type charset.",
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun decodedCharacterCeilingIsEnforced() = runTest {
        val server = MockWebServer()

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader(
                        "Content-Type",
                        "text/plain; charset=UTF-8",
                    )
                    .setBody("four"),
            )
            server.start()

            val result = testGateway(
                allowedHosts = setOf(server.hostName),
            ).execute(
                request = PluginHttpRequest(
                    url =
                        server.url("/text-limit")
                            .toString(),
                ),
                budget = RequestBudget(
                    maxDecodedCharacters = 3,
                ),
            )

            assertEquals(
                expected =
                    "network.response_decoded_text_too_large",
                actual = result.errorCode(),
            )
            assertEquals(
                expected = 1,
                actual = server.requestCount,
            )
        } finally {
            server.shutdown()
        }
    }
    private fun testGateway(
        allowedHosts: Set<String>,
        pluginId: String = "fixture.content",
        sessionStore: PluginSessionStore =
            TestPluginSessionStore(),
        logger: RedactingNetworkLogger =
            RedactingNetworkLogger {},
        rateLimiter: PluginRequestRateLimiter =
            UnlimitedPluginRequestRateLimiter,
    ): AllowlistedHttpGateway =
        AllowlistedHttpGateway.forTesting(
            client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
            pluginId = pluginId,
            allowedHosts = allowedHosts,
            sessionStore = sessionStore,
            logger = logger,
            rateLimiter = rateLimiter,
        )

    private suspend fun AllowlistedHttpGateway.get(
        url: String,
        budget: RequestBudget = RequestBudget(),
        headers: Map<String, String> = emptyMap(),
    ): AppResult<PluginHttpResponse> =
        execute(
            request = PluginHttpRequest(
                url = url,
                headers = headers,
            ),
            budget = budget,
        )

    private fun AppResult<*>.errorCode(): String? =
        when (this) {
            is AppResult.Failure -> error.code
            is AppResult.Success -> null
        }

    private fun gzip(
        input: ByteArray,
    ): ByteArray {
        val output =
            ByteArrayOutputStream()

        GZIPOutputStream(output).use { gzip ->
            gzip.write(input)
        }

        return output.toByteArray()
    }

    private class TestPluginSessionStore(
        private val cookies:
            Map<Pair<String, String>, List<Cookie>> =
                emptyMap(),
    ) : PluginSessionStore {
        override fun load(
            pluginId: String,
            host: String,
        ): List<Cookie> =
            cookies[pluginId to host].orEmpty()

        override fun save(
            pluginId: String,
            host: String,
            cookies: List<Cookie>,
        ) = Unit
    }
}
