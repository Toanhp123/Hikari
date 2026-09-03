package app.openstory.reader.assets

import android.graphics.BitmapFactory
import app.openstory.common.id.PluginId
import app.openstory.reader.routing.ReaderSessionId
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderAssetCoilFetcherTest {
    @Test
    fun localLeaseRemainsOpenWhileCoilSourceIsActiveAndClosesExactlyOnce() = runTest {
        val lease = TrackingLease(VALID_PNG)
        val fetcher = ReaderAssetCoilFetcher(
            request = TEST_REQUEST,
            options = options(diskCachePolicy = CachePolicy.DISABLED),
            requestPage = { ReaderAssetLoadOutcome.Local(lease) },
        )

        val result = assertIs<SourceFetchResult>(fetcher.fetch())

        assertEquals(DataSource.DISK, result.dataSource)
        assertFalse(lease.closed)
        assertContentEquals(VALID_PNG.copyOfRange(0, 8), result.source.source().readByteArray(8))
        assertEquals(0, lease.closeCount)
        result.source.close()
        result.source.close()
        assertEquals(1, lease.closeCount)
    }

    @Test
    fun localLeaseClosesWhenCoilMaterializesBufferedSourceAsTempFile() = runTest {
        val lease = TrackingLease(VALID_PNG)
        val fetcher = ReaderAssetCoilFetcher(
            request = TEST_REQUEST,
            options = options(diskCachePolicy = CachePolicy.DISABLED),
            requestPage = { ReaderAssetLoadOutcome.Local(lease) },
        )

        val result = assertIs<SourceFetchResult>(fetcher.fetch())

        result.source.file()
        assertEquals(1, lease.closeCount)
        result.source.close()
        assertEquals(1, lease.closeCount)
    }

    @Test
    fun localLeaseClosesWhenOpeningItsStreamFails() = runTest {
        val lease = FailingOpenLease()
        val fetcher = ReaderAssetCoilFetcher(
            request = TEST_REQUEST,
            options = options(diskCachePolicy = CachePolicy.DISABLED),
            requestPage = { ReaderAssetLoadOutcome.Local(lease) },
        )

        val failure = runCatching { fetcher.fetch() }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        assertEquals(1, lease.closeCount)
    }

    @Test
    fun boundedRemoteBytesRemainDecodableWithoutDurableCommit() = runTest {
        val payload = ReaderAssetPayload.verifiedBounded(
            bytes = VALID_PNG,
            mimeType = "image/png",
            sourceIntegrityEvidence = null,
        )
        val fetcher = ReaderAssetCoilFetcher(
            request = TEST_REQUEST,
            options = options(diskCachePolicy = CachePolicy.DISABLED),
            requestPage = { ReaderAssetLoadOutcome.Remote(payload) },
        )

        val result = assertIs<SourceFetchResult>(fetcher.fetch())
        val bitmap = BitmapFactory.decodeStream(result.source.source().inputStream())

        assertEquals(DataSource.NETWORK, result.dataSource)
        assertEquals("image/png", result.mimeType)
        assertNotNull(bitmap)
        result.source.close()
    }

    @Test
    fun readerModelUsesStableAssetHashAsMemoryCacheKey() {
        val keyer = ReaderPageAssetKeyer()

        assertEquals(
            "reader-asset:${TEST_REQUEST.descriptor.key.hash.value}",
            keyer.key(TEST_REQUEST, options(diskCachePolicy = CachePolicy.DISABLED)),
        )
    }

    @Test
    fun factoryRejectsReaderRequestsThatEnableCoilDiskCache() {
        val context = RuntimeEnvironment.getApplication()
        val imageLoader = ImageLoader.Builder(context).build()
        try {
            val failure = runCatching {
                ReaderAssetCoilFetcher.Factory { ReaderAssetLoadOutcome.Remote(TEST_PAYLOAD) }
                    .create(
                        data = TEST_REQUEST,
                        options = options(diskCachePolicy = CachePolicy.ENABLED),
                        imageLoader = imageLoader,
                    )
            }.exceptionOrNull()

            assertIs<IllegalArgumentException>(failure)
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun imageLoaderInstallerDoesNotResolveReaderCoordinatorDuringGlobalLoaderCreation() {
        val context = RuntimeEnvironment.getApplication()
        val installer = ReaderAssetImageLoaderInstaller(
            coordinatorProvider = Provider { error("Reader coordinator must stay lazy") },
        )

        val imageLoader = installer.createImageLoader(context)
        try {
            assertTrue(
                imageLoader.components.keyers.any { (_, dataType) ->
                    dataType == ReaderPageAssetRequest::class
                },
            )
            assertTrue(
                imageLoader.components.fetcherFactories.any { (_, dataType) ->
                    dataType == ReaderPageAssetRequest::class
                },
            )
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun imageLoaderInstallerRetainsGenericNetworkFetcher() {
        val context = RuntimeEnvironment.getApplication()
        val installer = ReaderAssetImageLoaderInstaller(
            coordinatorProvider = Provider { error("Reader coordinator must stay lazy") },
        )
        val imageLoader = installer.createImageLoader(context)
        try {
            val requestOptions = options(diskCachePolicy = CachePolicy.ENABLED)
            val mapped = imageLoader.components.map(
                "https://cdn.example.test/artwork.jpg",
                requestOptions,
            )

            assertNotNull(
                imageLoader.components.newFetcher(mapped, requestOptions, imageLoader),
            )
        } finally {
            imageLoader.shutdown()
        }
    }

    @Test
    fun failureIsExposedThroughReaderOwnedThrowableBridge() = runTest {
        val fetcher = ReaderAssetCoilFetcher(
            request = TEST_REQUEST,
            options = options(diskCachePolicy = CachePolicy.DISABLED),
            requestPage = {
                ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.RouteInvalidated)
            },
        )

        val failure = runCatching { fetcher.fetch() }.exceptionOrNull()
        val typed = assertIs<ReaderAssetLoadException>(failure)

        assertEquals(ReaderAssetFailure.RouteInvalidated, typed.failure)
    }

    private fun options(diskCachePolicy: CachePolicy): Options = Options(
        context = RuntimeEnvironment.getApplication(),
        diskCachePolicy = diskCachePolicy,
    )

    private class TrackingLease(
        private val bytes: ByteArray,
    ) : ReaderAssetReadLease {
        var closeCount = 0
            private set

        val closed: Boolean
            get() = closeCount > 0

        override val sizeBytes: Long = bytes.size.toLong()

        override fun openStream(): InputStream {
            check(!closed)
            return ByteArrayInputStream(bytes)
        }

        override fun close() {
            closeCount += 1
        }
    }

    private class FailingOpenLease : ReaderAssetReadLease {
        var closeCount = 0
            private set

        override val sizeBytes: Long = 1L

        override fun openStream(): InputStream = error("synthetic open failure")

        override fun close() {
            closeCount += 1
        }
    }

    private companion object {
        val VALID_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )

        val TEST_ASSET_KEY = ReaderPageAssetKey(
            schemaVersion = ReaderAssetKeySchemaVersion(1),
            sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("test.plugin")),
            securityScope = ReaderCacheSecurityScope.Public,
            contentVariant = ReaderContentVariant.ORIGINAL,
            persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
            imageSetNamespace = ReaderImageSetNamespace("3".repeat(64)),
            runtimeIsolationScope = null,
            pageIdentityHash = ReaderAssetIdentityHash("4".repeat(64)),
            hash = ReaderAssetKeyHash("5".repeat(64)),
        )

        val TEST_DESCRIPTOR = ReaderPageAssetDescriptor(
            key = TEST_ASSET_KEY,
            uiBlockId = "page-0",
            stableAssetId = "page/0",
            imageOrdinal = 0,
            deliveryLocator = "https://cdn.example.test/page-0.png",
            locatorFingerprint = ReaderAssetIdentity.locatorFingerprint(
                "https://cdn.example.test/page-0.png",
            ),
        )

        val TEST_REQUEST = ReaderPageAssetRequest(
            sessionId = ReaderSessionId(1L),
            manifestRevision = 1L,
            descriptor = TEST_DESCRIPTOR,
        )

        val TEST_PAYLOAD = ReaderAssetPayload.verifiedBounded(
            bytes = VALID_PNG,
            mimeType = "image/png",
            sourceIntegrityEvidence = null,
        )
    }
}
