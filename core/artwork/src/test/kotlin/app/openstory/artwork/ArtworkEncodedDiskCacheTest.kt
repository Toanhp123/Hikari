package app.openstory.artwork

import coil3.disk.DiskCache
import java.util.concurrent.CancellationException
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtworkEncodedDiskCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replacementIsAtomicAndCancellationLeavesCacheWritable() {
        val diskCache = DiskCache.Builder()
            .directory(temporaryFolder.newFolder("encoded-cache").toOkioPath())
            .maxSizeBytes(ArtworkLimits.ENCODED_DISK_BYTES)
            .build()
        try {
            val cache = ArtworkEncodedDiskCache(diskCache)
            assertEquals(ArtworkLimits.ENCODED_DISK_BYTES, cache.maxSizeBytes)
            assertFalse(cache.commit(KEY, Buffer(), declaredLength = 0))
            assertTrue(cache.commit(KEY, Buffer().writeUtf8("first"), declaredLength = 5))

            assertFalse(cache.commit(KEY, FailingSource(IllegalStateException("failed")), null))
            assertTrue(cache.commit(KEY, Buffer().writeUtf8("second"), declaredLength = 6))
            cache.read(KEY)!!.use { source -> assertEquals("second", source.source().readUtf8()) }

            assertThrows(CancellationException::class.java) {
                cache.commit(KEY, FailingSource(CancellationException("cancelled")), null)
            }
            assertTrue(cache.commit(KEY, Buffer().writeUtf8("third"), declaredLength = 5))
        } finally {
            diskCache.shutdown()
        }
    }

    private class FailingSource(private val failure: RuntimeException) : Source {
        override fun read(sink: Buffer, byteCount: Long): Long = throw failure
        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
    }

    private companion object {
        const val KEY = "hikari:v2:artwork:v1:test"
    }
}
