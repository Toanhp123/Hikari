package app.openstory.catalog.feature.discover

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoverScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentUsesOneVerticalScrollOwnerAndSemanticSectionOrder() {
        setContent(contentState())

        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        ).assertCountEquals(1)

        assertTraversalIndex(CatalogSectionKind.POPULAR, 0f)
        assertTraversalIndex(CatalogSectionKind.LATEST_UPDATES, 1f)
        assertTraversalIndex(CatalogSectionKind.TOP_RATED, 2f)
    }

    @Test
    fun emptySemanticSectionIsOmittedAndStableCardTagsRemainClickable() {
        val state = contentState().copy(
            content = DiscoverContentState.Content(
                sections = listOf(
                    section(CatalogSectionKind.POPULAR, 2),
                    section(CatalogSectionKind.TOP_RATED, 2),
                ),
                refreshing = false,
                issue = null,
            ),
        )
        setContent(state)

        composeRule.onAllNodesWithTag(
            DiscoverTestTags.section(CatalogSectionKind.LATEST_UPDATES),
        ).assertCountEquals(0)
        val firstCard = (state.content as DiscoverContentState.Content).sections.first().cards.first()
        composeRule.onNodeWithTag(
            DiscoverTestTags.card(CatalogSectionKind.POPULAR, firstCard.ref),
        ).assertHasClickAction()
    }

    @Test
    fun loadingUsesSectionShapedSkeletonGeometry() {
        setContent(contentState().copy(content = DiscoverContentState.NoContentLoading))

        composeRule.onNodeWithTag(DiscoverTestTags.POPULAR_SKELETON)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(100.dp)
            .assertHeightIsAtLeast(130.dp)
        composeRule.onNodeWithTag(DiscoverTestTags.ROOT)
            .performScrollToNode(hasTestTag(DiscoverTestTags.LATEST_SKELETON))
        composeRule.onNodeWithTag(DiscoverTestTags.LATEST_SKELETON)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(DiscoverVisualMetrics.RecommendedCoverWidth)
            .assertHeightIsEqualTo(DiscoverVisualMetrics.RecommendedCoverHeight)
        composeRule.onNodeWithTag(DiscoverTestTags.ROOT)
            .performScrollToNode(hasTestTag(DiscoverTestTags.TOP_RATED_SKELETON))
        composeRule.onNodeWithTag(DiscoverTestTags.TOP_RATED_SKELETON)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(220.dp)
            .assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun mediaControlsAreEnabledAndCardsExposeAccessibleLabelsWithinPolicyBound() {
        val state = contentState()
        setContent(state)

        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.MANGA))
            .assertIsEnabled()
        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.LIGHT_NOVEL))
            .assertIsEnabled()
        val content = state.content as DiscoverContentState.Content
        val firstCard = content.sections.first().cards.first()
        composeRule.onNodeWithTag(DiscoverTestTags.card(CatalogSectionKind.POPULAR, firstCard.ref))
            .assertContentDescriptionEquals(firstCard.title)
        assertTrue(content.sections.sumOf { it.cards.size } <= 19)
    }

    @Test
    fun mediaDestinationNavHasMangaHomeAndLightNovelTabsAndOneSelected() {
        setContent(contentState())

        composeRule.onAllNodesWithTag(DiscoverTestTags.MEDIA_NAV).assertCountEquals(1)
        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.MANGA))
            .assertIsEnabled()
            .assertIsSelected()
        composeRule.onNodeWithTag(DiscoverTestTags.NAV_HOME)
            .assertIsEnabled()
            .assertIsNotSelected()
        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.LIGHT_NOVEL))
            .assertIsEnabled()
            .assertIsNotSelected()
    }

    @Test
    fun mediaDestinationNavDispatchesOnlyForADifferentDestination() {
        val selections = mutableListOf<CatalogMediaType>()
        setContent(contentState(), onMediaSelected = selections::add)

        composeRule.onNodeWithTag(DiscoverTestTags.NAV_HOME).performClick()
        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.MANGA))
            .assertIsSelected()
        composeRule.onNodeWithTag(DiscoverTestTags.NAV_HOME).assertIsNotSelected()
        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.MANGA))
            .performClick()
        composeRule.onNodeWithTag(DiscoverTestTags.mediaDestination(CatalogMediaType.LIGHT_NOVEL))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(CatalogMediaType.LIGHT_NOVEL), selections)
        }
    }

    @Test
    fun mediaDestinationNavRemainsDisplayedAndClearsFinalTopRatedRow() {
        setContent(contentState())

        composeRule.onNodeWithTag(DiscoverTestTags.ROOT)
            .performScrollToNode(hasTestTag(DiscoverTestTags.FINAL_TOP_RATED_ROW))
        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val navBounds = composeRule.onNodeWithTag(DiscoverTestTags.MEDIA_NAV)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val finalRowBounds = composeRule.onNodeWithTag(DiscoverTestTags.FINAL_TOP_RATED_ROW)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Final row must clear the floating media navigation", finalRowBounds.bottom <= navBounds.top)
    }

    @Test
    fun headerUsesSelectedMediaAsPageIdentityWithoutDeveloperCopy() {
        setContent(contentState().copy(selectedMediaType = CatalogMediaType.LIGHT_NOVEL))

        composeRule.onNodeWithTag(DiscoverTestTags.PAGE_IDENTITY)
            .assertIsDisplayed()
            .assertTextEquals("Discover")
        composeRule.onNodeWithText("Amazing stories await you.").assertIsDisplayed()
        composeRule.onNodeWithText("Three distinct signals. One deliberately bounded shelf.")
            .assertDoesNotExist()
    }

    @Test
    fun sectionsUseDistinctArtworkFirstSilhouettes() {
        val state = contentState()
        setContent(state)
        val content = state.content as DiscoverContentState.Content
        val popular = content.sections.first { it.kind == CatalogSectionKind.POPULAR }.cards.first()
        val latest = content.sections.first { it.kind == CatalogSectionKind.LATEST_UPDATES }.cards.first()
        val topRated = content.sections.first { it.kind == CatalogSectionKind.TOP_RATED }.cards.first()

        composeRule.onNodeWithTag(
            DiscoverTestTags.card(CatalogSectionKind.POPULAR, popular.ref),
        ).assertWidthIsEqualTo(DiscoverVisualMetrics.TrendingCoverWidth)
            .assertHeightIsAtLeast(DiscoverVisualMetrics.TrendingCoverHeight)

        composeRule.onNodeWithTag(
            DiscoverTestTags.card(CatalogSectionKind.LATEST_UPDATES, latest.ref),
        ).assertWidthIsEqualTo(DiscoverVisualMetrics.RecommendedCoverWidth)
            .assertHeightIsAtLeast(DiscoverVisualMetrics.RecommendedCoverHeight)

        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).performScrollToNode(
            hasTestTag(DiscoverTestTags.card(CatalogSectionKind.TOP_RATED, topRated.ref)),
        )
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("8.0").assertIsDisplayed()
    }

    @Test
    fun conceptOnlyActionsAreNonInteractiveChromeOrOmitted() {
        setContent(contentState())

        composeRule.onNodeWithContentDescription("Search").assertHasNoClickAction()
        composeRule.onAllNodesWithText("See All")[0].assertHasNoClickAction()
        listOf("Read", "Add to Library", "Chapters", "Bookmark", "Explore", "Library", "Profile").forEach { copy ->
            composeRule.onNodeWithText(copy, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun durableContentRoutesRefreshAndRetryToDistinctIntents() {
        var refreshCalls = 0
        var retryCalls = 0
        val state = contentState().copy(
            content = (contentState().content as DiscoverContentState.Content).copy(
                issue = CatalogIssueUi(CatalogIssueKind.ACQUISITION_FAILED, retryable = true),
            ),
        )
        setContent(state, onRefresh = { refreshCalls += 1 }, onRetry = { retryCalls += 1 })

        val refreshNode = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertCountEquals(1)[0]
        val action = refreshNode.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertTrue(action.action())
        composeRule.onNodeWithText("Try again").performClick()

        composeRule.runOnIdle {
            assertTrue(refreshCalls == 1)
            assertTrue(retryCalls == 1)
        }
    }

    @Test
    fun absentContentDoesNotExposeRefreshAction() {
        setContent(contentState().copy(content = DiscoverContentState.NoContentLoading))

        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertCountEquals(0)
    }

    @Test
    fun successfulEmptyUsesPullRefreshWithoutManualButton() {
        setContent(contentState().copy(content = DiscoverContentState.Empty()))

        composeRule.onNodeWithText("Refresh").assertDoesNotExist()
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertCountEquals(1)
    }

    private fun setContent(
        state: DiscoverUiState,
        onMediaSelected: (CatalogMediaType) -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                DiscoverScreen(
                    state = state,
                    listState = rememberLazyListState(),
                    onMediaSelected = onMediaSelected,
                    onStorySelected = { _, _ -> },
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                )
            }
        }
    }

    private fun assertTraversalIndex(kind: CatalogSectionKind, expected: Float) {
        val tag = DiscoverTestTags.section(kind)
        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).performScrollToNode(hasTestTag(tag))
        val actual = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode().config[SemanticsProperties.TraversalIndex]
        assertTrue("Expected traversal index $expected but was $actual", actual == expected)
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("discover-screen-test")

        fun contentState() = DiscoverUiState(
            selectedMediaType = CatalogMediaType.MANGA,
            content = DiscoverContentState.Content(
                sections = listOf(
                    section(CatalogSectionKind.POPULAR, 5),
                    section(CatalogSectionKind.LATEST_UPDATES, 9),
                    section(CatalogSectionKind.TOP_RATED, 5),
                ),
                refreshing = false,
                issue = null,
            ),
        )

        fun section(kind: CatalogSectionKind, size: Int) = DiscoverSectionUi(
            kind = kind,
            cards = List(size) { position -> card(kind, position) },
        )

        fun card(kind: CatalogSectionKind, position: Int): DiscoverCardUi {
            val sourceStoryId = "${kind.name.lowercase()}-$position"
            val sourceKey = SourceStoryKey(SOURCE_KEY, sourceStoryId)
            return DiscoverCardUi(
                ref = StorySourceRef(
                    storyId = SourceStoryIdV1.derive(sourceKey),
                    catalogSourceKey = SOURCE_KEY,
                    sourceStoryId = sourceStoryId,
                ),
                title = "${kind.name} story ${position + 1}",
                coverAssetKey = null,
                ratingLabel = if (kind == CatalogSectionKind.TOP_RATED) "8.${position}" else null,
                supportingLabel = if (kind == CatalogSectionKind.LATEST_UPDATES) "Updated today" else null,
            )
        }
    }
}
