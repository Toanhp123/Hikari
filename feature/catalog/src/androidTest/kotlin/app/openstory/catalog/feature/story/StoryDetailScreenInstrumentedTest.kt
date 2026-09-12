package app.openstory.catalog.feature.story

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StoryDetailScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryRemainsVisibleWithStaticMetadataSkeleton() {
        setContent(
            state = state(
                detailLoading = true,
                issue = null,
            ),
        )

        composeRule.onNodeWithText("Story 17").assertIsDisplayed()
        composeRule.onNodeWithTag("story-detail-skeleton").assertIsDisplayed()
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertCountEquals(0)
    }

    @Test
    fun retryableMetadataFailureUsesInlineRetryWithoutPullRefresh() {
        var retryCalls = 0
        setContent(
            state = state(
                detailLoading = false,
                issue = CatalogIssueUi(CatalogIssueKind.ACQUISITION_FAILED, retryable = true),
            ),
            onRetry = { retryCalls += 1 },
        )

        composeRule.onNodeWithText("Story 17").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, retryCalls) }
    }

    @Test
    fun storyHeroSummaryNullReservesPortraitIdentityGeometryWithoutFakeTitle() {
        setContent(
            state = state(detailLoading = true, issue = null).copy(summary = null),
        )

        composeRule.onNodeWithTag("story-hero-cover")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(112.dp)
            .assertHeightIsAtLeast(168.dp)
        composeRule.onNodeWithTag("story-hero-identity-skeleton").assertIsDisplayed()
        composeRule.onNodeWithText("Story cover").assertDoesNotExist()
        composeRule.onNodeWithText("H").assertDoesNotExist()
    }

    @Test
    fun storyHeroKeepsPortraitCoverAndSummaryVisibleWhileDetailLoads() {
        setContent(state(detailLoading = true, issue = null))

        composeRule.onNodeWithTag("story-hero-cover")
            .assertWidthIsAtLeast(112.dp)
            .assertHeightIsAtLeast(168.dp)
        composeRule.onNodeWithText("Story 17").assertIsDisplayed()
        composeRule.onNodeWithTag("story-detail-skeleton").assertIsDisplayed()
    }

    @Test
    fun storyMetadataGroupsAvailableFieldsWithoutInternalAuthority() {
        setContent(
            state(detailLoading = false, issue = null).copy(
                detail = StoryDetailUi(
                    description = "A bounded description",
                    authors = listOf("Author One"),
                    artists = listOf("Artist One"),
                    genres = listOf("Drama", "Mystery"),
                    publicationStatus = "Ongoing",
                    language = "English",
                ),
            ),
        )

        listOf("About", "Authors", "Artists", "Genres", "Status", "Language").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("fixture-v1").assertDoesNotExist()
        composeRule.onNodeWithText(REF.storyId.value).assertDoesNotExist()
    }

    @Test
    fun storyDetailActionsAreDisabledStubsWithoutActiveDispatches() {
        setContent(
            state(detailLoading = false, issue = null).copy(
                detail = StoryDetailUi(
                    description = null,
                    authors = emptyList(),
                    artists = emptyList(),
                    genres = emptyList(),
                    publicationStatus = null,
                    language = null,
                ),
            ),
        )

        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertCountEquals(0)
        composeRule.onNodeWithTag(StoryTestTags.ROOT)
            .performScrollToNode(hasText("Read from Chapter 1"))
        composeRule.onNodeWithText("Read from Chapter 1").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Add to Library").performScrollTo().assertIsNotEnabled()
        listOf("Refresh", "Bookmark").forEach { copy ->
            composeRule.onNodeWithText(copy, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun richDetailUnlocksBodyAndBlueprintShellsWithoutRematerializingHero() {
        val current = mutableStateOf(state(detailLoading = true, issue = null))
        val materialization = mutableListOf<String>()
        val onHeroMaterialized = { materialization += "hero" }
        val onBodyMaterialized = { materialization += "body" }
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                StoryDetailScreen(
                    state = current.value,
                    onBack = {},
                    onRetry = {},
                    onHeroMaterialized = onHeroMaterialized,
                    onBodyMaterialized = onBodyMaterialized,
                )
            }
        }

        composeRule.onNodeWithText("Read from Chapter 1").assertDoesNotExist()
        composeRule.onNodeWithText("Synopsis").assertDoesNotExist()
        composeRule.onNodeWithText("You May Also Like").assertDoesNotExist()
        var heroCountBeforeDetail = 0
        composeRule.runOnIdle {
            heroCountBeforeDetail = materialization.count { it == "hero" }
            current.value = current.value.copy(
                detail = StoryDetailUi(
                    description = "A bounded description",
                    authors = listOf("Author One"),
                    artists = listOf("Artist One"),
                    genres = listOf("Drama"),
                    publicationStatus = "Ongoing",
                    language = "English",
                ),
                detailLoading = false,
            )
        }

        composeRule.onNodeWithTag(StoryTestTags.ROOT)
            .performScrollToNode(hasText("Read from Chapter 1"))
        composeRule.onNodeWithText("Read from Chapter 1").assertIsDisplayed()
        composeRule.onNodeWithText("Synopsis").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("You May Also Like").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(heroCountBeforeDetail, materialization.count { it == "hero" })
            assertEquals(1, materialization.count { it == "body" })
        }
    }

    private fun setContent(
        state: StoryDetailUiState,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                StoryDetailScreen(state = state, onBack = {}, onRetry = onRetry)
            }
        }
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("story-detail-screen-test")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
        fun state(
            detailLoading: Boolean,
            issue: CatalogIssueUi?,
        ) = StoryDetailUiState(
            ref = REF,
            summary = StorySummaryUi(
                title = "Story 17",
                contentType = CatalogMediaType.MANGA,
                ratingLabel = "8.5 / 10",
                publicationStatus = "Ongoing",
                latestUpdateLabel = "Updated Sep 11, 2026",
            ),
            detail = null,
            detailLoading = detailLoading,
            issue = issue,
            destinationActive = true,
        )
    }
}
