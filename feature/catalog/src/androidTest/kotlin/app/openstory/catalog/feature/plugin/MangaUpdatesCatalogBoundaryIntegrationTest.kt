package app.openstory.catalog.feature.plugin

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.assets.CatalogImageLimits
import app.openstory.catalog.feature.assets.CoverEncodedDiskCache
import app.openstory.catalog.feature.assets.CoverFetcher
import app.openstory.catalog.feature.assets.CoverRequest
import app.openstory.catalog.feature.assets.LocalCoverAssetResolver
import app.openstory.catalog.feature.assets.RemoteCoverPolicy
import app.openstory.plugins.api.protocol.PluginOperation
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Size
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaUpdatesCatalogBoundaryIntegrationTest {
    private lateinit var appContext: Context
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        appContext = instrumentation.targetContext.applicationContext
        testContext = instrumentation.context
        File(appContext.cacheDir, CATALOG_COVER_CACHE).deleteRecursively()
    }

    @After
    fun tearDown() {
        File(appContext.cacheDir, CATALOG_COVER_CACHE).deleteRecursively()
    }

    @Test
    fun realPluginCoverLocatorKeepsRemotePolicyAndPreflightFailuresBounded() = runBlocking {
        val source = ReferencePluginBridge.fromAssets(
            testContext,
            ControlledPluginTransport(::standardResponse),
        )
        val coverUri = try {
            val acquisition = source.acquireDiscover(CatalogMediaType.MANGA)
            val item = acquisition.sections.first().items.first()
            (item.cover as app.openstory.catalog.domain.asset.AcquisitionCoverInput.RemoteHttps).rawUri
        } finally {
            source.close()
        }
        val locator = RemoteHttpsUriV1.parseAndNormalize(coverUri).let { uri ->
            CoverLocator.RemoteHttps(SOURCE_KEY, uri, CoverRevisionV1.remoteUri(uri))
        }
        val key = CoverAssetKey(storyRef(MANGA_ID).storyId, locator.revision)
        val policyProvider: suspend () -> SourceAssetPolicyProvider = {
            SourceAssetPolicyProvider { requested ->
                SourceAssetPolicy(SOURCE_KEY, ALLOWED_HOSTS).takeIf { requested == SOURCE_KEY }
            }
        }

        assertArtworkFailure(CatalogArtworkFailureReason.INVALID_LOCATOR) {
            RemoteCoverPolicy(policyProvider, null, appContext.cacheDir)
                .fetch(SOURCE_KEY, "http://cdn.mangaupdates.com/cover.png")
        }
        assertArtworkFailure(CatalogArtworkFailureReason.POLICY_REJECTED) {
            RemoteCoverPolicy(policyProvider, null, appContext.cacheDir)
                .fetch(SOURCE_KEY, "https://unapproved.example/cover.png")
        }

        val redirectTransport = ControlledPluginTransport(
            handler = ::standardResponse,
            coverHandler = { request ->
                if (request.uri == locator.normalizedUri.value) {
                    ControlledCoverResponse(
                        statusCode = 302,
                        redirectLocation = "https://www.mangaupdates.com/cover-final.png",
                    )
                } else {
                    ControlledCoverResponse(200, "image/png", png(48, 72))
                }
            },
        )
        fetchCover(key, locator, redirectTransport, policyProvider)
        assertEquals(2, redirectTransport.coverRequestCount.get())

        val rejectionCases = listOf(
            CatalogArtworkFailureReason.REDIRECT_REJECTED to ControlledPluginTransport(
                handler = ::standardResponse,
                coverHandler = { request ->
                    val hop = request.uri.substringAfterLast('/').substringBefore('.').toIntOrNull() ?: 0
                    ControlledCoverResponse(
                        statusCode = 302,
                        redirectLocation = "https://cdn.mangaupdates.com/${hop + 1}.png",
                    )
                },
            ),
            CatalogArtworkFailureReason.REDIRECT_REJECTED to ControlledPluginTransport(
                handler = ::standardResponse,
                coverHandler = {
                    ControlledCoverResponse(
                        statusCode = 302,
                        redirectLocation = "https://unapproved.example/cover.png",
                    )
                },
            ),
            CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED to ControlledPluginTransport(
                handler = ::standardResponse,
                coverHandler = { ControlledCoverResponse(200, "text/plain", "nope".encodeToByteArray()) },
            ),
            CatalogArtworkFailureReason.ENCODED_TOO_LARGE to ControlledPluginTransport(
                handler = ::standardResponse,
                coverHandler = {
                    ControlledCoverResponse(
                        statusCode = 200,
                        contentType = "image/png",
                        contentLength = CatalogImageLimits.MAX_ENCODED_BYTES + 1,
                    )
                },
            ),
            CatalogArtworkFailureReason.ENCODED_TOO_LARGE to ControlledPluginTransport(
                handler = ::standardResponse,
                coverHandler = {
                    ControlledCoverResponse(
                        statusCode = 200,
                        contentType = "image/png",
                        bytes = ByteArray((CatalogImageLimits.MAX_ENCODED_BYTES + 1).toInt()),
                        contentLength = null,
                    )
                },
            ),
            CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE to ControlledPluginTransport(
                handler = ::standardResponse,
                coverHandler = {
                    ControlledCoverResponse(200, "image/png", oversizedPngWidth())
                },
            ),
        )
        rejectionCases.forEach { (reason, transport) ->
            assertArtworkFailure(reason) { fetchCover(key, locator, transport, policyProvider) }
        }
    }

    @Test
    fun executorEnforcesOwnedTimeoutAndByteCeilingsWhilePropagatingCancellation() = runBlocking {
        val timeout = ReferencePluginExecutor(
            evaluator = ReferenceJavaScriptEvaluator { _, _, _, _, _ -> awaitCancellation() },
            limits = ReferencePluginLimits(timeoutMillis = 1),
        )
        assertExecutionFailure(ReferencePluginFailureCode.TIMEOUT) {
            timeout.execute("source", PluginOperation.CATALOG_HOME, JsonObject(emptyMap())) { "{}" }
        }

        val bridgeOversize = ReferencePluginExecutor(
            evaluator = ReferenceJavaScriptEvaluator { _, _, _, _, bridge ->
                bridge("x".repeat(256 * 1_024 + 1))
            },
        )
        assertExecutionFailure(ReferencePluginFailureCode.BRIDGE_MESSAGE_TOO_LARGE) {
            bridgeOversize.execute("source", PluginOperation.CATALOG_HOME, JsonObject(emptyMap())) { "{}" }
        }

        val outputOversize = ReferencePluginExecutor(
            evaluator = ReferenceJavaScriptEvaluator { _, _, _, _, _ ->
                "x".repeat(2 * 1_024 * 1_024 + 1)
            },
        )
        assertExecutionFailure(ReferencePluginFailureCode.OUTPUT_TOO_LARGE) {
            outputOversize.execute("source", PluginOperation.CATALOG_HOME, JsonObject(emptyMap())) { "{}" }
        }

        val cancellable = ReferencePluginExecutor(
            evaluator = ReferenceJavaScriptEvaluator { _, _, _, _, _ -> awaitCancellation() },
        )
        val work = async {
            cancellable.execute("source", PluginOperation.CATALOG_HOME, JsonObject(emptyMap())) { "{}" }
        }
        work.cancelAndJoin()
        assertTrue(work.isCancelled)
        assertTrue(runCatching { work.await() }.exceptionOrNull() is CancellationException)
    }

    private suspend fun assertExecutionFailure(
        expected: ReferencePluginFailureCode,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected $expected")
        } catch (failure: ReferencePluginExecutionException) {
            assertEquals(expected, failure.failureCode)
        }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun fetchCover(
        key: CoverAssetKey,
        locator: CoverLocator.RemoteHttps,
        transport: ControlledPluginTransport,
        policyProvider: suspend () -> SourceAssetPolicyProvider,
    ) {
        val cacheDirectory = File(appContext.cacheDir, "task17-cover-${System.nanoTime()}")
        val diskCache = DiskCache.Builder()
            .directory(cacheDirectory)
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        try {
            val result = CoverFetcher(
                request = CoverRequest(key, locator),
                options = Options(
                    context = appContext,
                    size = Size(48, 72),
                    fileSystem = FileSystem.SYSTEM,
                ),
                localResolver = LocalCoverAssetResolver { _, _ -> null },
                encodedCache = CoverEncodedDiskCache(diskCache),
                remoteTransport = transport,
                policyProvider = policyProvider,
                preflight = app.openstory.catalog.feature.assets.CoverImagePreflight(),
            ).fetch() as SourceFetchResult
            result.source.close()
        } finally {
            diskCache.shutdown()
            cacheDirectory.deleteRecursively()
        }
    }

    private suspend fun assertArtworkFailure(
        expected: CatalogArtworkFailureReason,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected artwork failure $expected")
        } catch (failure: CatalogFailureException) {
            assertEquals(expected, (failure.failure as CatalogFailure.Artwork).reason)
        }
    }

    private fun oversizedPngWidth(): ByteArray = png(1, 1).also { bytes ->
        check(String(bytes, 12, 4, Charsets.US_ASCII) == "IHDR")
        writeIntBigEndian(bytes, 16, 8_193)
        val crc = java.util.zip.CRC32().apply { update(bytes, 12, 17) }.value.toInt()
        writeIntBigEndian(bytes, 29, crc)
    }

    private fun writeIntBigEndian(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private companion object {
        const val CATALOG_COVER_CACHE = MangaUpdatesCatalogIntegrationTest.CATALOG_COVER_CACHE
        const val MANGA_ID = MangaUpdatesCatalogIntegrationTest.MANGA_ID
        val SOURCE_KEY = MangaUpdatesCatalogIntegrationTest.SOURCE_KEY
        val ALLOWED_HOSTS = MangaUpdatesCatalogIntegrationTest.ALLOWED_HOSTS
    }
}
