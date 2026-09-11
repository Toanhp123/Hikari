package app.openstory.catalog.feature.story

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.feature.CatalogNavigationState
import app.openstory.catalog.feature.CatalogRoute
import app.openstory.catalog.feature.CatalogRouteCodec
import app.openstory.catalog.feature.CatalogRouteSaver
import app.openstory.catalog.feature.CatalogScreen
import app.openstory.catalog.feature.CatalogScreenActions
import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverSectionUi
import app.openstory.catalog.feature.discover.DiscoverTestTags
import app.openstory.catalog.feature.discover.DiscoverUiState
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryRouteRestorationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validStoryRouteRestoresValidatedIdentityAndCoverRevision() {
        val restorationTester = StateRestorationTester(composeRule)
        var observed: CatalogRoute? = null
        restorationTester.setContent {
            val route = rememberSaveable(stateSaver = CatalogRouteSaver) {
                mutableStateOf<CatalogRoute>(CatalogRoute.Story(REF, COVER_KEY))
            }
            observed = route.value
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertEquals(CatalogRoute.Story(REF, COVER_KEY), observed)
    }

    @Test
    fun malformedSavedTripleReturnsToDiscoverWithoutStartingStoryAcquisition() {
        var storyAcquisitionStarted = false
        val restored = CatalogRouteCodec.restore(
            CatalogRouteCodec.save(CatalogRoute.Story(REF, null)).replace("story-17", "story-18"),
        )

        composeRule.setContent {
            MaterialTheme {
                CatalogScreen(
                    route = restored,
                    discoverListState = remember { LazyListState() },
                    discoverState = DiscoverUiState(content = DiscoverContentState.Empty()),
                    storyState = null,
                    actions = CatalogScreenActions(
                        onMediaSelected = {},
                        onStorySelected = { _, _ -> storyAcquisitionStarted = true },
                        onDiscoverRefresh = {},
                        onDiscoverRetry = {},
                        onStoryRetry = {},
                        onBack = {},
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).assertIsDisplayed()
        assertEquals(CatalogRoute.Discover, restored)
        assertFalse(storyAcquisitionStarted)
    }

    @Test
    fun restoredStoryDoesNotExposeContentUntilPinFirstActivationCompletes() {
        val pinGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val runtime = GateStoryRuntime(pinGate, events)
        val viewModel = StoryDetailViewModel(runtime)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            LaunchedEffect(Unit) {
                viewModel.open(REF, COVER_KEY) { events += "route-visible" }
            }
            MaterialTheme {
                CatalogScreen(
                    route = CatalogRoute.Story(REF, COVER_KEY),
                    discoverListState = remember { LazyListState() },
                    discoverState = null,
                    storyState = state,
                    actions = CatalogScreenActions(
                        onMediaSelected = {},
                        onStorySelected = { _, _ -> },
                        onDiscoverRefresh = {},
                        onDiscoverRetry = {},
                        onStoryRetry = viewModel::retry,
                        onBack = viewModel::closeDestination,
                    ),
                )
            }
        }

        composeRule.waitUntil { events == listOf("activation-entered") }
        composeRule.onAllNodesWithText("Story 17").assertCountEquals(0)

        pinGate.complete(Unit)
        composeRule.waitUntil { events.size == 3 }
        composeRule.onNodeWithText("Story 17").assertIsDisplayed()
        assertEquals(listOf("activation-entered", "pin-registered", "route-visible"), events)
    }

    @Test
    fun backReturnsToDiscoverWithTheSameScrollStateInstance() {
        val listState = LazyListState(firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 29)
        val navigation = CatalogNavigationState(listState)
        var released = false

        composeRule.setContent {
            MaterialTheme {
                CatalogScreen(
                    route = navigation.route,
                    discoverListState = navigation.discoverListState,
                    discoverState = SCROLLABLE_DISCOVER_STATE,
                    storyState = ACTIVE_STATE,
                    actions = CatalogScreenActions(
                        onMediaSelected = {},
                        onStorySelected = { _, _ -> },
                        onDiscoverRefresh = {},
                        onDiscoverRetry = {},
                        onStoryRetry = {},
                        onBack = {
                            navigation.showDiscover()
                            released = true
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).assertIsDisplayed()
        assertEquals(2, navigation.discoverListState.firstVisibleItemIndex)
        assertEquals(29, navigation.discoverListState.firstVisibleItemScrollOffset)

        composeRule.runOnIdle { navigation.showStory(REF, COVER_KEY) }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).assertIsDisplayed()
        assertTrue(released)
        assertTrue(listState === navigation.discoverListState)
        assertEquals(2, navigation.discoverListState.firstVisibleItemIndex)
        assertEquals(29, navigation.discoverListState.firstVisibleItemScrollOffset)
    }

    private class GateStoryRuntime(
        private val pinGate: CompletableDeferred<Unit>,
        private val events: MutableList<String>,
    ) : StoryDetailRuntime {
        private val states = MutableStateFlow(
            StoryDetailSessionState(PROJECTION, CatalogAcquisitionStatus.Success),
        )

        override suspend fun activate(ref: StorySourceRef): StoryDetailRuntimeActivation {
            events += "activation-entered"
            pinGate.await()
            events += "pin-registered"
            return StoryDetailRuntimeActivation.Available(
                states = states,
                retry = { CatalogAcquisitionResult.Success },
                release = {},
            )
        }
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("story-restoration-test")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
        val COVER_KEY = CoverAssetKey(
            REF.storyId,
            CoverRevision("cover:v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
        )
        val PROJECTION = StoryDetailProjection(
            ref = REF,
            summary = StorySummaryProjection(
                ref = REF,
                title = "Story 17",
                contentType = CatalogMediaType.MANGA,
                sourceVersion = "fixture-v1",
                coverLocator = null,
                coverAssetKey = COVER_KEY,
                rating = null,
                publicationStatusSummary = "Ongoing",
                latestUpdateEpochMs = 17L,
            ),
            detail = StoryRichDetailProjection(
                description = "Metadata only",
                authors = emptyList(),
                artists = emptyList(),
                genres = emptyList(),
                publicationStatus = "Ongoing",
                language = "English",
            ),
            detailProvenance = AcquisitionProvenance(SOURCE_KEY, "fixture-v1", 18L),
        )
        val ACTIVE_STATE = StoryDetailUiState(
            ref = REF,
            summary = StorySummaryUi(
                title = "Story 17",
                contentType = CatalogMediaType.MANGA,
                coverAssetKey = COVER_KEY,
                ratingLabel = null,
                publicationStatus = "Ongoing",
                latestUpdateLabel = null,
            ),
            detail = StoryDetailUi("Metadata only", emptyList(), emptyList(), emptyList(), "Ongoing", "English"),
            detailLoading = false,
            issue = null,
            destinationActive = true,
        )
        val SCROLLABLE_DISCOVER_STATE = DiscoverUiState(
            content = DiscoverContentState.Content(
                sections = CatalogSectionKind.entries.map { kind ->
                    DiscoverSectionUi(
                        kind = kind,
                        cards = List(
                            size = when (kind) {
                                CatalogSectionKind.POPULAR -> 1
                                CatalogSectionKind.LATEST_UPDATES -> 9
                                CatalogSectionKind.TOP_RATED -> 5
                            },
                        ) { position ->
                            val sourceStoryId = "scroll-${kind.name.lowercase()}-$position"
                            val sourceKey = SourceStoryKey(SOURCE_KEY, sourceStoryId)
                            DiscoverCardUi(
                                ref = StorySourceRef(
                                    storyId = SourceStoryIdV1.derive(sourceKey),
                                    catalogSourceKey = SOURCE_KEY,
                                    sourceStoryId = sourceStoryId,
                                ),
                                title = "${kind.name} story ${position + 1}",
                                coverAssetKey = null,
                                ratingLabel = null,
                                supportingLabel = null,
                            )
                        },
                    )
                },
                refreshing = false,
                issue = null,
            ),
        )
    }
}
