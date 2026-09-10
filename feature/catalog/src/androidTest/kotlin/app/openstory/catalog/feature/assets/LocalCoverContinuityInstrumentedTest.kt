package app.openstory.catalog.feature.assets

import android.content.ComponentCallbacks2
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.feature.R
import coil3.ImageLoader
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.Fetcher
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class LocalCoverContinuityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val closeables = mutableListOf<AutoCloseable>()

    @After
    fun closeOwnedResources() {
        closeables.asReversed().forEach(AutoCloseable::close)
    }

    @Test
    fun stableRevisionUsesTheExactSameMemoryAndDiskKeyWhileRevisionChangeInvalidatesBoth() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = CoverRequest(assetKey("story-1", "1"), localLocator("1")).toImageRequest(context)
        val same = CoverRequest(assetKey("story-1", "1"), localLocator("1")).toImageRequest(context)
        val changed = CoverRequest(assetKey("story-1", "2"), localLocator("2")).toImageRequest(context)
        val requestData = first.data as CoverRequest

        assertEquals(first.memoryCacheKey, same.memoryCacheKey)
        assertEquals(first.diskCacheKey, same.diskCacheKey)
        assertEquals(requestData.assetKey.stableCacheKey, first.memoryCacheKey)
        assertEquals(requestData.assetKey.stableCacheKey, first.diskCacheKey)
        assertNotEquals(first.memoryCacheKey, changed.memoryCacheKey)
        assertNotEquals(first.diskCacheKey, changed.diskCacheKey)
        assertEquals(localLocator("1"), requestData.locator)
    }

    @Test
    fun memoryHitPerformsNoEncodedDiskReadAndNoSecondLocalResolution() = runBlocking {
        val resolverCalls = AtomicInteger()
        val encodedCache = RecordingEncodedDiskCache()
        val harness = imageHarness(
            encodedCache = encodedCache,
            resolver = LocalCoverAssetResolver { _, _ ->
                resolverCalls.incrementAndGet()
                LocalCoverAsset(R.drawable.catalog_debug_manga_a)
            },
        )
        val request = CoverRequest(assetKey("story-memory", "1"), localLocator("1"))
            .toImageRequest(harness.context)

        assertTrue(harness.imageLoader.execute(request) is SuccessResult)
        val transitionRequest = CoverRequest(request.data.let { (it as CoverRequest).assetKey }, locator = null)
            .toImageRequest(harness.context)
        assertTrue(harness.imageLoader.execute(transitionRequest) is SuccessResult)

        assertEquals(1, resolverCalls.get())
        assertEquals(0, encodedCache.reads.get())
        assertTrue(harness.memoryCache.size <= CatalogImageLimits.DECODED_MEMORY_BYTES)
    }

    @Test
    fun repeatedCoverRequestsRemainInsideTheDecodedMemoryCeiling() = runBlocking {
        val harness = imageHarness(
            resolver = LocalCoverAssetResolver { _, _ ->
                LocalCoverAsset(R.drawable.catalog_debug_manga_a)
            },
        )

        repeat(64) { index ->
            val result = harness.imageLoader.execute(
                CoverRequest(assetKey("bounded-$index", "1"), localLocator("1"))
                    .toImageRequest(harness.context),
            )
            assertTrue(result is SuccessResult)
        }

        assertEquals(CatalogImageLimits.DECODED_MEMORY_BYTES, harness.memoryCache.maxSize)
        assertTrue(harness.memoryCache.size <= CatalogImageLimits.DECODED_MEMORY_BYTES)
    }

    @Test
    fun coverJobsAreCappedAtEightAndCancelledWaitingDemandNeverStarts() = runBlocking {
        val limiter = CoverJobLimiter(CatalogImageLimits.ACTIVE_COVER_JOBS)
        val release = CompletableDeferred<Unit>()
        val entered = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()

        val jobs = List(9) {
            async {
                limiter.withPermit {
                    entered.incrementAndGet()
                    val now = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    release.await()
                    active.decrementAndGet()
                }
            }
        }

        while (entered.get() < CatalogImageLimits.ACTIVE_COVER_JOBS) kotlinx.coroutines.yield()
        assertEquals(CatalogImageLimits.ACTIVE_COVER_JOBS, peak.get())
        assertEquals(CatalogImageLimits.ACTIVE_COVER_JOBS, entered.get())

        jobs.last().cancelAndJoin()
        release.complete(Unit)
        jobs.dropLast(1).awaitAll()

        assertEquals(CatalogImageLimits.ACTIVE_COVER_JOBS, entered.get())
        assertEquals(0, active.get())
        assertEquals(0, CatalogImageLimits.MANUAL_OFFSCREEN_PREFETCH)
    }

    @Test
    fun loaderAndCallbacksStayAbsentUntilDemandThenPressureClearsOnlyDecodedMemory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val callbacks = RecordingCallbacksRegistry()
        val decodedMemoryCache = RecordingDecodedMemoryCache()
        val decodedCacheCreations = AtomicInteger()
        val loader = CatalogImageLoader(
            context = context,
            localResolver = LocalCoverAssetResolver { _, _ -> null },
            callbacksRegistry = callbacks,
            decodedMemoryCacheFactory = {
                decodedCacheCreations.incrementAndGet()
                decodedMemoryCache
            },
        ).also(closeables::add)

        assertEquals(0, decodedCacheCreations.get())
        assertNull(callbacks.registered)

        val session = loader.session()
        assertEquals(1, decodedCacheCreations.get())
        val diskSizeBeforeTrim = session.encodedDiskCache.maxSizeBytes
        val diskMarkerKey = assetKey("trim-disk-marker", "1").stableCacheKey
        assertTrue(
            session.encodedDiskCache.commit(
                diskMarkerKey,
                Buffer().writeUtf8("keep"),
                declaredLength = 4,
            ),
        )
        val callback = requireNotNull(callbacks.registered)

        callback.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(0, decodedMemoryCache.clearCount)
        callback.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(1, decodedMemoryCache.clearCount)
        callback.onLowMemory()
        assertEquals(2, decodedMemoryCache.clearCount)
        assertEquals(diskSizeBeforeTrim, session.encodedDiskCache.maxSizeBytes)
        session.encodedDiskCache.read(diskMarkerKey)!!.use { source ->
            assertEquals("keep", source.source().readUtf8())
        }

        loader.close()
        loader.close()
        assertNull(callbacks.registered)
        assertEquals(1, callbacks.unregisterCount)
    }

    @Test
    fun stableFailureDoesNotRetryOnUnrelatedRecompositionAndMetadataRemainsVisible() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolverCalls = AtomicInteger()
        val loader = CatalogImageLoader(
            context = context,
            localResolver = LocalCoverAssetResolver { _, _ ->
                resolverCalls.incrementAndGet()
                null
            },
        ).also(closeables::add)
        lateinit var recompose: () -> Unit
        var coverState: CoverArtworkState? = null

        composeRule.setContent {
            var tick by remember { mutableIntStateOf(0) }
            recompose = { tick++ }
            CompositionLocalProvider(LocalCatalogImageLoader provides loader) {
                MaterialTheme {
                    Column {
                        Text("Visible metadata $tick")
                        CoverArtwork(
                            title = "Failed cover",
                            locator = localLocator("1"),
                            assetKey = assetKey("failed-cover", "1"),
                            modifier = Modifier.size(96.dp),
                            onStateChanged = { coverState = it },
                        )
                    }
                }
            }
        }
        composeRule.waitUntil { coverState is CoverArtworkState.Failed }

        composeRule.runOnIdle {
            repeat(10) { recompose() }
        }
        composeRule.waitForIdle()

        assertEquals(1, resolverCalls.get())
        val failure = (coverState as CoverArtworkState.Failed).failure
        assertTrue(failure is CatalogFailure.Artwork)
        composeRule.onNodeWithText("Visible metadata 10").assertIsDisplayed()
    }

    @Test
    fun removingCoverFromCompositionCancelsItsInFlightDemand() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val started = AtomicInteger()
        val cancelled = AtomicInteger()
        val imageLoader = ImageLoader.Builder(context)
            .diskCache(null)
            .components {
                add(
                    Fetcher.Factory<CoverRequest> { _, _, _ ->
                        Fetcher {
                            started.incrementAndGet()
                            try {
                                awaitCancellation()
                            } finally {
                                cancelled.incrementAndGet()
                            }
                        }
                    },
                )
            }
            .build()
        closeables += AutoCloseable { imageLoader.shutdown() }
        lateinit var hideCover: () -> Unit

        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            hideCover = { visible = false }
            CompositionLocalProvider(
                LocalCatalogImageLoader provides CatalogCoverLoader { imageLoader },
            ) {
                MaterialTheme {
                    if (visible) {
                        CoverArtwork(
                            title = "Cancelable cover",
                            locator = localLocator("1"),
                            assetKey = assetKey("cancelable", "1"),
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
            }
        }
        composeRule.waitUntil { started.get() == 1 }

        composeRule.runOnIdle(hideCover)
        composeRule.waitUntil { cancelled.get() == 1 }

        assertEquals(1, started.get())
        assertEquals(1, cancelled.get())
    }

    @Test
    fun localResolverFailureMapsToTypedArtworkFailure() = runBlocking {
        val harness = imageHarness(resolver = LocalCoverAssetResolver { _, _ -> null })
        val result = harness.imageLoader.execute(
            CoverRequest(assetKey("missing-cover", "1"), localLocator("1"))
                .toImageRequest(harness.context),
        ) as ErrorResult

        val failure = result.throwable.findCatalogFailure()
        assertTrue(failure is CatalogFailure.Artwork)
    }

    @Test
    fun encodedDiskCacheClosesSnapshotsAndCommitsOnlyCompleteBoundedReplacements() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("task10-cache-${System.nanoTime()}"))
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        closeables += AutoCloseable { diskCache.shutdown() }
        val cache = CoverEncodedDiskCache(diskCache)
        val key = assetKey("disk-key", "1").stableCacheKey

        assertTrue(cache.commit(key, Buffer().writeUtf8("first"), declaredLength = 5))
        cache.read(key)!!.use { source -> assertEquals("first", source.source().readUtf8()) }
        assertTrue(cache.commit(key, Buffer().writeUtf8("second"), declaredLength = 6))
        cache.read(key)!!.use { source -> assertEquals("second", source.source().readUtf8()) }

        assertFalse(cache.commit(key, Buffer().writeUtf8("short"), declaredLength = 10))
        assertFalse(cache.commit(key, Buffer(), declaredLength = 0))
        cache.read(key)!!.use { source -> assertEquals("second", source.source().readUtf8()) }
        assertFalse(cache.commit(key, ThrowingSource(), declaredLength = null))
        assertFalse(
            cache.commit(
                key,
                Buffer().write(ByteArray(CatalogImageLimits.MAX_ENCODED_BYTES.toInt() + 1)),
                declaredLength = null,
            ),
        )
        cache.read(key)!!.use { source -> assertEquals("second", source.source().readUtf8()) }
        assertEquals(CatalogImageLimits.ENCODED_DISK_BYTES, cache.maxSizeBytes)
    }

    private fun imageHarness(
        encodedCache: CoverEncodedCache = RecordingEncodedDiskCache(),
        resolver: LocalCoverAssetResolver = LocalCoverAssetResolver { _, _ -> null },
    ): ImageHarness {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val memoryCache = MemoryCache.Builder()
            .maxSizeBytes(CatalogImageLimits.DECODED_MEMORY_BYTES)
            .weakReferencesEnabled(false)
            .build()
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("task10-loader-${System.nanoTime()}"))
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .components {
                add(CoverFetcher.Factory(resolver, encodedCache))
            }
            .build()
        closeables += AutoCloseable {
            imageLoader.shutdown()
            diskCache.shutdown()
        }
        return ImageHarness(context, imageLoader, memoryCache)
    }

    private data class ImageHarness(
        val context: android.content.Context,
        val imageLoader: ImageLoader,
        val memoryCache: MemoryCache,
    )

    private class RecordingEncodedDiskCache : CoverEncodedCache {
        val reads = AtomicInteger()
        override val maxSizeBytes: Long = CatalogImageLimits.ENCODED_DISK_BYTES

        override fun read(stableCacheKey: String): ImageSource? {
            reads.incrementAndGet()
            return null
        }

        override fun commit(stableCacheKey: String, source: Source, declaredLength: Long?): Boolean = false
    }

    private class RecordingCallbacksRegistry : CatalogImageCallbacksRegistry {
        var registered: ComponentCallbacks2? = null
        var unregisterCount = 0

        override fun register(callbacks: ComponentCallbacks2) {
            check(registered == null)
            registered = callbacks
        }

        override fun unregister(callbacks: ComponentCallbacks2) {
            assertSame(registered, callbacks)
            registered = null
            unregisterCount++
        }
    }

    private class RecordingDecodedMemoryCache : DecodedCoverMemoryCache {
        override val coilMemoryCache: MemoryCache = MemoryCache.Builder()
            .maxSizeBytes(CatalogImageLimits.DECODED_MEMORY_BYTES)
            .weakReferencesEnabled(false)
            .build()
        var clearCount = 0
            private set
        override val maxSizeBytes: Long
            get() = coilMemoryCache.maxSize

        override fun clear() {
            clearCount++
            coilMemoryCache.clear()
        }
    }

    private class ThrowingSource : Source {
        override fun read(sink: okio.Buffer, byteCount: Long): Long = throw IOException("fixture failure")
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
        override fun close() = Unit
    }

    private fun Throwable.findCatalogFailure(): CatalogFailure? {
        var current: Throwable? = this
        while (current != null) {
            if (current is CatalogFailureException) return current.failure
            current = current.cause
        }
        return null
    }

    private companion object {
        const val LOGICAL_ASSET_ID = "debug:manga:cover-a"
        val SOURCE_KEY = CatalogSourceKey("task10-local-cover")

        fun localLocator(version: String) = CoverLocator.TrustedLocalResource(LOGICAL_ASSET_ID, version)

        fun assetKey(sourceStoryId: String, version: String): CoverAssetKey {
            val storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId))
            return CoverAssetKey(storyId, CoverRevisionV1.local(LOGICAL_ASSET_ID, version))
        }
    }
}
