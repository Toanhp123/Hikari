package app.openstory.catalog.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.openstory.common.id.PluginId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.matching.ContentMatchDecision
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class MappingSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun evidenceAndApprovalRejectionActionsAreVisible() {
        var approved: Pair<PluginId, String>? = null
        var rejected: Pair<PluginId, String>? = null
        compose.setContent {
            HikariTheme {
                MappingItemsTestHost(
                    state = stateWithCandidate(),
                    actions = MappingActions(
                        onApprove = { pluginId, sourceStoryId -> approved = pluginId to sourceStoryId },
                        onReject = { pluginId, sourceStoryId -> rejected = pluginId to sourceStoryId },
                    ),
                )
            }
        }

        compose.onAllNodesWithText("- Title 100%").assertCountEquals(1)
        compose.onAllNodesWithText("- Content type match").assertCountEquals(1)
        compose.onNodeWithText("Approve").performScrollTo().performClick()
        compose.onNodeWithText("Reject").performScrollTo().performClick()

        val expected = PluginId("org.example.reader") to "source-1"
        assertEquals(expected, approved)
        assertEquals(expected, rejected)
        compose.onNodeWithText("Approve").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Reject").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun replacementCandidateUsesExplicitReplaceAction() {
        compose.setContent {
            HikariTheme {
                MappingItemsTestHost(
                    state = stateWithCandidate(replacesSourceStoryId = "source-1"),
                    actions = MappingActions(),
                )
            }
        }

        compose.onNodeWithText("Replace").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Replaces source-1").assertIsDisplayed()
        compose.onAllNodesWithText("Approve").assertCountEquals(0)
    }

    @Test
    fun urlInputAndResolveStayUiActions() {
        val url = mutableStateOf("")
        var resolved = false
        compose.setContent {
            HikariTheme {
                MappingItemsTestHost(
                    state = MappingUiState(urlInput = url.value, loading = false),
                    actions = MappingActions(
                        onUrlChange = { value -> url.value = value },
                        onResolveUrl = { resolved = true },
                    ),
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("https://reader.example/story/1")
        assertEquals("https://reader.example/story/1", url.value)
        compose.onNodeWithText("Resolve URL").assertIsEnabled().performClick()
        assertTrue(resolved)
    }

    @Test
    fun currentProtectedMappingIsRendered() {
        compose.setContent {
            HikariTheme {
                MappingItemsTestHost(
                    state = MappingUiState(
                        mappings = listOf(
                            MappingItemUiModel(
                                pluginId = PluginId("org.example.reader"),
                                sourceStoryId = "chosen",
                                origin = ContentMappingOrigin.USER_APPROVED,
                            ),
                        ),
                    ),
                    actions = MappingActions(),
                )
            }
        }

        compose.onNodeWithText("org.example.reader").assertIsDisplayed()
        compose.onNodeWithText("chosen").assertIsDisplayed()
        compose.onNodeWithText("Approved").assertIsDisplayed()
    }
    @Test
    fun emptyMappingKeepsCopyAndActions() {
        compose.setContent {
            HikariTheme {
                MappingItemsTestHost(
                    state = MappingUiState(loading = false),
                    actions = MappingActions(),
                )
            }
        }

        compose.onNodeWithText("No reading source linked yet").assertIsDisplayed()
        compose.onNodeWithText("Find reading sources").assertIsDisplayed()
        compose.onNodeWithText("Resolve URL").assertIsDisplayed()
    }
}

@Composable
private fun MappingItemsTestHost(
    state: MappingUiState,
    actions: MappingActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        mappingItems(state, actions)
    }
}

private fun stateWithCandidate(replacesSourceStoryId: String? = null) = MappingUiState(
    candidates = listOf(
        MappingCandidateUiModel(
            pluginId = PluginId("org.example.reader"),
            sourceStoryId = "source-1",
            title = "The Story",
            sourceUrl = "https://reader.example/story/source-1",
            decision = ContentMatchDecision.REVIEW,
            score = 0.95,
            evidenceLabels = listOf("Title 100%", "Authors 100%", "Content type match"),
            fromUrl = false,
            replacesSourceStoryId = replacesSourceStoryId,
        ),
    ),
)
