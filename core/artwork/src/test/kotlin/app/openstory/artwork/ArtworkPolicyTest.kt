package app.openstory.artwork

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.CancellationException
import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.common.execution.WorkAdmissionResult
import app.openstory.common.execution.WorkPriority
import app.openstory.common.execution.WorkResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtworkPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun oversizedLocatorFailsBeforeTransport() = runTest {
        val transport = RecordingTransport()

        assertArtworkFailure(ArtworkFailureReason.INVALID_LOCATOR) {
            ArtworkRemotePolicy(resolver(), transport, temporaryFolder.root)
                .fetch(identity(locator = "https://images.example/${"x".repeat(4_097)}"))
        }

        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun artworkPolicyRequiresCanonicalDnsHosts() {
        listOf("images.example.", "127.0.0.1", "bad_host.example").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) {
                ArtworkPolicy(AUTHORITY, setOf(host))
            }
        }
    }

    @Test
    fun artworkPolicySnapshotsCallerOwnedHostSet() {
        val hosts = mutableSetOf("images.example")
        val policy = ArtworkPolicy(AUTHORITY, hosts)

        hosts += "later.example"

        assertEquals(setOf("images.example"), policy.allowedHttpsHosts)
        assertEquals(ArtworkPolicy(AUTHORITY, setOf("images.example")), policy)
    }

    @Test
    fun requestUsesFrozenTimeoutsAndRevalidatesThreeRedirects() = runTest {
        val transport = RecordingTransport(
            responses = ArrayDeque(
                listOf(
                    response(302, redirect = "/two"),
                    response(307, redirect = "https://images.example/three"),
                    response(308, redirect = "/final"),
                    response(200, contentType = "image/webp", body = byteArrayOf(1, 2, 3)),
                ),
            ),
        )
        val policy = ArtworkRemotePolicy(
            policyResolver = resolver(),
            transport = transport,
            temporaryDirectory = temporaryFolder.root,
        )

        val payload = policy.fetch(identity())

        payload.use {
            assertEquals(3L, it.length)
            assertEquals("image/webp", it.mediaType)
        }
        assertEquals(4, transport.requests.size)
        assertEquals(
            listOf(
                "https://images.example/one",
                "https://images.example/two",
                "https://images.example/three",
                "https://images.example/final",
            ),
            transport.requests.map { it.uri },
        )
        transport.requests.forEach { request ->
            assertEquals(5_000L, request.connectTimeoutMillis)
            assertEquals(10_000L, request.readTimeoutMillis)
            assertEquals(15_000L, request.callTimeoutMillis)
        }
        assertTrue(transport.responses.all { it.closeCount == 1 })
    }

    @Test
    fun missingPolicyUnsafeHostAndFourthRedirectFailClosed() = runTest {
        assertArtworkFailure(ArtworkFailureReason.POLICY_REJECTED) {
            ArtworkRemotePolicy(
                policyResolver = ArtworkPolicyResolver { null },
                transport = RecordingTransport(),
                temporaryDirectory = temporaryFolder.root,
            ).fetch(identity())
        }
        assertArtworkFailure(ArtworkFailureReason.POLICY_REJECTED) {
            ArtworkRemotePolicy(
                policyResolver = resolver(),
                transport = RecordingTransport(),
                temporaryDirectory = temporaryFolder.root,
            ).fetch(identity(locator = "https://evil.example/cover"))
        }
        val redirects = RecordingTransport(
            responses = ArrayDeque(
                listOf(
                    response(302, redirect = "/two"),
                    response(302, redirect = "/three"),
                    response(302, redirect = "/four"),
                    response(302, redirect = "/five"),
                ),
            ),
        )
        assertArtworkFailure(ArtworkFailureReason.REDIRECT_REJECTED) {
            ArtworkRemotePolicy(resolver(), redirects, temporaryFolder.root).fetch(identity())
        }
        assertEquals(4, redirects.requests.size)
    }

    @Test
    fun redirectTargetsLoopsAndUnsupportedStatusesFailClosed() = runTest {
        val unsafeResponse = response(302, redirect = "https://evil.example/cover")
        val unsafeRedirect = RecordingTransport(
            ArrayDeque(listOf(unsafeResponse)),
        )
        assertArtworkFailure(ArtworkFailureReason.REDIRECT_REJECTED) {
            ArtworkRemotePolicy(resolver(), unsafeRedirect, temporaryFolder.root).fetch(identity())
        }
        assertEquals(1, unsafeRedirect.requests.size)
        assertEquals(1, unsafeResponse.closeCount)

        val loop = RecordingTransport(
            ArrayDeque(listOf(response(301, redirect = "https://images.example/one"))),
        )
        assertArtworkFailure(ArtworkFailureReason.REDIRECT_REJECTED) {
            ArtworkRemotePolicy(resolver(), loop, temporaryFolder.root).fetch(identity())
        }
        assertEquals(1, loop.requests.size)

        val unsupported = RecordingTransport(
            ArrayDeque(listOf(response(304, redirect = "/not-followed"))),
        )
        assertArtworkFailure(ArtworkFailureReason.REDIRECT_REJECTED) {
            ArtworkRemotePolicy(resolver(), unsupported, temporaryFolder.root).fetch(identity())
        }
        assertEquals(1, unsupported.requests.size)
    }

    @Test
    fun declaredAndStreamingBodyLimitsAreEnforced() = runTest {
        val declared = RecordingTransport(
            ArrayDeque(
                listOf(
                    response(
                        status = 200,
                        contentType = "image/png",
                        contentLength = ArtworkLimits.MAX_ENCODED_BYTES + 1,
                    ),
                ),
            ),
        )
        assertArtworkFailure(ArtworkFailureReason.ENCODED_TOO_LARGE) {
            ArtworkRemotePolicy(resolver(), declared, temporaryFolder.root).fetch(identity())
        }

        val streaming = RecordingTransport(
            ArrayDeque(
                listOf(
                    response(
                        status = 200,
                        contentType = "image/jpeg",
                        body = RepeatedByteInputStream(ArtworkLimits.MAX_ENCODED_BYTES + 1),
                    ),
                ),
            ),
        )
        assertArtworkFailure(ArtworkFailureReason.ENCODED_TOO_LARGE) {
            ArtworkRemotePolicy(resolver(), streaming, temporaryFolder.root).fetch(identity())
        }
        assertTrue(temporaryFolder.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun callerCancellationPropagatesUnchanged() = runTest {
        val cancellation = CancellationException("consumer left")
        val transport = ArtworkTransport { throw cancellation }

        val thrown = try {
            ArtworkRemotePolicy(resolver(), transport, temporaryFolder.root).fetch(identity())
            error("Expected cancellation")
        } catch (thrown: CancellationException) {
            thrown
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun ownedDeadlineMapsToTimeout() = runTest {
        val transport = ArtworkTransport {
            delay(ArtworkLimits.CALL_TIMEOUT_MILLIS + 1)
            response(200, contentType = "image/jpeg", body = byteArrayOf(1))
        }

        assertArtworkFailure(ArtworkFailureReason.TIMEOUT) {
            ArtworkRemotePolicy(resolver(), transport, temporaryFolder.root).fetch(identity())
        }
    }

    @Test
    fun bodyCancellationAndResponseCloseFailureCleanUpPayloads() = runTest {
        val cancellation = CancellationException("body cancelled")
        val cancelled = RecordingTransport(
            ArrayDeque(
                listOf(response(200, contentType = "image/jpeg", body = ThrowingInputStream(cancellation))),
            ),
        )
        val thrown = try {
            ArtworkRemotePolicy(resolver(), cancelled, temporaryFolder.root).fetch(identity())
            error("Expected cancellation")
        } catch (thrown: CancellationException) {
            thrown
        }
        assertSame(cancellation, thrown)
        assertTrue(temporaryFolder.root.listFiles().orEmpty().isEmpty())

        val closeFailure = RecordingTransport(
            ArrayDeque(
                listOf(
                    response(
                        status = 200,
                        contentType = "image/jpeg",
                        body = byteArrayOf(1),
                        closeFailure = IllegalStateException("close failed"),
                    ),
                ),
            ),
        )
        assertArtworkFailure(ArtworkFailureReason.IO_FAILED) {
            ArtworkRemotePolicy(resolver(), closeFailure, temporaryFolder.root).fetch(identity())
        }
        assertTrue(temporaryFolder.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun everyRemoteHopUsesVisibleNetworkAdmission() = runTest {
        val admission = RecordingNetworkAdmission()
        val transport = RecordingTransport(
            ArrayDeque(listOf(response(200, contentType = "image/png", body = byteArrayOf(1)))),
        )

        ArtworkRemotePolicy(resolver(), transport, temporaryFolder.root, admission)
            .fetch(identity()).close()

        assertEquals(listOf(WorkResource.NETWORK), admission.resources)
        assertEquals(listOf(WorkPriority.VISIBLE_ARTWORK), admission.priorities)
    }

    private fun identity(locator: String = "https://images.example/one") = ArtworkRequestIdentity(
        authority = AUTHORITY,
        stableAssetKey = "asset:1",
        locator = locator,
        transformKey = "crop:240x360",
        varyKey = "public",
    )

    private fun resolver() = ArtworkPolicyResolver { authority ->
        ArtworkPolicy(authority, setOf("images.example"))
    }

    private suspend fun assertArtworkFailure(
        expected: ArtworkFailureReason,
        block: suspend () -> Unit,
    ) {
        val failure = try {
            block()
            error("Expected artwork failure")
        } catch (failure: ArtworkFailureException) {
            failure
        }
        assertEquals(expected, failure.reason)
    }

    private companion object {
        val AUTHORITY = ArtworkAuthorityKey("catalog:mangaupdates")
    }
}

private class RecordingNetworkAdmission : ProcessWorkAdmission {
    val resources = mutableListOf<WorkResource>()
    val priorities = mutableListOf<WorkPriority>()

    override suspend fun <T> run(
        resource: WorkResource,
        priority: WorkPriority,
        block: suspend () -> T,
    ): WorkAdmissionResult<T> {
        resources += resource
        priorities += priority
        return WorkAdmissionResult.Completed(block())
    }
}

private class RecordingTransport(
    val responses: ArrayDeque<RecordingResponse> = ArrayDeque(),
) : ArtworkTransport {
    val requests = mutableListOf<ArtworkTransportRequest>()

    override suspend fun execute(request: ArtworkTransportRequest): ArtworkTransportResponse {
        requests += request
        return responses.removeFirst()
    }
}

private class RecordingResponse(
    override val statusCode: Int,
    override val redirectLocation: String?,
    override val contentType: String?,
    override val contentLength: Long?,
    override val body: InputStream,
    private val closeFailure: RuntimeException? = null,
) : ArtworkTransportResponse, Closeable {
    var closeCount = 0

    override fun close() {
        closeCount += 1
        body.close()
        closeFailure?.let { throw it }
    }
}

private fun response(
    status: Int,
    redirect: String? = null,
    contentType: String? = null,
    contentLength: Long? = null,
    body: ByteArray = byteArrayOf(),
    closeFailure: RuntimeException? = null,
) = response(status, redirect, contentType, contentLength, ByteArrayInputStream(body), closeFailure)

private fun response(
    status: Int,
    redirect: String? = null,
    contentType: String? = null,
    contentLength: Long? = null,
    body: InputStream,
    closeFailure: RuntimeException? = null,
) = RecordingResponse(status, redirect, contentType, contentLength, body, closeFailure)

private class RepeatedByteInputStream(private val length: Long) : InputStream() {
    private var read = 0L

    override fun read(): Int = if (read++ < length) 0 else -1

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (read >= this.length) return -1
        val count = minOf(length.toLong(), this.length - read).toInt()
        buffer.fill(0, offset, offset + count)
        read += count
        return count
    }
}

private class ThrowingInputStream(private val failure: RuntimeException) : InputStream() {
    override fun read(): Int = throw failure
}
