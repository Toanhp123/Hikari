package app.openstory.reader.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.reader.assets.ReaderAssetFailure
import app.openstory.reader.assets.ReaderAssetGraphRevision
import app.openstory.reader.assets.ReaderAssetLoadException
import app.openstory.reader.assets.ReaderAssetManifestFactory
import app.openstory.reader.assets.ReaderPageAssetRequest
import app.openstory.reader.assets.ReaderViewportDirection
import app.openstory.reader.assets.ReaderViewportSnapshot
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderSessionId
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class ReaderImagePageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pageLocalFailuresStayAtThePageBoundary() {
        listOf(
            ReaderAssetFailure.TransportUnavailable(retryable = true),
            ReaderAssetFailure.DeliveryRejected(httpStatus = 503),
            ReaderAssetFailure.DeliveryLocatorStale,
            ReaderAssetFailure.CacheCorrupt,
            ReaderAssetFailure.CacheStorageUnavailable,
        ).forEach { failure ->
            assertEquals(ReaderImageFailureAction.RETRY_PAGE, readerImageFailureAction(failure))
        }
        assertEquals(
            ReaderImageFailureAction.WAIT_FOR_REPLACEMENT,
            readerImageFailureAction(ReaderAssetFailure.Superseded),
        )
        assertEquals(
            ReaderImageFailureAction.RELOAD_ROUTE,
            readerImageFailureAction(ReaderAssetFailure.RouteInvalidated),
        )
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun imageRequestUsesSemanticAssetModelAndReportsVisiblePresentation() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        val request = assetRequest()
        var observedModel: Any? = null
        var measuredHeight = 0
        var presented: ReaderPageAssetRequest? = null
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            chain.request.sizeResolver.size()
                            observedModel = chain.request.data
                            SuccessResult(
                                image = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).asImage(),
                                request = chain.request,
                            )
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        request = request,
                        visibleViewport = viewportFor(request),
                        acceptedViewport = viewportFor(request),
                        isActuallyVisible = { true },
                        onAssetPresented = { presented = it },
                        onRouteInvalidated = {},
                        onImageMeasured = { measuredHeight = it },
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 2_000L) { measuredHeight > 0 && presented != null }
            compose.onNodeWithText("Loading page").assertDoesNotExist()
            assertSame(request, observedModel)
            assertEquals(request, presented)
            assertTrue(measuredHeight > 0)
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun lateSuccessAfterPageLeavesViewportIsNotPresented() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        val request = assetRequest()
        var presented = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            SuccessResult(
                                image = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).asImage(),
                                request = chain.request,
                            )
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        request = request,
                        visibleViewport = viewportFor(request),
                        acceptedViewport = viewportFor(request),
                        isActuallyVisible = { false },
                        onAssetPresented = { presented += 1 },
                        onRouteInvalidated = {},
                        onImageMeasured = {},
                    )
                }
            }

            compose.waitForIdle()
            assertEquals(0, presented)
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun pageLocalRetryRestartsPainterWithoutDocumentReloadPath() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        val request = assetRequest()
        var attempts = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            attempts += 1
                            if (attempts == 1) {
                                throw ReaderAssetLoadException(ReaderAssetFailure.TransportUnavailable(retryable = true))
                            }
                            SuccessResult(
                                image = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).asImage(),
                                request = chain.request,
                            )
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        request = request,
                        visibleViewport = viewportFor(request),
                        acceptedViewport = viewportFor(request),
                        isActuallyVisible = { true },
                        onAssetPresented = {},
                        onRouteInvalidated = {},
                        onImageMeasured = {},
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 2_000L) { attempts == 1 }
            compose.onNodeWithText("Retry").performClick()
            compose.waitUntil(timeoutMillis = 2_000L) { attempts >= 2 }
            compose.onNodeWithText("Page image unavailable").assertDoesNotExist()
            assertEquals(2, attempts)
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun supersededIsSilentAndNonUserVisible() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        val request = assetRequest(manifestRevision = 7)
        var routeInvalidations = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor {
                            throw ReaderAssetLoadException(ReaderAssetFailure.Superseded)
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        request = request,
                        visibleViewport = viewportFor(request),
                        acceptedViewport = viewportFor(request),
                        isActuallyVisible = { true },
                        onAssetPresented = {},
                        onRouteInvalidated = { routeInvalidations += 1 },
                        onImageMeasured = {},
                    )
                }
            }

            compose.waitForIdle()
            compose.onNodeWithText("Page image unavailable").assertDoesNotExist()
            assertEquals(0, routeInvalidations)
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun routeInvalidatedUsesSeparateRouteReloadCallback() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        val request = assetRequest(manifestRevision = 8)
        var routeInvalidations = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor {
                            throw ReaderAssetLoadException(ReaderAssetFailure.RouteInvalidated)
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        request = request,
                        visibleViewport = viewportFor(request),
                        acceptedViewport = viewportFor(request),
                        isActuallyVisible = { true },
                        onAssetPresented = {},
                        onRouteInvalidated = { revision ->
                            assertEquals(8L, revision)
                            routeInvalidations += 1
                        },
                        onImageMeasured = {},
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 2_000L) { routeInvalidations == 1 }
            compose.onNodeWithText("Page image unavailable").assertDoesNotExist()
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }

    private fun viewportFor(request: ReaderPageAssetRequest) = ReaderViewportSnapshot(
        sessionId = request.sessionId,
        manifestRevision = request.manifestRevision,
        leadingVisibleImageOrdinal = request.descriptor.imageOrdinal,
        trailingVisibleImageOrdinal = request.descriptor.imageOrdinal,
        direction = ReaderViewportDirection.IDLE,
        chapterProgressBasisPoints = 0,
    )

    private fun assetRequest(manifestRevision: Long = 1): ReaderPageAssetRequest {
        val release = ChapterRelease(
            ChapterReleaseId("release"),
            StoryId("story"),
            PluginId("plugin"),
            "source-story",
            "source-release",
            "chapter",
            ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            "en",
            1,
            CanonicalChapterId("chapter"),
        )
        val document = ReaderDocument(
            title = null,
            blocks = listOf(
                ReaderBlock.ImagePage("page-1", "asset-1", "https://example.test/page-1.jpg"),
            ),
            fingerprint = "document-1",
        )
        val manifest = requireNotNull(
            ReaderAssetManifestFactory().create(
                sessionId = ReaderSessionId(1),
                storyId = StoryId("story"),
                canonicalChapterId = CanonicalChapterId("chapter"),
                selectedRelease = release,
                graphRevision = ReaderAssetGraphRevision(1),
                document = document,
                imageSourcePolicy = ReaderImageSourcePolicy.FAIL_CLOSED,
                sourcePluginId = PluginId("plugin"),
            ),
        )
        return ReaderPageAssetRequest(
            sessionId = manifest.sessionId,
            manifestRevision = manifestRevision,
            descriptor = manifest.descriptors.single(),
        )
    }
}
