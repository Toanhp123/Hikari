package app.openstory.reader.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.reader.assets.ReaderAssetGraphRevision
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
class ReaderImageContinuityTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Test
    fun `page request remains semantic after Coil memory eviction`() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        val request = assetRequest()
        val observedModels = mutableListOf<Any?>()
        var presentations = 0
        val imageLoader = ImageLoader.Builder(context)
            .components {
                add(
                    Interceptor { chain ->
                        observedModels += chain.request.data
                        SuccessResult(
                            image = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).asImage(),
                            request = chain.request,
                        )
                    },
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
        try {
            imageLoader.memoryCache?.clear()
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        request = request,
                        visibleViewport = viewportFor(request),
                        acceptedViewport = viewportFor(request),
                        isActuallyVisible = { true },
                        onAssetPresented = { presentations += 1 },
                        onRouteInvalidated = {},
                        onImageMeasured = {},
                    )
                }
            }
            compose.waitUntil(timeoutMillis = 2_000L) { presentations > 0 }
            assertEquals(1, observedModels.size)
            observedModels.forEach { observed -> assertSame(request, observed) }
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
            imageLoader.shutdown()
        }
    }

    private fun viewportFor(request: ReaderPageAssetRequest) = ReaderViewportSnapshot(
        sessionId = request.sessionId,
        manifestRevision = request.manifestRevision,
        leadingVisibleImageOrdinal = 0,
        trailingVisibleImageOrdinal = 0,
        direction = ReaderViewportDirection.IDLE,
        chapterProgressBasisPoints = 0,
    )

    private fun assetRequest(): ReaderPageAssetRequest {
        val pluginId = PluginId("org.example.reader")
        val storyId = StoryId("story")
        val chapterId = CanonicalChapterId("chapter")
        val release = ChapterRelease(
            id = ChapterReleaseId("release"),
            storyId = storyId,
            pluginId = pluginId,
            sourceStoryId = "source-story",
            sourceReleaseId = "source-release",
            displayLabel = "chapter",
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = chapterId,
        )
        val manifest = requireNotNull(
            ReaderAssetManifestFactory().create(
                sessionId = ReaderSessionId(1),
                storyId = storyId,
                canonicalChapterId = chapterId,
                selectedRelease = release,
                graphRevision = ReaderAssetGraphRevision(1),
                document = ReaderDocument(
                    title = null,
                    blocks = listOf(
                        ReaderBlock.ImagePage(
                            id = "page-1",
                            stableAssetId = "chapter/page-1.png",
                            imageUrl = "https://cdn.example.test/chapter/page-1.png",
                        ),
                    ),
                    fingerprint = "document",
                ),
                imageSourcePolicy = ReaderImageSourcePolicy.FAIL_CLOSED,
                sourcePluginId = pluginId,
            ),
        )
        return ReaderPageAssetRequest(manifest.sessionId, 1L, manifest.descriptors.single())
    }
}
