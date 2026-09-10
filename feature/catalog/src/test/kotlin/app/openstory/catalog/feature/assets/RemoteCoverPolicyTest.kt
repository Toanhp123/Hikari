package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCoverPolicyTest {
    @Test
    fun unsafeInitialLocatorTextMapsToInvalidLocator() = runTest {
        val invalidLocators = listOf(
            "http://images.example.com/cover.jpg",
            "file:///tmp/cover.jpg",
            "content://plugin/cover.jpg",
            "https://user@images.example.com/cover.jpg",
            "https://images.example.com/cover.jpg#fragment",
            "https://images.example.com/${"x".repeat(4_097)}",
        )

        invalidLocators.forEach { raw ->
            val transport = RecordingRemoteCoverTransport {
                response(contentType = "image/jpeg", body = PAYLOAD)
            }
            val failure = assertArtworkFailure {
                policy(transport).fetch(SOURCE_KEY, raw)
            }

            assertEquals(CatalogArtworkFailureReason.INVALID_LOCATOR, failure.reason)
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test
    fun requestPreservesUriIdentityAndUsesFrozenTimeouts() = runTest {
        val raw = "https://images.example.com/a/../cover.webp?sig=A%2f&order=2"
        val transport = RecordingRemoteCoverTransport {
            response(contentType = " Image/WebP ; charset=binary", body = PAYLOAD)
        }
        val policy = policy(transport)
        val locator = locator(raw)

        val payload = policy.fetch(locator)

        payload.use {
            assertArrayEquals(PAYLOAD, it.file.readBytes())
            assertEquals("image/webp", it.mediaType)
            assertEquals(PAYLOAD.size.toLong(), it.length)
        }
        assertFalse(payload.file.exists())
        assertEquals(CoverRevisionV1.remoteUri(locator.normalizedUri), locator.revision)
        assertEquals(listOf(locator.normalizedUri), transport.requests.map { it.uri })
        assertEquals(
            listOf(
                RemoteCoverTransportRequest(
                    uri = locator.normalizedUri,
                    connectTimeoutMillis = 10_000,
                    readTimeoutMillis = 20_000,
                    callTimeoutMillis = 20_000,
                ),
            ),
            transport.requests,
        )
        assertAllResponsesClosedOnce(transport)
    }

    @Test
    fun admittedRedirectCodesResolveRelativeAndAbsoluteTargets() = runTest {
        ADMITTED_REDIRECT_CODES.forEach { status ->
            val start = RemoteHttpsUriV1.parseAndNormalize("https://images.example.com/a/start.jpg")
            val middle = RemoteHttpsUriV1.parseAndNormalize("https://images.example.com/next.png?x=A%2F")
            val finish = RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/final.webp")
            val transport = RecordingRemoteCoverTransport { request ->
                when (request.uri) {
                    start -> response(statusCode = status, redirectLocation = "../next.png?x=A%2F")
                    middle -> response(statusCode = status, redirectLocation = finish.value)
                    finish -> response(contentType = "image/webp", body = PAYLOAD)
                    else -> error("Unexpected request ${request.uri.value}")
                }
            }

            policy(transport, allowedHosts = setOf("images.example.com", "cdn.example.com"))
                .fetch(locator(start))
                .close()

            assertEquals(listOf(start, middle, finish), transport.requests.map { it.uri })
            assertAllResponsesClosedOnce(transport)
        }
    }

    @Test
    fun everyRedirectIsRevalidatedBeforeTheNextRequest() = runTest {
        val start = RemoteHttpsUriV1.parseAndNormalize("https://images.example.com/start.jpg")
        val transport = RecordingRemoteCoverTransport {
            response(statusCode = 302, redirectLocation = "https://undeclared.example/cover.jpg")
        }

        val failure = assertArtworkFailure {
            policy(transport).fetch(locator(start))
        }

        assertEquals(CatalogArtworkFailureReason.REDIRECT_REJECTED, failure.reason)
        assertEquals(listOf(start), transport.requests.map { it.uri })
        assertAllResponsesClosedOnce(transport)
    }

    @Test
    fun redirectLoopsUnsupported3xxAndMaxHopExhaustionFailClosed() = runTest {
        val start = RemoteHttpsUriV1.parseAndNormalize("https://images.example.com/0.jpg")
        val loopTransport = RecordingRemoteCoverTransport {
            response(statusCode = 301, redirectLocation = start.value)
        }
        assertEquals(
            CatalogArtworkFailureReason.REDIRECT_REJECTED,
            assertArtworkFailure { policy(loopTransport).fetch(locator(start)) }.reason,
        )
        assertEquals(1, loopTransport.requests.size)
        assertAllResponsesClosedOnce(loopTransport)

        val unsupported = RecordingRemoteCoverTransport {
            response(statusCode = 304, redirectLocation = "/not-followed.jpg")
        }
        assertEquals(
            CatalogArtworkFailureReason.REDIRECT_REJECTED,
            assertArtworkFailure { policy(unsupported).fetch(locator(start)) }.reason,
        )
        assertEquals(1, unsupported.requests.size)
        assertAllResponsesClosedOnce(unsupported)

        val exhausted = RecordingRemoteCoverTransport { request ->
            val index = request.uri.value.substringAfterLast('/').substringBefore('.').toInt()
            response(statusCode = 302, redirectLocation = "/${index + 1}.jpg")
        }
        assertEquals(
            CatalogArtworkFailureReason.REDIRECT_REJECTED,
            assertArtworkFailure { policy(exhausted).fetch(locator(start)) }.reason,
        )
        assertEquals(6, exhausted.requests.size)
        assertAllResponsesClosedOnce(exhausted)
    }

    @Test
    fun mediaTypeIsCaseInsensitiveButOnlyJpegPngAndWebpAreAdmitted() = runTest {
        listOf("image/jpeg", "IMAGE/PNG; charset=binary", " Image/WebP ").forEach { mediaType ->
            val transport = RecordingRemoteCoverTransport {
                response(contentType = mediaType, body = PAYLOAD)
            }
            policy(transport).fetch(locator()).close()
            assertAllResponsesClosedOnce(transport)
        }

        listOf(null, "text/html", "image/gif", "image/avif").forEach { mediaType ->
            val transport = RecordingRemoteCoverTransport {
                response(contentType = mediaType, body = PAYLOAD)
            }
            val failure = assertArtworkFailure { policy(transport).fetch(locator()) }
            assertEquals(CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED, failure.reason)
            assertEquals(0, transport.responses.single().bodyReadCount.get())
            assertAllResponsesClosedOnce(transport)
        }
    }

    @Test
    fun declaredAndStreamingEncodedLimitsAreEnforcedWithoutLeavingPartialFiles() = runTest {
        val declaredTooLarge = RecordingRemoteCoverTransport {
            response(
                contentType = "image/jpeg",
                contentLength = CatalogImageLimits.MAX_ENCODED_BYTES + 1,
                body = PAYLOAD,
            )
        }
        val declaredFailure = assertArtworkFailure {
            policy(declaredTooLarge).fetch(locator())
        }
        assertEquals(CatalogArtworkFailureReason.ENCODED_TOO_LARGE, declaredFailure.reason)
        assertEquals(0, declaredTooLarge.responses.single().bodyReadCount.get())
        assertAllResponsesClosedOnce(declaredTooLarge)

        listOf(null, -1L, 1L).forEach { declaredLength ->
            val transport = RecordingRemoteCoverTransport {
                response(
                    contentType = "image/png",
                    contentLength = declaredLength,
                    input = RepeatingInputStream(CatalogImageLimits.MAX_ENCODED_BYTES + 1),
                )
            }
            val tempDirectory = createTempDirectory("remote-cover-limit-").toFile()
            try {
                val failure = assertArtworkFailure {
                    policy(transport, temporaryDirectory = tempDirectory).fetch(locator())
                }
                assertEquals(CatalogArtworkFailureReason.ENCODED_TOO_LARGE, failure.reason)
                assertTrue(tempDirectory.listFiles().orEmpty().isEmpty())
                assertAllResponsesClosedOnce(transport)
            } finally {
                tempDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun ownedDeadlineMapsToTimeoutWhileCallerCancellationPropagatesUnchanged() = runTest {
        val timedOutTransport = RecordingRemoteCoverTransport {
            delay(20_001)
            response(contentType = "image/jpeg", body = PAYLOAD)
        }

        val timeout = assertArtworkFailure {
            policy(timedOutTransport).fetch(locator())
        }

        assertEquals(CatalogArtworkFailureReason.TIMEOUT, timeout.reason)
        assertTrue(timedOutTransport.responses.isEmpty())

        val cancellation = CancellationException("session cancelled")
        val cancelledTransport = RecordingRemoteCoverTransport { throw cancellation }
        val thrown = try {
            policy(cancelledTransport).fetch(locator())
            throw AssertionError("Expected caller cancellation")
        } catch (error: CancellationException) {
            error
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun responseAndPartialFileCloseOnBodyCancellationAndIoFailure() = runTest {
        listOf(
            CancellationException("cancel body"),
            IllegalStateException("broken body"),
        ).forEach { bodyFailure ->
            val transport = RecordingRemoteCoverTransport {
                response(
                    contentType = "image/jpeg",
                    input = ThrowingInputStream(bodyFailure),
                )
            }
            val tempDirectory = createTempDirectory("remote-cover-cleanup-").toFile()
            try {
                if (bodyFailure is CancellationException) {
                    val thrown = try {
                        policy(transport, temporaryDirectory = tempDirectory).fetch(locator())
                        throw AssertionError("Expected cancellation")
                    } catch (error: CancellationException) {
                        error
                    }
                    assertSame(bodyFailure, thrown)
                } else {
                    val failure = assertArtworkFailure {
                        policy(transport, temporaryDirectory = tempDirectory).fetch(locator())
                    }
                    assertEquals(CatalogArtworkFailureReason.IO_FAILED, failure.reason)
                }
                assertTrue(tempDirectory.listFiles().orEmpty().isEmpty())
                assertAllResponsesClosedOnce(transport)
            } finally {
                tempDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun responseCloseFailureDeletesCompletedPayloadAndMapsIoFailure() = runTest {
        val tempDirectory = createTempDirectory("remote-cover-close-failure-").toFile()
        val transport = RecordingRemoteCoverTransport {
            response(
                contentType = "image/jpeg",
                body = PAYLOAD,
                closeFailure = IllegalStateException("close failed"),
            )
        }
        try {
            val failure = assertArtworkFailure {
                policy(transport, temporaryDirectory = tempDirectory).fetch(locator())
            }

            assertEquals(CatalogArtworkFailureReason.IO_FAILED, failure.reason)
            assertTrue(tempDirectory.listFiles().orEmpty().isEmpty())
            assertAllResponsesClosedOnce(transport)
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    @Test
    fun persistedLocatorRecoversPolicyFromCurrentHostBindingAndMissingPolicyFailsClosed() = runTest {
        val persistedSource = CatalogSourceKey("persisted.source")
        val persistedUri = "https://images.example.com/covers/stable.jpg?token=A%2F"
        val restoredUri = RemoteHttpsUriV1.parseAndNormalize(persistedUri)
        val restoredLocator = CoverLocator.RemoteHttps(
            catalogSourceKey = persistedSource,
            normalizedUri = restoredUri,
            revision = CoverRevisionV1.remoteUri(restoredUri),
        )
        val transport = RecordingRemoteCoverTransport {
            response(contentType = "image/jpeg", body = PAYLOAD)
        }
        val restoredProvider = SourceAssetPolicyProvider { sourceKey ->
            SourceAssetPolicy(persistedSource, setOf("images.example.com"))
                .takeIf { sourceKey == persistedSource }
        }

        RemoteCoverPolicy(
            policyProvider = { restoredProvider },
            transport = transport,
            temporaryDirectory = createTempDirectory("remote-cover-recreated-").toFile(),
        ).fetch(restoredLocator).close()

        assertEquals(listOf(restoredUri), transport.requests.map { it.uri })
        val missingPolicyTransport = RecordingRemoteCoverTransport {
            response(contentType = "image/jpeg", body = PAYLOAD)
        }
        val failure = assertArtworkFailure {
            RemoteCoverPolicy(
                policyProvider = { SourceAssetPolicyProvider { null } },
                transport = missingPolicyTransport,
                temporaryDirectory = createTempDirectory("remote-cover-missing-policy-").toFile(),
            ).fetch(restoredLocator)
        }
        assertEquals(CatalogArtworkFailureReason.POLICY_REJECTED, failure.reason)
        assertTrue(missingPolicyTransport.requests.isEmpty())

        val noTransportFailure = assertArtworkFailure {
            RemoteCoverPolicy(
                policyProvider = { SourceAssetPolicyProvider { null } },
                transport = null,
                temporaryDirectory = createTempDirectory("remote-cover-no-transport-").toFile(),
            ).fetch(restoredLocator)
        }
        assertEquals(CatalogArtworkFailureReason.POLICY_REJECTED, noTransportFailure.reason)
    }

    private fun policy(
        transport: RemoteCoverTransport,
        allowedHosts: Set<String> = setOf("images.example.com"),
        temporaryDirectory: File = createTempDirectory("remote-cover-policy-").toFile(),
    ): RemoteCoverPolicy {
        val policy = SourceAssetPolicy(SOURCE_KEY, allowedHosts)
        return RemoteCoverPolicy(
            policyProvider = { SourceAssetPolicyProvider { key -> policy.takeIf { key == SOURCE_KEY } } },
            transport = transport,
            temporaryDirectory = temporaryDirectory,
        )
    }

    private fun locator(raw: String = "https://images.example.com/cover.jpg"): CoverLocator.RemoteHttps =
        locator(RemoteHttpsUriV1.parseAndNormalize(raw))

    private fun locator(uri: RemoteHttpsUriV1): CoverLocator.RemoteHttps = CoverLocator.RemoteHttps(
        catalogSourceKey = SOURCE_KEY,
        normalizedUri = uri,
        revision = CoverRevisionV1.remoteUri(uri),
    )

    private fun assertAllResponsesClosedOnce(transport: RecordingRemoteCoverTransport) {
        transport.responses.forEach { response ->
            assertEquals(1, response.closeCount.get())
            assertEquals(1, response.bodyCloseCount.get())
        }
    }

    private suspend fun assertArtworkFailure(block: suspend () -> Any?): CatalogFailure.Artwork {
        val thrown = try {
            block()
            throw AssertionError("Expected CatalogFailureException")
        } catch (failure: CatalogFailureException) {
            failure
        }
        return thrown.failure as? CatalogFailure.Artwork
            ?: throw AssertionError("Expected artwork failure, got ${thrown.failure}")
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("persisted.source")
        val PAYLOAD = "encoded-cover".encodeToByteArray()
        val ADMITTED_REDIRECT_CODES = listOf(301, 302, 303, 307, 308)
    }
}

private class RecordingRemoteCoverTransport(
    private val respond: suspend (RemoteCoverTransportRequest) -> RecordingRemoteCoverResponse,
) : RemoteCoverTransport {
    val requests = mutableListOf<RemoteCoverTransportRequest>()
    val responses = mutableListOf<RecordingRemoteCoverResponse>()

    override suspend fun execute(request: RemoteCoverTransportRequest): RemoteCoverTransportResponse {
        requests += request
        return respond(request).also(responses::add)
    }
}

private class RecordingRemoteCoverResponse(
    override val statusCode: Int,
    override val redirectLocation: String?,
    override val contentType: String?,
    override val contentLength: Long?,
    input: InputStream,
    private val closeFailure: RuntimeException?,
) : RemoteCoverTransportResponse {
    val closeCount = AtomicInteger()
    val bodyCloseCount = AtomicInteger()
    val bodyReadCount = AtomicInteger()
    override val body: InputStream = object : InputStream() {
        override fun read(): Int {
            bodyReadCount.incrementAndGet()
            return input.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            bodyReadCount.incrementAndGet()
            return input.read(bytes, offset, length)
        }

        override fun close() {
            if (bodyCloseCount.incrementAndGet() == 1) input.close()
        }
    }

    override fun close() {
        check(closeCount.incrementAndGet() == 1)
        body.close()
        closeFailure?.let { throw it }
    }
}

private fun response(
    statusCode: Int = 200,
    redirectLocation: String? = null,
    contentType: String? = null,
    contentLength: Long? = null,
    body: ByteArray? = null,
    input: InputStream = ByteArrayInputStream(body ?: ByteArray(0)),
    closeFailure: RuntimeException? = null,
) = RecordingRemoteCoverResponse(
    statusCode = statusCode,
    redirectLocation = redirectLocation,
    contentType = contentType,
    contentLength = contentLength ?: body?.size?.toLong(),
    input = input,
    closeFailure = closeFailure,
)

private class RepeatingInputStream(private val length: Long) : InputStream() {
    private var read = 0L

    override fun read(): Int = if (read++ < length) 0 else -1

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (read >= this.length) return -1
        val count = minOf(length.toLong(), this.length - read).toInt()
        bytes.fill(0, offset, offset + count)
        read += count
        return count
    }
}

private class ThrowingInputStream(private val failure: RuntimeException) : InputStream() {
    override fun read(): Int = throw failure
}
