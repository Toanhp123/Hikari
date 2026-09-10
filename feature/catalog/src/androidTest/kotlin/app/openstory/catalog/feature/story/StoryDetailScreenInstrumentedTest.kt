package app.openstory.catalog.feature.story

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
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
                coverAssetKey = null,
                ratingLabel = "8.5 / 10",
                publicationStatus = "Ongoing",
            ),
            detail = null,
            detailLoading = detailLoading,
            issue = issue,
            destinationActive = true,
        )
    }
}
