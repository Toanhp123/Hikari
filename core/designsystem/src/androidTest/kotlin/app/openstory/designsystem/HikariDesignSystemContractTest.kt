package app.openstory.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.control.HikariSegmentedControl
import app.openstory.designsystem.control.HikariSegmentedOption
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.designsystem.theme.hikariSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class HikariDesignSystemContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeKeepsWindowBackgroundsAndStableSpacing() {
        var lightBackground = Color.Unspecified
        var darkBackground = Color.Unspecified
        var firstSpacing: Any? = null
        var secondSpacing: Any? = null

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                lightBackground = MaterialTheme.colorScheme.background
                firstSpacing = MaterialTheme.hikariSpacing
            }
            HikariTheme(darkTheme = true) {
                darkBackground = MaterialTheme.colorScheme.background
                secondSpacing = MaterialTheme.hikariSpacing
            }
        }

        composeRule.runOnIdle {
            assertEquals(Color.White, lightBackground)
            assertEquals(Color.Black, darkBackground)
            assertSame(firstSpacing, secondSpacing)
        }
    }

    @Test
    fun segmentedControlSelectsOneOptionAndIgnoresDisabledOption() {
        var selected = "manga"
        var dispatches = 0
        val options = listOf(
            HikariSegmentedOption("manga", "Manga"),
            HikariSegmentedOption("novel", "Light Novel", enabled = false),
        )

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariSegmentedControl(
                    options = options,
                    selectedKey = selected,
                    onSelected = {
                        selected = it
                        dispatches += 1
                    },
                )
            }
        }

        composeRule.onNodeWithText("Manga").assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Light Novel").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals("manga", selected)
            assertEquals(0, dispatches)
        }
    }

    @Test
    fun sharedStatePrimitivesExposeOnlyTheirDeclaredSemantics() {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariSectionHeader("Popular")
                HikariSkeleton(
                    modifier = Modifier.size(80.dp).testTag("skeleton"),
                    shape = MaterialTheme.shapes.medium,
                )
                HikariEmptyState(title = "Nothing here", body = "Try another medium")
            }
        }

        composeRule.onNodeWithText("Popular").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithTag("skeleton").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo),
        )
        composeRule.onNodeWithText("Nothing here").assertHasNoClickAction()
    }

    @Test
    fun feedbackActionsDispatchOnlyWhenEnabled() {
        var enabledDispatches = 0
        var disabledDispatches = 0

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariErrorState(
                    title = "Could not load",
                    actionLabel = "Try again",
                    onAction = { enabledDispatches += 1 },
                )
                HikariInlineFeedback(
                    message = "Refresh failed",
                    actionLabel = "Retry later",
                    actionEnabled = false,
                    onAction = { disabledDispatches += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithText("Retry later").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, enabledDispatches)
            assertEquals(0, disabledDispatches)
        }
    }

    @Test
    fun idlePullRefreshAccessibilityActionDispatches() {
        var dispatches = 0

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariPullToRefresh(
                    refreshing = false,
                    onRefresh = { dispatches += 1 },
                    modifier = Modifier.testTag("refresh"),
                ) { Box(Modifier.size(80.dp)) }
            }
        }

        val idleActions = composeRule.onNodeWithTag("refresh")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Refresh"), idleActions.map { it.label })
        assertTrue(idleActions.single().action())
        composeRule.runOnIdle { assertEquals(1, dispatches) }
    }

    @Test
    fun refreshingPullRefreshAccessibilityActionDoesNotDispatch() {
        var dispatches = 0

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariPullToRefresh(
                    refreshing = true,
                    onRefresh = { dispatches += 1 },
                    modifier = Modifier.testTag("refreshing"),
                ) { Box(Modifier.size(80.dp)) }
            }
        }

        val refreshing = composeRule.onNodeWithTag("refreshing").fetchSemanticsNode().config
        assertEquals("Refreshing", refreshing[SemanticsProperties.StateDescription])
        assertFalse(refreshing[SemanticsActions.CustomActions].single().action())
        composeRule.runOnIdle { assertEquals(0, dispatches) }
    }

    @Test
    fun disabledPullRefreshExposesNoAccessibilityAction() {
        var dispatches = 0

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariPullToRefresh(
                    refreshing = false,
                    enabled = false,
                    onRefresh = { dispatches += 1 },
                    modifier = Modifier.testTag("disabled"),
                ) { Box(Modifier.size(80.dp)) }
            }
        }

        val disabled = composeRule.onNodeWithTag("disabled").fetchSemanticsNode().config
        assertFalse(disabled.contains(SemanticsActions.CustomActions))
        composeRule.runOnIdle { assertEquals(0, dispatches) }
    }

    @Test
    fun segmentedControlRejectsInvalidBoundedChoiceContracts() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                HikariSegmentedControl(
                    options = listOf(HikariSegmentedOption("manga", "Manga")),
                    selectedKey = "manga",
                    onSelected = {},
                )
            }
        }
    }

    @Test
    fun feedbackRejectsDeadActionPairs() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                HikariInlineFeedback(message = "Failed", actionLabel = "Retry")
            }
        }
    }
}
