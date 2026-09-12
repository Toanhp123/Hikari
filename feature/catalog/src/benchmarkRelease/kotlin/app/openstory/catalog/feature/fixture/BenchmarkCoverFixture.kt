package app.openstory.catalog.feature.fixture

import android.content.Context
import android.os.Looper
import app.openstory.catalog.feature.R
import app.openstory.catalog.feature.assets.CatalogImageLimits
import app.openstory.catalog.feature.assets.RemoteCoverTransport
import app.openstory.catalog.feature.assets.RemoteCoverTransportRequest
import app.openstory.catalog.feature.assets.RemoteCoverTransportResponse
import app.openstory.catalog.feature.assets.RemoteCoverLimits
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

public object BenchmarkCoverFixture {
    private val requestCounter = AtomicInteger()

    @JvmStatic
    public fun resetTransportRequests() {
        requestCounter.set(0)
    }

    @JvmStatic
    public fun transportRequestCount(): Int = requestCounter.get()

    internal fun transport(context: Context): RemoteCoverTransport = DeterministicBenchmarkCoverTransport(
        encodedBytes = context.resources.openRawResource(R.drawable.catalog_benchmark_manga_a).use { it.readBytes() },
        requestCounter = requestCounter,
        assertWorkerThread = ::assertNotMainThread,
    )

    internal fun pathologicalTransports(): List<RemoteCoverTransport> = listOf(
        DeterministicBenchmarkCoverTransport(
            encodedBytes = byteArrayOf(0),
            contentType = "image/png",
            declaredContentLength = CatalogImageLimits.MAX_ENCODED_BYTES + 1,
            requestCounter = requestCounter,
            assertWorkerThread = ::assertNotMainThread,
        ),
        DeterministicBenchmarkCoverTransport(
            encodedBytes = pathologicalPng(RemoteCoverLimits.MAX_SOURCE_DIMENSION + 1, 1),
            contentType = "image/png",
            requestCounter = requestCounter,
            assertWorkerThread = ::assertNotMainThread,
        ),
    )
}

internal class DeterministicBenchmarkCoverTransport(
    private val encodedBytes: ByteArray,
    private val contentType: String = "image/webp",
    private val declaredContentLength: Long = encodedBytes.size.toLong(),
    private val requestCounter: AtomicInteger,
    private val assertWorkerThread: () -> Unit,
) : RemoteCoverTransport {
    override suspend fun execute(request: RemoteCoverTransportRequest): RemoteCoverTransportResponse {
        assertWorkerThread()
        requestCounter.incrementAndGet()
        return BenchmarkCoverResponse(encodedBytes, contentType, declaredContentLength)
    }
}

internal fun pathologicalPng(width: Int, height: Int): ByteArray {
    require(width > 0 && height > 0)
    return ByteBuffer.allocate(PNG_DIMENSIONS_END_OFFSET)
        .put(PNG_SIGNATURE)
        .putInt(PNG_IHDR_DATA_BYTES)
        .put(PNG_IHDR)
        .putInt(width)
        .putInt(height)
        .array()
}

private fun assertNotMainThread() {
    val mainLooper = runCatching(Looper::getMainLooper).getOrNull() ?: return
    check(Looper.myLooper() != mainLooper) {
        "Benchmark cover transport executed on the main thread."
    }
}

private class BenchmarkCoverResponse(
    private val encodedBytes: ByteArray,
    override val contentType: String,
    override val contentLength: Long,
) : RemoteCoverTransportResponse {
    override val statusCode: Int = 200
    override val redirectLocation: String? = null
    override val body: InputStream = ByteArrayInputStream(encodedBytes)

    override fun close() {
        body.close()
    }
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
)
private val PNG_IHDR = "IHDR".encodeToByteArray()
private const val PNG_IHDR_DATA_BYTES = 13
private const val PNG_DIMENSIONS_END_OFFSET = 24
