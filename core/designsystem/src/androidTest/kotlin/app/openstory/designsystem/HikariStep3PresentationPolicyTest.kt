package app.openstory.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariPosterCard
import app.openstory.designsystem.content.HikariPosterGeometry
import app.openstory.designsystem.content.HikariPosterRail
import app.openstory.designsystem.content.HikariPosterSkeleton
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariSearchField
import app.openstory.designsystem.navigation.HikariFloatingDestinationNav
import app.openstory.designsystem.navigation.HikariFloatingDestinationNavItem
import app.openstory.designsystem.presentation.HikariFocusedHeader
import app.openstory.designsystem.presentation.HikariInfoRow
import app.openstory.designsystem.presentation.HikariValueRow
import app.openstory.designsystem.sheet.HikariChoiceSheet
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HikariStep3PresentationPolicyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun interactivePrimitivesEnforceTouchTargetsAndDispatchCallerActions() {
        val events = mutableListOf<String>()

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                var query by remember { mutableStateOf("") }
                Column {
                    HikariIconAction(
                        onClick = { events += "icon" },
                        contentDescription = "Open filters",
                        modifier = Modifier.size(20.dp).testTag("icon-action"),
                    ) { Box(Modifier.size(18.dp)) }
                    HikariFilterChip(
                        selected = false,
                        onClick = { events += "filter" },
                        label = "Manga",
                        modifier = Modifier.testTag("filter-chip"),
                    )
                    HikariSearchField(
                        value = query,
                        onValueChange = {
                            query = it
                            events += it
                        },
                        label = "Search library",
                        onSearch = { events += "search:$it" },
                        modifier = Modifier.testTag("search-field"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("icon-action")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Open filters").performClick()
        composeRule.onNodeWithTag("filter-chip").assertHeightIsAtLeast(48.dp).performClick()
        val searchField = composeRule.onNodeWithTag("search-field")
        searchField.performTextInput("saved")
        searchField.performImeAction()

        composeRule.runOnIdle {
            assertEquals(listOf("icon", "filter", "saved", "search:saved"), events)
        }
    }

    @Test
    fun destinationNavigationKeepsSelectionAndCallbacksCallerOwned() {
        val selections = mutableListOf<String>()

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariFloatingDestinationNav {
                    HikariFloatingDestinationNavItem(
                        label = "Home",
                        selected = true,
                        onClick = { selections += "home" },
                    )
                    HikariFloatingDestinationNavItem(
                        label = "Manga",
                        selected = false,
                        onClick = { selections += "manga" },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Manga").performClick()
        composeRule.runOnIdle { assertEquals(listOf("manga"), selections) }
    }

    @Test
    fun focusedHeaderAndRowsKeepDistinctSemantics() {
        var valueClicks = 0

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                Column {
                    HikariFocusedHeader(
                        title = "Library",
                        subtitle = "Saved stories",
                        trailingContent = { Text("Edit") },
                    )
                    HikariValueRow(
                        label = "Theme",
                        value = "System",
                        onClick = { valueClicks += 1 },
                    )
                    HikariInfoRow(label = "Version", value = "2.0")
                }
            }
        }

        composeRule.onNodeWithText("Library").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )
        composeRule.onNodeWithText("Theme").assertHasClickAction().performClick()
        composeRule.onNodeWithText("Version").assertHasNoClickAction()
        composeRule.runOnIdle { assertEquals(1, valueClicks) }
    }

    @Test
    fun posterRailMaterializesFeatureOwnedContent() {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariPosterRail(modifier = Modifier.size(240.dp)) {
                    items(listOf("First", "Second")) { label ->
                        Text(label, Modifier.testTag("rail-$label"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("rail-First").assertIsDisplayed()
        composeRule.onNodeWithTag("rail-Second").assertIsDisplayed()
    }

    @Test
    fun posterGeometryKeepsContentAndSkeletonArtworkAligned() {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariPosterCard(
                    title = "Content",
                    supportingText = null,
                    onClick = {},
                    modifier = Modifier.width(104.dp),
                    geometry = HikariPosterGeometry.Standard,
                    artworkModifier = Modifier.testTag("content-artwork"),
                )
                HikariPosterSkeleton(
                    modifier = Modifier.width(104.dp),
                    geometry = HikariPosterGeometry.Standard,
                    artworkModifier = Modifier.testTag("skeleton-artwork"),
                )
            }
        }

        composeRule.onNodeWithTag("content-artwork", useUnmergedTree = true)
            .assertWidthIsAtLeast(104.dp)
            .assertHeightIsAtLeast(150.dp)
        composeRule.onNodeWithTag("skeleton-artwork", useUnmergedTree = true)
            .assertWidthIsAtLeast(104.dp)
            .assertHeightIsAtLeast(150.dp)
    }

    @Test
    fun choiceSheetKeepsOptionIdentityAndSelectionCallerOwned() {
        val selections = mutableListOf<String>()

        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                HikariChoiceSheet(
                    title = "Theme",
                    options = listOf("System", "Dark"),
                    selectedOption = "System",
                    optionLabel = { it },
                    onOptionSelected = { selections += it },
                    onDismissRequest = {},
                )
            }
        }

        composeRule.onNodeWithText("System").assertIsSelected()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.runOnIdle { assertEquals(listOf("Dark"), selections) }
    }
}
