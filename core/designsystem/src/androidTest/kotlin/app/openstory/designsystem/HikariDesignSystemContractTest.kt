package app.openstory.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.HikariSpacing
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
    fun themeAppliesR28VisualVocabularyWhileKeepingStableRootContracts() {
        var lightColors: ColorScheme? = null
        var darkColors: ColorScheme? = null
        var typography: Typography? = null
        var firstSpacing: Any? = null
        var secondSpacing: Any? = null

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                lightColors = MaterialTheme.colorScheme
                typography = MaterialTheme.typography
                firstSpacing = MaterialTheme.hikariSpacing
            }
            HikariTheme(darkTheme = true) {
                darkColors = MaterialTheme.colorScheme
                secondSpacing = MaterialTheme.hikariSpacing
            }
        }

        composeRule.runOnIdle {
            val light = requireNotNull(lightColors)
            val dark = requireNotNull(darkColors)
            val type = requireNotNull(typography)

            assertEquals(Color.White, light.background)
            assertEquals(Color.Black, dark.background)
            assertEquals(Color(0xFFC94C40), light.primary)
            assertEquals(Color(0xFFFFF9F6), light.surface)
            assertEquals(Color(0xFF211A18), light.onSurface)
            assertEquals(Color(0xFFFF8E80), dark.primary)
            assertEquals(Color(0xFF121217), dark.surface)
            assertEquals(Color(0xFF24242E), dark.surfaceVariant)
            assertEquals(Color(0xFFF5F1F0), dark.onSurface)

            assertSame(firstSpacing, secondSpacing)
            assertEquals(4.dp, HikariSpacing.space4)
            assertEquals(8.dp, HikariSpacing.space8)
            assertEquals(12.dp, HikariSpacing.space12)
            assertEquals(16.dp, HikariSpacing.space16)
            assertEquals(20.dp, HikariSpacing.space20)
            assertEquals(24.dp, HikariSpacing.space24)
            assertEquals(32.dp, HikariSpacing.space32)

            assertTextStyle(type.headlineMedium, FontFamily.Serif, FontWeight.SemiBold, 30.sp, 36.sp)
            assertTextStyle(type.headlineSmall, FontFamily.Serif, FontWeight.SemiBold, 20.sp, 26.sp)
            assertTextStyle(type.titleLarge, FontFamily.SansSerif, FontWeight.Bold, 20.sp, 26.sp)
            assertTextStyle(type.bodyMedium, FontFamily.SansSerif, FontWeight.Normal, 14.sp, 20.sp)
            assertTextStyle(type.bodySmall, FontFamily.SansSerif, FontWeight.Normal, 12.sp, 18.sp)
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
    fun feedbackRejectsDeadActionPairs() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                HikariInlineFeedback(message = "Failed", actionLabel = "Retry")
            }
        }
    }

    private fun assertTextStyle(
        style: androidx.compose.ui.text.TextStyle,
        fontFamily: FontFamily,
        fontWeight: FontWeight,
        fontSize: androidx.compose.ui.unit.TextUnit,
        lineHeight: androidx.compose.ui.unit.TextUnit,
    ) {
        assertEquals(fontFamily, style.fontFamily)
        assertEquals(fontWeight, style.fontWeight)
        assertEquals(fontSize, style.fontSize)
        assertEquals(lineHeight, style.lineHeight)
    }
}
