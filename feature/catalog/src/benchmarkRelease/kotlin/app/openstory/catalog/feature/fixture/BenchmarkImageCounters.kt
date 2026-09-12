package app.openstory.catalog.feature.fixture

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal object BenchmarkImageCounters {
    private val decodedMemoryBytes = AtomicLong()
    private val peakDecodedMemoryBytes = AtomicLong()
    private val encodedDiskBytes = AtomicLong()
    private val peakEncodedDiskBytes = AtomicLong()
    private val successfulDecodes = AtomicInteger()
    private val maxDecodeTargetWidth = AtomicInteger()
    private val maxDecodeTargetHeight = AtomicInteger()
    private val originalSizeDecodes = AtomicInteger()
    private val mainThreadDecodes = AtomicInteger()

    fun reset() {
        decodedMemoryBytes.set(0)
        peakDecodedMemoryBytes.set(0)
        encodedDiskBytes.set(0)
        peakEncodedDiskBytes.set(0)
        successfulDecodes.set(0)
        maxDecodeTargetWidth.set(0)
        maxDecodeTargetHeight.set(0)
        originalSizeDecodes.set(0)
        mainThreadDecodes.set(0)
    }

    fun cacheSnapshot() = BenchmarkCacheDiagnosticSnapshot(
        decodedMemoryBytes.get(),
        peakDecodedMemoryBytes.get(),
        encodedDiskBytes.get(),
        peakEncodedDiskBytes.get(),
    )

    fun decodeSnapshot() = BenchmarkDecodeDiagnosticSnapshot(
        successfulDecodes.get(),
        maxDecodeTargetWidth.get(),
        maxDecodeTargetHeight.get(),
        originalSizeDecodes.get(),
        mainThreadDecodes.get(),
    )

    fun recordImageOwnership(decodedBytes: Long, encodedBytes: Long) {
        decodedMemoryBytes.set(decodedBytes)
        encodedDiskBytes.set(encodedBytes)
        peakDecodedMemoryBytes.updateAndGet { previous -> maxOf(previous, decodedBytes) }
        peakEncodedDiskBytes.updateAndGet { previous -> maxOf(previous, encodedBytes) }
    }

    fun recordSuccessfulDecode(
        targetWidth: Int?,
        targetHeight: Int?,
        originalSize: Boolean,
        mainThread: Boolean,
    ) {
        successfulDecodes.incrementAndGet()
        targetWidth?.let { width -> maxDecodeTargetWidth.updateAndGet { previous -> maxOf(previous, width) } }
        targetHeight?.let { height -> maxDecodeTargetHeight.updateAndGet { previous -> maxOf(previous, height) } }
        if (originalSize || targetWidth == null || targetHeight == null) originalSizeDecodes.incrementAndGet()
        if (mainThread) mainThreadDecodes.incrementAndGet()
    }
}
