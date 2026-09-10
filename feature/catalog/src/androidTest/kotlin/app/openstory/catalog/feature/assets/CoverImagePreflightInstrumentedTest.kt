package app.openstory.catalog.feature.assets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Size
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class CoverImagePreflightInstrumentedTest {
    private val files = mutableListOf<File>()
    private val preflight = CoverImagePreflight()

    @After
    fun tearDown() {
        files.forEach(File::delete)
    }

    @Test
    fun jpegPngAndStaticWebpAreBoundsProbedAndSampledForTheRequestedSurface() {
        listOf(
            "image/jpeg" to encodedBitmap(Bitmap.CompressFormat.JPEG, 1_024, 768),
            "image/png" to encodedBitmap(Bitmap.CompressFormat.PNG, 1_024, 768),
            "image/webp" to encodedBitmap(Bitmap.CompressFormat.WEBP, 1_024, 768),
        ).forEach { (mediaType, bytes) ->
            val file = fixture(bytes)

            val result = preflight.inspect(file, mediaType, Size(128, 96))

            assertEquals(1_024, result.sourceWidth)
            assertEquals(768, result.sourceHeight)
            assertTrue(result.sampleSize > 1)
            val options = BitmapFactory.Options().apply { inSampleSize = result.sampleSize }
            val decoded = requireNotNull(BitmapFactory.decodeFile(file.path, options))
            try {
                assertTrue(decoded.width <= 256)
                assertTrue(decoded.height <= 192)
            } finally {
                decoded.recycle()
            }
        }
    }

    @Test
    fun sourceWidthHeightAndPixelSurfaceAreRejectedBeforeFullDecode() {
        val cases = listOf(
            "image/jpeg" to patchJpegDimensions(
                encodedBitmap(Bitmap.CompressFormat.JPEG, 1, 1),
                width = 8_193,
                height = 1,
            ),
            "image/png" to patchPngDimensions(
                encodedBitmap(Bitmap.CompressFormat.PNG, 1, 1),
                width = 1,
                height = 8_193,
            ),
            "image/webp" to patchWebpDimensions(
                encodedBitmap(Bitmap.CompressFormat.WEBP, 1, 1),
                width = 8_000,
                height = 4_001,
            ),
        )

        cases.forEach { (mediaType, bytes) ->
            val failure = assertArtworkFailure {
                preflight.inspect(fixture(bytes), mediaType, Size(128, 192))
            }

            assertEquals(mediaType, CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE, failure.reason)
        }
    }

    @Test
    fun malformedHeaderAndDeclaredTypeMismatchFailClosed() {
        assertEquals(
            CatalogArtworkFailureReason.DECODE_FAILED,
            assertArtworkFailure {
                preflight.inspect(fixture("not-an-image".encodeToByteArray()), "image/png", Size(64, 64))
            }.reason,
        )
        assertEquals(
            CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED,
            assertArtworkFailure {
                preflight.inspect(
                    fixture(encodedBitmap(Bitmap.CompressFormat.PNG, 8, 8)),
                    "image/jpeg",
                    Size(64, 64),
                )
            }.reason,
        )
    }

    @Test
    fun originalSizeRemoteDecodeIsNotAdmitted() {
        val failure = assertArtworkFailure {
            preflight.inspect(
                fixture(encodedBitmap(Bitmap.CompressFormat.JPEG, 64, 64)),
                "image/jpeg",
                Size.ORIGINAL,
            )
        }

        assertEquals(CatalogArtworkFailureReason.DECODE_FAILED, failure.reason)
    }

    @Test
    fun animatedWebpAndApngContainersAreRejected() {
        assertEquals(
            CatalogArtworkFailureReason.DECODE_FAILED,
            assertArtworkFailure {
                preflight.inspect(fixture(animatedWebpHeader()), "image/webp", Size(64, 64))
            }.reason,
        )
        assertEquals(
            CatalogArtworkFailureReason.DECODE_FAILED,
            assertArtworkFailure {
                val png = encodedBitmap(Bitmap.CompressFormat.PNG, 8, 8)
                preflight.inspect(fixture(insertApngAnimationChunk(png)), "image/png", Size(64, 64))
            }.reason,
        )
    }

    @Test
    fun controlledRemoteMissPreflightsAndCommitsWhileDiskHitSkipsTransport() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cacheDirectory = File(context.cacheDir, "remote-cover-cache-${System.nanoTime()}")
        val diskCache = DiskCache.Builder()
            .directory(cacheDirectory)
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        val encodedCache = CoverEncodedDiskCache(diskCache)
        val encoded = encodedBitmap(Bitmap.CompressFormat.PNG, 1_024, 768)
        val transport = InstrumentedRemoteCoverTransport(encoded, "image/png")
        val locator = remoteLocator()
        val request = CoverRequest(remoteAssetKey(locator), locator)
        val options = Options(context = context, size = Size(128, 96), fileSystem = FileSystem.SYSTEM)
        try {
            val remoteResult = CoverFetcher(
                request = request,
                options = options,
                localResolver = LocalCoverAssetResolver { _, _ -> null },
                encodedCache = encodedCache,
                remoteTransport = transport,
                policyProvider = ::remotePolicyProvider,
                preflight = preflight,
            ).fetch() as SourceFetchResult
            remoteResult.source.close()

            val diskResult = CoverFetcher(
                request = request,
                options = options,
                localResolver = LocalCoverAssetResolver { _, _ -> null },
                encodedCache = encodedCache,
                remoteTransport = transport,
                policyProvider = ::remotePolicyProvider,
                preflight = preflight,
            ).fetch() as SourceFetchResult
            diskResult.source.close()

            assertEquals(1, transport.requestCount.get())
            assertEquals(1, transport.closeCount.get())
            encodedCache.read(request.assetKey.stableCacheKey)!!.use { cached ->
                assertTrue(cached.source().readByteArray().contentEquals(encoded))
            }
        } finally {
            diskCache.shutdown()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun dimensionFailureClosesResponseAndNeverCommitsEncodedEntry() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cacheDirectory = File(context.cacheDir, "remote-cover-reject-${System.nanoTime()}")
        val diskCache = DiskCache.Builder()
            .directory(cacheDirectory)
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        val encodedCache = CoverEncodedDiskCache(diskCache)
        val oversized = patchPngDimensions(
            encodedBitmap(Bitmap.CompressFormat.PNG, 1, 1),
            width = 8_193,
            height = 1,
        )
        val transport = InstrumentedRemoteCoverTransport(oversized, "image/png")
        val locator = remoteLocator()
        val request = CoverRequest(remoteAssetKey(locator), locator)
        try {
            val failure = assertArtworkFailureSuspend {
                CoverFetcher(
                    request = request,
                    options = Options(context = context, size = Size(128, 96)),
                    localResolver = LocalCoverAssetResolver { _, _ -> null },
                    encodedCache = encodedCache,
                    remoteTransport = transport,
                    policyProvider = ::remotePolicyProvider,
                    preflight = preflight,
                ).fetch()
            }

            assertEquals(CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE, failure.reason)
            assertEquals(1, transport.closeCount.get())
            assertEquals(null, encodedCache.read(request.assetKey.stableCacheKey))
        } finally {
            diskCache.shutdown()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun restoredRemoteDiskHitRequiresTheCurrentHostOwnedPolicy() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cacheDirectory = File(context.cacheDir, "remote-cover-policy-hit-${System.nanoTime()}")
        val diskCache = DiskCache.Builder()
            .directory(cacheDirectory)
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        val encodedCache = CoverEncodedDiskCache(diskCache)
        val locator = remoteLocator()
        val request = CoverRequest(remoteAssetKey(locator), locator)
        val encoded = encodedBitmap(Bitmap.CompressFormat.PNG, 32, 32)
        check(encodedCache.commit(request.assetKey.stableCacheKey, okio.Buffer().write(encoded), encoded.size.toLong()))
        try {
            val failure = assertArtworkFailureSuspend {
                CoverFetcher(
                    request = request,
                    options = Options(context = context, size = Size(32, 32)),
                    localResolver = LocalCoverAssetResolver { _, _ -> null },
                    encodedCache = encodedCache,
                    remoteTransport = null,
                    policyProvider = { SourceAssetPolicyProvider { null } },
                    preflight = preflight,
                ).fetch()
            }

            assertEquals(CatalogArtworkFailureReason.POLICY_REJECTED, failure.reason)
        } finally {
            diskCache.shutdown()
            cacheDirectory.deleteRecursively()
        }
    }

    private fun fixture(bytes: ByteArray): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return File.createTempFile("cover-preflight-", ".img", context.cacheDir).also { file ->
            file.writeBytes(bytes)
            files += file
        }
    }

    private fun encodedBitmap(format: Bitmap.CompressFormat, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(format, 90, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun remoteLocator(): CoverLocator.RemoteHttps {
        val uri = RemoteHttpsUriV1.parseAndNormalize("https://images.example.com/cover.png?sig=A%2F")
        return CoverLocator.RemoteHttps(SOURCE_KEY, uri, CoverRevisionV1.remoteUri(uri))
    }

    private fun remoteAssetKey(locator: CoverLocator.RemoteHttps): CoverAssetKey {
        val ref = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "remote-story")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "remote-story",
        )
        return CoverAssetKey(ref.storyId, locator.revision)
    }

    private fun remotePolicyProvider(): SourceAssetPolicyProvider = SourceAssetPolicyProvider { key ->
        SourceAssetPolicy(SOURCE_KEY, setOf("images.example.com")).takeIf { key == SOURCE_KEY }
    }

    private fun assertArtworkFailure(block: () -> Unit): CatalogFailure.Artwork {
        val thrown = try {
            block()
            throw AssertionError("Expected CatalogFailureException")
        } catch (failure: CatalogFailureException) {
            failure
        }
        return thrown.failure as? CatalogFailure.Artwork
            ?: throw AssertionError("Expected artwork failure, got ${thrown.failure}")
    }

    private suspend fun assertArtworkFailureSuspend(block: suspend () -> Unit): CatalogFailure.Artwork {
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
        val SOURCE_KEY = CatalogSourceKey("instrumented.source")
    }
}

private class InstrumentedRemoteCoverTransport(
    private val encoded: ByteArray,
    private val contentType: String,
) : RemoteCoverTransport {
    val requestCount = AtomicInteger()
    val closeCount = AtomicInteger()

    override suspend fun execute(request: RemoteCoverTransportRequest): RemoteCoverTransportResponse {
        requestCount.incrementAndGet()
        return object : RemoteCoverTransportResponse {
            override val statusCode = 200
            override val redirectLocation: String? = null
            override val contentType: String = this@InstrumentedRemoteCoverTransport.contentType
            override val contentLength: Long = encoded.size.toLong()
            override val body: InputStream = ByteArrayInputStream(encoded)

            override fun close() {
                if (closeCount.incrementAndGet() == 1) body.close()
            }
        }
    }
}

private fun patchJpegDimensions(bytes: ByteArray, width: Int, height: Int): ByteArray =
    bytes.copyOf().also { patched ->
        var index = 2
        while (index + 8 < patched.size) {
            if (patched[index].toInt() and 0xff != 0xff) {
                index++
                continue
            }
            val marker = patched[index + 1].toInt() and 0xff
            if (marker in setOf(0xc0, 0xc1, 0xc2)) {
                patched[index + 5] = (height ushr 8).toByte()
                patched[index + 6] = height.toByte()
                patched[index + 7] = (width ushr 8).toByte()
                patched[index + 8] = width.toByte()
                return@also
            }
            val length = ((patched[index + 2].toInt() and 0xff) shl 8) or
                (patched[index + 3].toInt() and 0xff)
            index += 2 + length
        }
        error("JPEG SOF marker not found")
    }

private fun patchPngDimensions(bytes: ByteArray, width: Int, height: Int): ByteArray =
    bytes.copyOf().also { patched ->
        check(String(patched, 12, 4, Charsets.US_ASCII) == "IHDR")
        writeIntBigEndian(patched, 16, width)
        writeIntBigEndian(patched, 20, height)
        val crc = CRC32().apply { update(patched, 12, 17) }.value.toInt()
        writeIntBigEndian(patched, 29, crc)
    }

private fun patchWebpDimensions(bytes: ByteArray, width: Int, height: Int): ByteArray =
    bytes.copyOf().also { patched ->
        val frameHeader = patched.indexOfSequence(byteArrayOf(0x9d.toByte(), 0x01, 0x2a))
        check(frameHeader >= 0) { "WebP VP8 frame header not found" }
        patched[frameHeader + 3] = (width and 0xff).toByte()
        patched[frameHeader + 4] = ((width ushr 8) and 0x3f).toByte()
        patched[frameHeader + 5] = (height and 0xff).toByte()
        patched[frameHeader + 6] = ((height ushr 8) and 0x3f).toByte()
    }

private fun animatedWebpHeader(): ByteArray = byteArrayOf(
    'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
    22, 0, 0, 0,
    'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
    'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte(),
    10, 0, 0, 0,
    0x02, 0, 0, 0,
    0, 0, 0,
    0, 0, 0,
)

private fun insertApngAnimationChunk(png: ByteArray): ByteArray {
    val chunkType = "acTL".encodeToByteArray()
    val data = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0)
    val crc = CRC32().apply {
        update(chunkType)
        update(data)
    }.value.toInt()
    val chunk = ByteArray(4 + 4 + data.size + 4).also { bytes ->
        writeIntBigEndian(bytes, 0, data.size)
        chunkType.copyInto(bytes, destinationOffset = 4)
        data.copyInto(bytes, destinationOffset = 8)
        writeIntBigEndian(bytes, 8 + data.size, crc)
    }
    val insertAt = 33
    return png.copyOfRange(0, insertAt) + chunk + png.copyOfRange(insertAt, png.size)
}

private fun writeIntBigEndian(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value ushr 24).toByte()
    bytes[offset + 1] = (value ushr 16).toByte()
    bytes[offset + 2] = (value ushr 8).toByte()
    bytes[offset + 3] = value.toByte()
}

private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
    for (index in 0..size - sequence.size) {
        if (sequence.indices.all { offset -> this[index + offset] == sequence[offset] }) return index
    }
    return -1
}
