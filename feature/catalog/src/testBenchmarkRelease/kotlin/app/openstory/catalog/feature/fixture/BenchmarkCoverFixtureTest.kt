package app.openstory.catalog.feature.fixture

import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.ArtworkLimits
import app.openstory.artwork.remote.ArtworkTransportRequest
import app.openstory.artwork.preflight.ArtworkImagePreflight
import coil3.size.Size
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkCoverFixtureTest {
    @Test
    fun deterministicTransportReturnsStableBytesAndCountsEachRequest() = runTest {
        val bytes = byteArrayOf(1, 3, 5, 7)
        val counter = AtomicInteger()
        var workerThreadAssertions = 0
        val transport = DeterministicBenchmarkCoverTransport(
            encodedBytes = bytes,
            requestCounter = counter,
            assertWorkerThread = { workerThreadAssertions += 1 },
        )
        val request = ArtworkTransportRequest(
            uri = "https://covers.hikari.invalid/cover.webp",
            connectTimeoutMillis = 5_000,
            readTimeoutMillis = 10_000,
            callTimeoutMillis = 15_000,
        )

        val first = transport.execute(request)
        val second = transport.execute(request)

        assertArrayEquals(bytes, first.use { it.body.readBytes() })
        assertArrayEquals(bytes, second.use { it.body.readBytes() })
        assertEquals(2, counter.get())
        assertEquals(2, workerThreadAssertions)
    }

    @Test
    fun pathologicalTransportCanDeclareAnEncodedPayloadBeyondTheProductionCeiling() = runTest {
        val transport = DeterministicBenchmarkCoverTransport(
            encodedBytes = byteArrayOf(1),
            contentType = "image/png",
            declaredContentLength = ArtworkLimits.MAX_ENCODED_BYTES + 1,
            requestCounter = AtomicInteger(),
            assertWorkerThread = {},
        )

        val response = transport.execute(request())

        assertEquals("image/png", response.contentType)
        assertEquals(ArtworkLimits.MAX_ENCODED_BYTES + 1, response.contentLength)
    }

    @Test
    fun pathologicalPngFixtureTripsTheProductionDimensionBoundBeforeDecode() {
        val file = Files.createTempFile("benchmark-cover-dimensions-", ".png").toFile()
        try {
            file.writeBytes(pathologicalPng(width = 8_193, height = 1))

            val thrown = org.junit.Assert.assertThrows(ArtworkFailureException::class.java) {
                ArtworkImagePreflight().inspect(file, "image/png", Size(360, 540))
            }

            assertEquals(
                ArtworkFailureReason.DIMENSIONS_TOO_LARGE,
                thrown.reason,
            )
        } finally {
            file.delete()
        }
    }

    private fun request() = ArtworkTransportRequest(
        uri = "https://covers.hikari.invalid/cover.webp",
        connectTimeoutMillis = 5_000,
        readTimeoutMillis = 10_000,
        callTimeoutMillis = 15_000,
    )
}
