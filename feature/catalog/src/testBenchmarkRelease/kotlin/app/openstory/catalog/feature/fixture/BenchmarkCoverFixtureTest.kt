package app.openstory.catalog.feature.fixture

import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.feature.assets.CatalogImageLimits
import app.openstory.catalog.feature.assets.CoverImagePreflight
import app.openstory.catalog.feature.assets.RemoteCoverTransportRequest
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
        val request = RemoteCoverTransportRequest(
            uri = RemoteHttpsUriV1.parseAndNormalize("https://covers.hikari.invalid/cover.webp"),
            connectTimeoutMillis = 10_000,
            readTimeoutMillis = 20_000,
            callTimeoutMillis = 20_000,
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
            declaredContentLength = CatalogImageLimits.MAX_ENCODED_BYTES + 1,
            requestCounter = AtomicInteger(),
            assertWorkerThread = {},
        )

        val response = transport.execute(request())

        assertEquals("image/png", response.contentType)
        assertEquals(CatalogImageLimits.MAX_ENCODED_BYTES + 1, response.contentLength)
    }

    @Test
    fun pathologicalPngFixtureTripsTheProductionDimensionBoundBeforeDecode() {
        val file = Files.createTempFile("benchmark-cover-dimensions-", ".png").toFile()
        try {
            file.writeBytes(pathologicalPng(width = 8_193, height = 1))

            val thrown = org.junit.Assert.assertThrows(CatalogFailureException::class.java) {
                CoverImagePreflight().inspect(file, "image/png", Size(360, 540))
            }

            assertEquals(
                CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE,
                (thrown.failure as CatalogFailure.Artwork).reason,
            )
        } finally {
            file.delete()
        }
    }

    private fun request() = RemoteCoverTransportRequest(
        uri = RemoteHttpsUriV1.parseAndNormalize("https://covers.hikari.invalid/cover.webp"),
        connectTimeoutMillis = 10_000,
        readTimeoutMillis = 20_000,
        callTimeoutMillis = 20_000,
    )
}
