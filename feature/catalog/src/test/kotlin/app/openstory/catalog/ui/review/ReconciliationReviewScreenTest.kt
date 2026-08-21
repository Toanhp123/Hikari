package app.openstory.catalog.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class ReconciliationReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun blockedReviewExplainsEvidenceAndOmitsMergeAction() {
        compose.setContent {
            HikariTheme {
                ReconciliationReviewScreen(
                    state = ReconciliationReviewUiState(
                        items = listOf(
                            ReconciliationReviewItemUiModel(
                                caseId = "case-1",
                                caseRevision = 1,
                                leftStoryId = StoryId("left"),
                                rightStoryId = StoryId("right"),
                                leftTitle = "Left story",
                                rightTitle = "Right story",
                                leftCoverUrl = null,
                                rightCoverUrl = null,
                                confidence = 0.92,
                                reasonLabels = listOf("Titles are very similar", "Different content types"),
                                mergeAllowed = false,
                                userStateImpact = 2,
                            ),
                        ),
                    ),
                    onBack = {},
                    onMerge = { _, _ -> },
                    onKeepSeparate = { _, _ -> },
                    onDefer = { _, _ -> },
                    onProtectedMappingSelected = { _, _ -> },
                    onConfirmProtectedMerge = {},
                    onDismissProtectedConflict = {},
                )
            }
        }

        compose.onNodeWithText("Review duplicates").assertIsDisplayed()
        compose.onNodeWithText("Titles are very similar").assertIsDisplayed()
        compose.onNodeWithText("Keep separate").assertIsDisplayed()
        compose.onNodeWithText("Merge").assertDoesNotExist()
    }

    @Test
    fun emptyQueueUsesDedicatedEmptyState() {
        compose.setContent {
            HikariTheme {
                ReconciliationReviewScreen(
                    state = ReconciliationReviewUiState(),
                    onBack = {},
                    onMerge = { _, _ -> },
                    onKeepSeparate = { _, _ -> },
                    onDefer = { _, _ -> },
                    onProtectedMappingSelected = { _, _ -> },
                    onConfirmProtectedMerge = {},
                    onDismissProtectedConflict = {},
                )
            }
        }

        compose.onNodeWithText("No duplicates to review").assertIsDisplayed()
    }
    @Test
    fun emptyQueueStillSurfacesLoadFailure() {
        compose.setContent {
            HikariTheme {
                ReconciliationReviewScreen(
                    state = ReconciliationReviewUiState(failureMessage = "Couldn't load duplicate reviews right now."),
                    onBack = {},
                    onMerge = { _, _ -> },
                    onKeepSeparate = { _, _ -> },
                    onDefer = { _, _ -> },
                    onProtectedMappingSelected = { _, _ -> },
                    onConfirmProtectedMerge = {},
                    onDismissProtectedConflict = {},
                )
            }
        }

        compose.onNodeWithText("Couldn't load duplicate reviews right now.").assertIsDisplayed()
        compose.onNodeWithText("No duplicates to review").assertIsDisplayed()
    }

}
