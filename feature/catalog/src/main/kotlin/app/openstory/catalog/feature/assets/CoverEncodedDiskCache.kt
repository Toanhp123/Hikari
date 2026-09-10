package app.openstory.catalog.feature.assets

import coil3.decode.ImageSource
import coil3.disk.DiskCache
import java.util.concurrent.CancellationException
import okio.Buffer
import okio.Source

internal interface CoverEncodedCache {
    val maxSizeBytes: Long

    fun read(stableCacheKey: String): ImageSource?

    fun commit(stableCacheKey: String, source: Source, declaredLength: Long?): Boolean
}

internal class CoverEncodedDiskCache(
    private val diskCache: DiskCache,
) : CoverEncodedCache {
    override val maxSizeBytes: Long
        get() = diskCache.maxSize

    override fun read(stableCacheKey: String): ImageSource? {
        val snapshot = diskCache.openSnapshot(stableCacheKey) ?: return null
        return ImageSource(
            file = snapshot.data,
            fileSystem = diskCache.fileSystem,
            diskCacheKey = stableCacheKey,
            closeable = snapshot,
        )
    }

    override fun commit(stableCacheKey: String, source: Source, declaredLength: Long?): Boolean =
        if (declaredLength.exceedsEncodedLimit()) {
            false
        } else {
            diskCache.openEditor(stableCacheKey)?.let { editor ->
                commit(editor, source, declaredLength)
            } ?: false
        }

    private fun commit(editor: DiskCache.Editor, source: Source, declaredLength: Long?): Boolean =
        try {
            val copied = copyBounded(source, editor)
            val incomplete = declaredLength
                ?.takeIf { it >= 0 }
                ?.let { expected -> expected != copied } == true
            if (copied == null || copied == 0L || incomplete) {
                editor.abort()
                false
            } else {
                editor.commit()
                true
            }
        } catch (cancellation: CancellationException) {
            runCatching(editor::abort)
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            runCatching(editor::abort)
            false
        }

    private fun copyBounded(source: Source, editor: DiskCache.Editor): Long? {
        var total = 0L
        var overLimit = false
        diskCache.fileSystem.write(editor.data) {
            val buffer = Buffer()
            while (!overLimit) {
                val read = source.read(buffer, COPY_BUFFER_BYTES)
                if (read == -1L) break
                total += read
                if (total > CatalogImageLimits.MAX_ENCODED_BYTES) {
                    overLimit = true
                } else {
                    write(buffer, read)
                }
            }
        }
        return total.takeUnless { overLimit }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 8_192L
    }
}

private fun Long?.exceedsEncodedLimit(): Boolean =
    this != null && this >= 0 && this > CatalogImageLimits.MAX_ENCODED_BYTES
