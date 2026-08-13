package app.openstory.catalog.ui.story

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.chapters.ChapterItemUiModel
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.chapters.ChapterReleaseUiModel
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.mapping.MappingUiState
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.catalog.ui.mapping.MappingItemUiModel
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactOverview() = capture(fixture(), "compact-overview.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactSources() = capture(fixture(StorySection.SOURCES), "compact-sources.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactChapters() = capture(fixture(StorySection.CHAPTERS), "compact-chapters.png")

    @Test @Config(sdk = [35], qualifiers = "w412dp-h892dp")
    fun largePhone() = capture(fixture(), "large-phone.png")

    @Test @Config(sdk = [35], qualifiers = "w600dp-h960dp")
    fun mediumTwoPane() = capture(fixture(StorySection.CHAPTERS), "medium-two-pane.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun missingArtworkFallback() = capture(fixture().withoutArtwork(), "missing-artwork.png", loadArtwork = false)

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun cachedError() = capture(
        fixture(StorySection.SOURCES).copy(failure = StoryRefreshFailure("catalog.offline", true)),
        "cached-error.png",
    )

    @OptIn(DelicateCoilApi::class)
    private fun capture(state: StoryUiState, fileName: String, loadArtwork: Boolean = true) {
        val original = SingletonImageLoader.get(RuntimeEnvironment.getApplication())
        var requests = 0
        var successes = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).eventListener(
                object : EventListener() {
                    override fun onSuccess(request: ImageRequest, result: SuccessResult) { successes++ }
                },
            ).components {
                if (loadArtwork) add(Interceptor { chain ->
                    requests++
                    SuccessResult(fixtureArtwork().asImage(), chain.request)
                })
            }.build(),
        )
        try {
            compose.setContent {
                HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                    StoryScreen(
                        state = state,
                        onRetry = {},
                        onSourceSelected = { _, _ -> },
                        mappingState = mappingFixture(),
                        chapterState = chapterFixture(),
                    )
                }
            }
            if (loadArtwork) compose.waitUntil { requests > 0 && successes == requests }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/snapshots/story/$fileName")
        } finally {
            SingletonImageLoader.setUnsafe(original)
        }
    }
}

private fun fixture(section: StorySection = StorySection.OVERVIEW): StoryUiState {
    val storyId = StoryId("moonlit-archive")
    val sourceA = CatalogEntry(
        storyId, PluginId("catalog.mangadex"), "moonlit-a", "The Fox of the Moonlit Archive",
        aliases = setOf("Moonlit Archive"), authors = setOf("Mira Hoshino"),
        description = "A disgraced archivist follows a fox spirit into a library that records futures no one survived.",
        genres = setOf("Fantasy", "Mystery"), contentType = ContentType.WEB_NOVEL,
        languageTags = setOf("en", "ja"), coverUrl = "https://example.test/story.png",
        sourceUrl = "https://example.test/story", score = Score(8.8, 10.0),
    )
    val sourceB = sourceA.copy(pluginId = PluginId("catalog.mal"), sourceId = "moonlit-b", title = "Moonlit Archive")
    return StoryUiState(
        storyId = storyId,
        story = StoryUiModel(
            storyId, sourceA.title, sourceA.contentType, sourceA.aliases, sourceA.description,
            sourceA.coverUrl, sourceA.score, sourceA.authors, sourceA.genres, sourceA.languageTags,
            listOf(sourceA, sourceB),
        ),
        selectedSource = StorySourceIdentity(sourceA.pluginId, sourceA.sourceId),
        libraryStatus = LibraryStatus.READING,
        resumeTarget = ReaderTarget(storyId, CanonicalChapterId("chapter-12"), ChapterReleaseId("release-12")),
        selectedSection = section,
    )
}

private fun StoryUiState.withoutArtwork() = copy(story = story?.copy(coverUrl = null, sources = story.sources.map { it.copy(coverUrl = null) }))

private fun chapterFixture() = ChapterListUiState(
    storyId = StoryId("moonlit-archive"), unreadCount = 2,
    readableTargets = listOf(
        ReaderTarget(StoryId("moonlit-archive"), CanonicalChapterId("chapter-12"), ChapterReleaseId("release-12")),
    ),
    chapters = listOf(
        ChapterItemUiModel(
            CanonicalChapterId("chapter-12"), "Chapter 12 · The Locked Constellation", false, true,
            listOf(ChapterReleaseUiModel(ChapterReleaseId("release-12"), PluginId("mangadex"), "MangaDex", "English", 12L)),
        ),
        ChapterItemUiModel(CanonicalChapterId("chapter-11"), "Chapter 11 · A Fox at Dawn", false, false, emptyList()),
    ),
)

private fun mappingFixture() = MappingUiState(
    mappings = listOf(MappingItemUiModel(PluginId("mangadex"), "reading-source", ContentMappingOrigin.USER_APPROVED)),
)

private fun fixtureArtwork(): Bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply {
    for (y in 0 until height) for (x in 0 until width) {
        setPixel(x, y, when {
            x < width / 3 -> Color(0xFFC66A3D)
            y < height / 2 -> Color(0xFF315E68)
            else -> Color(0xFF102A32)
        }.toArgb())
    }
}
