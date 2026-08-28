package app.openstory.catalog.ui.story

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.chapters.ChapterCapabilityState
import app.openstory.catalog.ui.chapters.ChapterItemUiModel
import app.openstory.catalog.ui.chapters.ChapterListContent
import app.openstory.catalog.ui.chapters.ChapterListUiState
import app.openstory.catalog.ui.chapters.ChapterReleaseUiModel
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun overviewExposesPullRefreshAction() {
        var refreshCalls = 0
        setStoryContent(
            state = fixture().withoutArtwork(),
            onRefresh = { refreshCalls += 1 },
        )

        val refreshAction = compose.onNodeWithTag("story-overview-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }

        compose.runOnIdle {
            assertTrue(refreshAction.action())
            assertEquals(1, refreshCalls)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun overviewPullGestureRefreshesSourceDetails() {
        var refreshCalls = 0
        setStoryContent(
            state = fixture(StorySection.OVERVIEW).withoutArtwork(),
            onRefresh = { refreshCalls += 1 },
        )

        compose.onNodeWithTag("story-overview-pull-refresh").performTouchInput { swipeDown() }
        compose.waitForIdle()

        assertEquals(1, refreshCalls)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun sourcesExposePullRefreshWithoutManualRefreshIcon() {
        var refreshCalls = 0
        setStoryContent(
            state = fixture(StorySection.SOURCES).withoutArtwork(),
            onRefresh = { refreshCalls += 1 },
        )

        val refreshAction = compose.onNodeWithTag("story-sources-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }

        compose.runOnIdle {
            assertTrue(refreshAction.action())
            assertEquals(1, refreshCalls)
        }
        compose.onAllNodesWithTag("story-source-refresh").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun sourcesPullGestureRefreshesSourceDetails() {
        var refreshCalls = 0
        setStoryContent(
            state = fixture(StorySection.SOURCES).withoutArtwork(),
            onRefresh = { refreshCalls += 1 },
        )

        compose.onNodeWithTag("story-sources-pull-refresh").performTouchInput { swipeDown() }
        compose.waitForIdle()

        assertEquals(1, refreshCalls)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chaptersDoNotExposePullRefresh() {
        setStoryContent(state = fixture(StorySection.CHAPTERS).withoutArtwork())

        compose.onAllNodesWithTag("story-overview-pull-refresh").assertCountEquals(0)
        compose.onAllNodesWithTag("story-sources-pull-refresh").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w700dp-h800dp")
    fun mediumSourcesExposeOnlyContentPaneRefresh() {
        setStoryContent(state = fixture(StorySection.SOURCES).withoutArtwork())

        compose.onAllNodesWithTag("story-overview-pull-refresh").assertCountEquals(0)
        compose.onAllNodesWithTag("story-sources-pull-refresh").assertCountEquals(1)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chaptersHideSourceDetailRefreshFailure() {
        setStoryContent(
            state = fixture(StorySection.CHAPTERS).withoutArtwork().copy(
                failure = StoryRefreshFailure("catalog.offline", retryable = true),
            ),
        )

        compose.onNodeWithText("Source detail refresh failed: catalog.offline").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun initialNoContentLoadingDoesNotExposePullRefresh() {
        setStoryContent(
            state = fixture().withoutArtwork().copy(
                story = null,
                refreshing = true,
                failure = null,
            ),
        )

        compose.onNodeWithText("Loading story").assertIsDisplayed()
        compose.onAllNodesWithTag("story-empty-pull-refresh").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun retryableEmptyErrorRemainsVisibleWhileRefreshing() {
        setStoryContent(
            state = fixture().withoutArtwork().copy(
                story = null,
                refreshing = true,
                failure = StoryRefreshFailure("catalog.offline", retryable = true),
            ),
        )

        compose.onNodeWithTag("story-empty-pull-refresh").assertIsDisplayed()
        compose.onNodeWithTag("story-empty-pull-refresh").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                "Refreshing",
            ),
        )
        compose.onNodeWithText("Story unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun storyHeroDoesNotShowFindSourceBeforeReaderCapabilityResolves() {
        val target = ReaderTarget(
            StoryId("moonlit-archive"),
            CanonicalChapterId("chapter-12"),
            ChapterReleaseId("release-12"),
        )
        setStoryContent(
            state = fixture().withoutArtwork().copy(resumeTarget = null),
            chapterState = storyChapterState(
                chapterCount = 1,
                releaseTargets = listOf(target),
                readerAvailabilityResolved = false,
            ),
        )

        compose.onNodeWithTag("story-reader-checking").assertIsDisplayed()
        compose.onAllNodesWithTag("story-find-source").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun emptyChapterListOffersFindSource() {
        setStoryContent(
            state = fixture().withoutArtwork().copy(resumeTarget = null),
            chapterState = storyChapterState(chapterCount = 0),
        )

        compose.onNodeWithTag("story-find-source").assertIsDisplayed().assertIsEnabled()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chapterGroupsWithoutReleasesDoNotShowFindSource() {
        setStoryContent(
            state = fixture().withoutArtwork().copy(resumeTarget = null),
            chapterState = storyChapterState(chapterCount = 1, releaseTargets = emptyList()),
        )

        compose.onNodeWithTag("story-no-releases").assertIsDisplayed()
        compose.onAllNodesWithTag("story-find-source").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun chapterObservationFailureDoesNotShowFindSource() {
        setStoryContent(
            state = fixture().withoutArtwork().copy(resumeTarget = null),
            chapterState = ChapterListUiState(
                storyId = StoryId("moonlit-archive"),
                content = ContentState.Failed(
                    CatalogUiFailure("chapter.list.observe_failed", retryable = true),
                ),
            ),
        )

        compose.onNodeWithTag("story-chapters-unavailable").assertIsDisplayed()
        compose.onAllNodesWithTag("story-find-source").assertCountEquals(0)
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun resumeTargetFromAnotherStoryIsNotValidated() {
        val target = ReaderTarget(
            StoryId("moonlit-archive"),
            CanonicalChapterId("chapter-12"),
            ChapterReleaseId("release-12"),
        )
        val staleResume = target.copy(storyId = StoryId("another-story"))
        setStoryContent(
            state = fixture().withoutArtwork().copy(resumeTarget = staleResume),
            chapterState = storyChapterState(
                chapterCount = 1,
                readableTargets = listOf(target),
                releaseTargets = listOf(target),
                readerAvailabilityResolved = true,
            ),
        )

        compose.onNodeWithTag("story-read").assertIsDisplayed()
        compose.onNodeWithText("Read").assertIsDisplayed()
        compose.onNodeWithText("Resume").assertDoesNotExist()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun resolvedChaptersWithoutReadableSourceShowFindSource() {
        val target = ReaderTarget(
            StoryId("moonlit-archive"),
            CanonicalChapterId("chapter-12"),
            ChapterReleaseId("release-12"),
        )
        setStoryContent(
            state = fixture().withoutArtwork().copy(resumeTarget = null),
            chapterState = storyChapterState(
                chapterCount = 1,
                releaseTargets = listOf(target),
                readerAvailabilityResolved = true,
            ),
        )

        compose.onNodeWithTag("story-find-source").assertIsDisplayed()
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun cachedError() = capture(
        fixture(StorySection.SOURCES).copy(failure = StoryRefreshFailure("catalog.offline", true)),
        "cached-error.png",
    )

    private fun setStoryContent(
        state: StoryUiState,
        onRefresh: () -> Unit = {},
        chapterState: ChapterListUiState? = chapterFixture(),
    ) {
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                StoryScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onSourceSelected = { _, _ -> },
                    mappingState = mappingFixture(),
                    chapterState = chapterState,
                )
            }
        }
    }

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
                        onRefresh = {},
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

private fun chapterFixture(): ChapterListUiState {
    val storyId = StoryId("moonlit-archive")
    val target = ReaderTarget(storyId, CanonicalChapterId("chapter-12"), ChapterReleaseId("release-12"))
    val chapters = listOf(
        ChapterItemUiModel(
            id = CanonicalChapterId("chapter-12"),
            label = "Chapter 12",
            tombstoned = false,
            releases = listOf(
                ChapterReleaseUiModel(
                    ChapterReleaseId("release-12"),
                    PluginId("mangadex"),
                    "MangaDex",
                    "English",
                    12L,
                    ChapterCapabilityState.SUPPORTED,
                    ChapterCapabilityState.SUPPORTED,
                ),
            ),
            title = "The Locked Constellation",
        ),
        ChapterItemUiModel(
            id = CanonicalChapterId("chapter-11"),
            label = "Chapter 11",
            tombstoned = false,
            releases = emptyList(),
            title = "A Fox at Dawn",
        ),
    )
    return storyChapterState(
        chapters = chapters,
        chapterCount = 2,
        readableTargets = listOf(target),
        downloadableTargets = listOf(target),
        releaseTargets = listOf(target),
        readerAvailabilityResolved = true,
    )
}

private fun storyChapterState(
    chapters: List<ChapterItemUiModel> = emptyList(),
    chapterCount: Int,
    readableTargets: List<ReaderTarget> = emptyList(),
    downloadableTargets: List<ReaderTarget> = emptyList(),
    releaseTargets: List<ReaderTarget> = emptyList(),
    readerAvailabilityResolved: Boolean = true,
): ChapterListUiState = ChapterListUiState(
    storyId = StoryId("moonlit-archive"),
    content = ContentState.Ready(
        ChapterListContent(
            chapters = chapters,
            readableTargets = readableTargets,
            downloadableTargets = downloadableTargets,
            releaseTargets = releaseTargets,
            chapterCount = chapterCount,
            readerAvailabilityResolved = readerAvailabilityResolved,
        ),
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
