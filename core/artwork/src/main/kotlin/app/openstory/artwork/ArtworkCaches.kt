package app.openstory.artwork

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import okio.Buffer
import okio.Source

object ArtworkLimits {
    const val DECODED_MEMORY_BYTES = 32L * 1024 * 1024
    const val ENCODED_DISK_BYTES = 128L * 1024 * 1024
    const val MAX_ENCODED_BYTES = 8L * 1024 * 1024
    const val MANUAL_OFFSCREEN_PREFETCH = 0
    const val MAX_REDIRECTS = 3
    const val CONNECT_TIMEOUT_MILLIS = 5_000L
    const val READ_TIMEOUT_MILLIS = 10_000L
    const val CALL_TIMEOUT_MILLIS = 15_000L
    const val MAX_SOURCE_DIMENSION = 8_192
    const val MAX_SOURCE_PIXELS = 32_000_000L
    const val MAX_LOCATOR_CHARS = 4_096
}

interface ArtworkEncodedCache {
    val maxSizeBytes: Long

    fun read(stableCacheKey: String): ImageSource?

    fun commit(stableCacheKey: String, source: Source, declaredLength: Long?): Boolean
}

class ArtworkEncodedDiskCache(
    private val diskCache: DiskCache,
) : ArtworkEncodedCache {
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
                if (total > ArtworkLimits.MAX_ENCODED_BYTES) {
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

interface ArtworkDecodedMemoryCache {
    val coilMemoryCache: MemoryCache
    val maxSizeBytes: Long

    fun clear()
}

internal class CoilArtworkDecodedMemoryCache(
    override val coilMemoryCache: MemoryCache,
) : ArtworkDecodedMemoryCache {
    override val maxSizeBytes: Long
        get() = coilMemoryCache.maxSize

    override fun clear() = coilMemoryCache.clear()
}

interface ArtworkCallbacksRegistry {
    fun register(callbacks: ComponentCallbacks2)

    fun unregister(callbacks: ComponentCallbacks2)
}

internal class AndroidArtworkCallbacksRegistry(
    private val context: Context,
) : ArtworkCallbacksRegistry {
    override fun register(callbacks: ComponentCallbacks2) = context.registerComponentCallbacks(callbacks)

    override fun unregister(callbacks: ComponentCallbacks2) = context.unregisterComponentCallbacks(callbacks)
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class ArtworkMemoryPressureController(
    private val decodedMemoryCache: ArtworkDecodedMemoryCache,
    private val registry: ArtworkCallbacksRegistry,
) : ComponentCallbacks2, AutoCloseable {
    private val closed = AtomicBoolean(false)

    init {
        registry.register(this)
    }

    override fun onLowMemory() = decodedMemoryCache.clear()

    override fun onTrimMemory(level: Int) {
        if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            decodedMemoryCache.clear()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun close() {
        if (closed.compareAndSet(false, true)) registry.unregister(this)
    }
}

private fun Long?.exceedsEncodedLimit(): Boolean =
    this != null && this >= 0 && this > ArtworkLimits.MAX_ENCODED_BYTES
