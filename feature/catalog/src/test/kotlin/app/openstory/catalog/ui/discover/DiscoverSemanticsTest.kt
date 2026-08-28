package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DiscoverSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun flexibleHeaderPrioritizesSearchBesideUtilityAction() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                    onUtilityRequested = {},
                )
            }
        }

        val searchBounds = compose.onNodeWithContentDescription("Search all stories")
            .fetchSemanticsNode().boundsInRoot
        val utilityBounds = compose.onNodeWithContentDescription("Open quick access")
            .fetchSemanticsNode().boundsInRoot

        compose.onNodeWithText("Discover").assertDoesNotExist()
        assertTrue(searchBounds.top < utilityBounds.bottom && utilityBounds.top < searchBounds.bottom)
    }

    @Test
    fun pullRefreshRemainsTheRefreshContract() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(),
                    onRefresh = { refreshCalls += 1 },
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        val refreshAction = compose.onNodeWithTag("discover-pull-refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }
        assertTrue(refreshAction.action())
        assertEquals(1, refreshCalls)
    }

    @Test
    fun pullGestureRefreshesDiscover() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(),
                    onRefresh = { refreshCalls += 1 },
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithTag("discover-pull-refresh").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(1, refreshCalls)
    }

    @Test
    fun pullIndicatorStaysBelowDiscoverSafeTopInset() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(refreshing = true),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                    contentPadding = PaddingValues(top = 32.dp),
                )
            }
        }

        compose.waitForIdle()
        val indicatorTop = compose.onNodeWithTag("hikari-pull-refresh-indicator")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(indicatorTop >= 32f * density)
    }

    @Test
    fun popularPagerExposesPagePositionAndDotsOnlyForMultipleStories() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(popular = listOf(story(1), story(2))),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithTag("discover-popular-pager").assertIsDisplayed()
        compose.onNodeWithTag("discover-popular-page-indicator").assertIsDisplayed()
        compose.onNodeWithContentDescription("Popular story 1 of 2: Story 1").assertIsDisplayed()

        compose.onNodeWithTag("discover-popular-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Popular story 2 of 2: Story 2").assertIsDisplayed()
    }

    @Test
    fun popularPagerIndicatorKeepsSixteenDpEndInset() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            HikariTheme {
                DiscoverPopularPager(
                    stories = listOf(story(1), story(2)),
                    selectedContentType = ContentType.MANGA,
                    onSelected = {},
                )
            }
        }

        compose.waitForIdle()
        val pager = compose.onNodeWithTag("discover-popular-pager")
            .fetchSemanticsNode().boundsInRoot
        val indicator = compose.onNodeWithTag("discover-popular-page-indicator")
            .fetchSemanticsNode().boundsInRoot
        val endInset = pager.right - indicator.right

        assertTrue(kotlin.math.abs(endInset - (16f * density)) <= 1.5f)
    }

    @Test
    fun singlePopularStoryDoesNotRenderPageDots() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(popular = listOf(story(1))),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithTag("discover-popular-page-indicator").assertDoesNotExist()
    }

    @Test
    fun mediaSelectorFillsWidthEquallyAndLightNovelIsDisabled() {
        var selected: ContentType? = null
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = { selected = it },
                )
            }
        }

        val manga = compose.onNodeWithText("Manga")
        val lightNovel = compose.onNodeWithText("Light Novel")
        manga.assertIsSelected()
        lightNovel.assertIsNotEnabled()
        val mangaWidth = manga.fetchSemanticsNode().boundsInRoot.width
        val lightNovelWidth = lightNovel.fetchSemanticsNode().boundsInRoot.width
        assertTrue(kotlin.math.abs(mangaWidth - lightNovelWidth) <= 1.5f)

        manga.performClick()
        assertEquals(ContentType.MANGA, selected)
    }

    @Test
    fun latestCardRendersProviderFormattedReleaseLabelWithoutPrefixing() {
        compose.setContent {
            HikariTheme {
                DiscoverLatestCard(
                    item = story(1).copy(
                        latestUpdate = CatalogLatestUpdate(
                            atEpochMillis = 10L,
                            releaseLabel = "Ch. 56",
                        ),
                    ),
                    onSelected = {},
                )
            }
        }

        compose.onNodeWithText("Ch. 56").assertIsDisplayed()
        compose.onNodeWithText("Ch. Ch. 56").assertDoesNotExist()
    }

    @Test
    fun latestIsCappedAtNineAndTopRatedAtFive() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(
                        latest = (1..10).map(::story),
                        top = (1..6).map(::story),
                    ),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithTag("discover-list").performScrollToIndex(3)
        compose.onNodeWithTag("discover-latest-item-story-9", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("discover-latest-item-story-10", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("discover-list").performScrollToIndex(8)
        compose.onNodeWithTag("discover-top-rated-rank-5", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("discover-top-rated-rank-6", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun topRatedRowExposesOneCoherentRankingDescription() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(top = listOf(story(1))),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Rank 1, Story 1, rating 9.1 out of 10, Action · Fantasy, Ongoing",
        ).assertIsDisplayed()
    }

    @Test
    fun initialLoadingUsesLayoutShapedContentButCachedRefreshKeepsRealContent() {
        var state by mutableStateOf(pendingState())
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = state,
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }
        compose.onNodeWithTag("discover-loading").assertIsDisplayed()

        compose.runOnIdle {
            state = semanticState(popular = listOf(story(1)), refreshing = true)
        }
        compose.onNodeWithTag("discover-loading").assertDoesNotExist()
        compose.onNodeWithTag("discover-popular-pager").assertIsDisplayed()
    }


    @Test
    fun blockingFailureRetryTargetsContentBoundaryNotManualRefresh() {
        var contentRetryCalls = 0
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = DiscoverUiState(
                        content = ContentState.Failed(
                            CatalogUiFailure("catalog.home.observe_exception", retryable = true),
                        ),
                    ),
                    onRefresh = { refreshCalls += 1 },
                    onRetryContent = { contentRetryCalls += 1 },
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithText("Discover unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, contentRetryCalls)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun noEnabledProvidersUsesSetupCopyWithoutGenericRetry() {
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(noContentReason = DiscoverNoContentReason.NO_ENABLED_PROVIDERS),
                    onRefresh = {},
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithText("No catalog sources enabled").assertIsDisplayed()
        compose.onNodeWithText("Enable a catalog source before discovering stories.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun observationRetryRemainsIndependentWhileManualRefreshIsInProgress() {
        var observationRetryCalls = 0
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(
                        popular = listOf(story(1)),
                        refreshing = true,
                        observationIssue = CatalogUiFailure("catalog.home.ranking_exception", retryable = true),
                    ),
                    onRefresh = { refreshCalls += 1 },
                    onRetryContent = {},
                    onRetryObservation = { observationRetryCalls += 1 },
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, observationRetryCalls)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun completedEmptyStateIsExplicitAndRetryRemainsNonBlocking() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                DiscoverScreen(
                    state = semanticState(
                        refreshFailure = CatalogUiFailure("catalog.offline", retryable = true),
                    ),
                    onRefresh = { refreshCalls += 1 },
                    onRetryContent = {},
                    onRetryObservation = {},
                    onSearch = {},
                    onStorySelected = {},
                    onContentTypeSelected = {},
                )
            }
        }

        compose.onNodeWithText("Nothing to discover yet").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, refreshCalls)
    }
}

private fun semanticState(
    popular: List<DiscoverStoryItem> = emptyList(),
    latest: List<DiscoverStoryItem> = emptyList(),
    top: List<DiscoverStoryItem> = emptyList(),
    refreshing: Boolean = false,
    refreshFailure: CatalogUiFailure? = null,
    observationIssue: CatalogUiFailure? = null,
    noContentReason: DiscoverNoContentReason = DiscoverNoContentReason.EMPTY_FEED,
): DiscoverUiState = DiscoverUiState(
    content = ContentState.Ready(
        DiscoverContent(
            selectedContentType = ContentType.MANGA,
            mediaTypeOptions = defaultDiscoverMediaTypeOptions,
            popular = popular,
            latestUpdates = latest,
            topRated = top,
            noContentReason = noContentReason.takeIf { popular.isEmpty() && latest.isEmpty() && top.isEmpty() },
        ),
    ),
    refresh = RefreshState(inProgress = refreshing, failure = refreshFailure),
    observationIssue = observationIssue,
)

private fun pendingState(): DiscoverUiState = DiscoverUiState(content = ContentState.Pending)

private fun story(index: Int): DiscoverStoryItem = DiscoverStoryItem(
    storyId = StoryId("story-$index"),
    title = "Story $index",
    coverUrl = null,
    contentType = ContentType.MANGA,
    score = Score(9.0 + index / 10.0, 10.0),
    genres = listOf("Action", "Fantasy"),
    publicationStatus = PublicationStatus.ONGOING,
    latestUpdate = CatalogLatestUpdate(1_000L + index, index.toString()),
)
