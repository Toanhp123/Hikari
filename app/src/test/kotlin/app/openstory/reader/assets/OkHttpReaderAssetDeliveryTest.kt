package app.openstory.reader.assets

import app.openstory.common.id.PluginId
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkHttpReaderAssetDeliveryTest {
    @Test
    fun successfulFetchConsumesAndClosesBodyBeforeReturningWithoutAuthHeaders() = runTest {
        MockWebServer().use { server ->
            val bodyClosed = AtomicBoolean(false)
            val client = bridgedClient(bodyClosed = bodyClosed)
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )

            val result = OkHttpReaderAssetDelivery(client).fetch(
                deliveryRequest(httpsUrl(server, "/page.png")),
            )

            val success = assertIs<ReaderAssetDeliveryResult.Success>(result)
            assertContentEquals(PNG_BYTES, success.payload.bytes())
            assertEquals("image/png", success.payload.mimeType)
            assertTrue(bodyClosed.get())
            val recorded = server.takeRequest()
            assertNull(recorded.getHeader("Authorization"))
            assertNull(recorded.getHeader("Cookie"))
        }
    }

    @Test
    fun inheritedInterceptorsCannotInjectCredentialsIntoReaderDelivery() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(cleartextMockServerBridge())
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer must-not-be-used")
                            .header("Cookie", "session=must-not-be-used")
                            .build(),
                    )
                }
                .build()

            val result = OkHttpReaderAssetDelivery(client).fetch(
                deliveryRequest(httpsUrl(server, "/credential-free.png")),
            )

            assertIs<ReaderAssetDeliveryResult.Success>(result)
            val recorded = server.takeRequest()
            assertNull(recorded.getHeader("Authorization"))
            assertNull(recorded.getHeader("Cookie"))
        }
    }

    @Test
    fun declaredBodyAboveLimitFailsBeforeReadingBody() = runTest {
        MockWebServer().use { server ->
            val bodyRead = AtomicBoolean(false)
            val bodyClosed = AtomicBoolean(false)
            val client = bridgedClient(bodyRead = bodyRead, bodyClosed = bodyClosed)
            server.enqueue(
                MockResponse()
                    .setBody("small-body-that-must-not-be-read")
                    .setHeader("Content-Type", "image/jpeg")
                    .setHeader("Content-Length", ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES + 1L),
            )

            val result = OkHttpReaderAssetDelivery(client).fetch(
                deliveryRequest(httpsUrl(server, "/oversized.jpg")),
            )

            assertEquals(
                ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.AssetTooLarge),
                result,
            )
            assertFalse(bodyRead.get())
            assertTrue(bodyClosed.get())
        }
    }

    @Test
    fun streamedBodyCrossingLimitFailsWhenReportedContentLengthIsIncorrectlySmall() = runTest {
        MockWebServer().use { server ->
            val bytes = ByteArray(ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES + 1) { 0x5A.toByte() }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody(Buffer().write(bytes)),
            )

            val result = OkHttpReaderAssetDelivery(
                bridgedClient(reportedContentLength = 1L),
            ).fetch(
                deliveryRequest(httpsUrl(server, "/lying-length.jpg")),
            )

            assertEquals(
                ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.AssetTooLarge),
                result,
            )
        }
    }

    @Test
    fun chunkedBodyCrossingLimitFailsEvenWithoutContentLength() = runTest {
        MockWebServer().use { server ->
            val bytes = ByteArray(ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES + 1) { 0x5A.toByte() }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setChunkedBody(Buffer().write(bytes), 8 * 1024),
            )

            val result = OkHttpReaderAssetDelivery(bridgedClient()).fetch(
                deliveryRequest(httpsUrl(server, "/chunked.jpg")),
            )

            assertEquals(
                ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.AssetTooLarge),
                result,
            )
        }
    }

    @Test
    fun nonHttpsLocatorIsRejectedBeforeNetworkExecution() = runTest {
        val executed = AtomicBoolean(false)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                executed.set(true)
                chain.proceed(chain.request())
            }
            .build()

        val failure = runCatching {
            OkHttpReaderAssetDelivery(client).fetch(deliveryRequest("http://example.test/page.jpg"))
        }.exceptionOrNull()

        assertIs<IllegalArgumentException>(failure)
        assertFalse(executed.get())
    }

    @Test
    fun obviousHtmlAndJsonBodiesAreRejectedAsInvalidPayload() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><body>blocked</body></html>"),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/octet-stream")
                    .setBody("{\"error\":\"expired\"}"),
            )
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())

            repeat(2) { ordinal ->
                val result = delivery.fetch(deliveryRequest(httpsUrl(server, "/invalid-$ordinal")))
                assertEquals(
                    ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.InvalidPayload),
                    result,
                )
            }
        }
    }

    @Test
    fun deliveryDoesNotHideHttp408BehindOkHttpAutomaticRetry() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(408))
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())

            val result = delivery.fetch(deliveryRequest(httpsUrl(server, "/timeout.png")))

            assertEquals(
                ReaderAssetDeliveryResult.Failure(
                    ReaderAssetFailure.TransportUnavailable(retryable = true),
                ),
                result,
            )
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun deliveryDoesNotHideRetryAfterZero503BehindOkHttpAutomaticFollowUp() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setHeader("Retry-After", "00"),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())

            val result = delivery.fetch(deliveryRequest(httpsUrl(server, "/unavailable.png")))

            assertEquals(
                ReaderAssetDeliveryResult.Failure(
                    ReaderAssetFailure.TransportUnavailable(retryable = true),
                ),
                result,
            )
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun deliverySuppressesArbitrarilyLongNumericZeroRetryAfterWithoutHiddenFollowUp() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setHeader("Retry-After", "0".repeat(64)),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())

            val result = delivery.fetch(deliveryRequest(httpsUrl(server, "/long-zero-retry-after.png")))

            assertEquals(
                ReaderAssetDeliveryResult.Failure(
                    ReaderAssetFailure.TransportUnavailable(retryable = true),
                ),
                result,
            )
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun deliverySuppressesHugeNumericRetryAfterWithoutParserSideEffects() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setHeader("Retry-After", "9".repeat(64)),
            )
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())

            val result = delivery.fetch(deliveryRequest(httpsUrl(server, "/huge-retry-after.png")))

            assertEquals(
                ReaderAssetDeliveryResult.Failure(
                    ReaderAssetFailure.TransportUnavailable(retryable = true),
                ),
                result,
            )
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun retryableStatusesMapToTransportUnavailable() = runTest {
        MockWebServer().use { server ->
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())
            listOf(408, 429, 500, 502, 503).forEach { status ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(
                    ReaderAssetDeliveryResult.Failure(
                        ReaderAssetFailure.TransportUnavailable(retryable = true),
                    ),
                    delivery.fetch(deliveryRequest(httpsUrl(server, "/status-$status"))),
                )
            }
        }
    }

    @Test
    fun redirectIsNotFollowedSoLocatorIdentityCannotChangeBehindTheAdapter() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", server.url("/redirected.png")),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )

            val result = OkHttpReaderAssetDelivery(bridgedClient()).fetch(
                deliveryRequest(httpsUrl(server, "/original.png")),
            )

            assertEquals(
                ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.DeliveryRejected(302)),
                result,
            )
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun inheritedAuthenticatorCannotTurnTerminal401IntoHiddenCredentialRetry() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("WWW-Authenticate", "Basic realm=reader"),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG_BYTES)),
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(cleartextMockServerBridge())
                .authenticator { _, response ->
                    response.request.newBuilder()
                        .header("Authorization", "Bearer must-not-be-used")
                        .build()
                }
                .build()

            val result = OkHttpReaderAssetDelivery(client).fetch(
                deliveryRequest(httpsUrl(server, "/auth-required.png")),
            )

            assertEquals(
                ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.DeliveryRejected(401)),
                result,
            )
            assertEquals(1, server.requestCount)
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun terminalFourHundredsRemainDeliveryRejectedWithoutSemanticGuessing() = runTest {
        MockWebServer().use { server ->
            val delivery = OkHttpReaderAssetDelivery(bridgedClient())
            listOf(400, 401, 403, 404, 409, 422).forEach { status ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(
                    ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.DeliveryRejected(status)),
                    delivery.fetch(deliveryRequest(httpsUrl(server, "/status-$status"))),
                )
            }
        }
    }

    @Test
    fun ioFailureMapsToRetryableTransportUnavailable() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("synthetic transport failure") }
            .build()

        val result = OkHttpReaderAssetDelivery(client).fetch(
            deliveryRequest("https://example.test/page.jpg"),
        )

        assertEquals(
            ReaderAssetDeliveryResult.Failure(
                ReaderAssetFailure.TransportUnavailable(retryable = true),
            ),
            result,
        )
    }

    private fun bridgedClient(
        bodyClosed: AtomicBoolean? = null,
        bodyRead: AtomicBoolean? = null,
        reportedContentLength: Long? = null,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(cleartextMockServerBridge())
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            val trackedSource = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    bodyRead?.set(true)
                    return super.read(sink, byteCount)
                }

                override fun close() {
                    bodyClosed?.set(true)
                    super.close()
                }
            }.buffer()
            response.newBuilder()
                .body(
                    object : ResponseBody() {
                        override fun contentType() = body.contentType()

                        override fun contentLength() = reportedContentLength ?: body.contentLength()

                        override fun source() = trackedSource
                    },
                )
                .build()
        }
        .build()

    private fun cleartextMockServerBridge(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val cleartextUrl = original.url.newBuilder().scheme("http").build()
        chain.proceed(original.newBuilder().url(cleartextUrl).build())
    }

    private fun httpsUrl(server: MockWebServer, path: String): String =
        server.url(path).newBuilder().scheme("https").build().toString()

    private fun deliveryRequest(locator: String) = ReaderAssetDeliveryRequest(
        assetKey = TEST_ASSET_KEY,
        deliveryLocator = locator,
    )

    private companion object {
        val PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        )

        val TEST_ASSET_KEY = ReaderPageAssetKey(
            schemaVersion = ReaderAssetKeySchemaVersion(1),
            sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("test.plugin")),
            securityScope = ReaderCacheSecurityScope.Public,
            contentVariant = ReaderContentVariant.ORIGINAL,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
            imageSetNamespace = ReaderImageSetNamespace("0".repeat(64)),
            runtimeIsolationScope = null,
            pageIdentityHash = ReaderAssetIdentityHash("1".repeat(64)),
            hash = ReaderAssetKeyHash("2".repeat(64)),
        )
    }
}
