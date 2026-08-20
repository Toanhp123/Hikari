package app.openstory.designsystem.layout

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HikariSearchBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editableSearchForwardsTypedQuery() {
        var query = ""
        compose.setContent {
            HikariTheme {
                HikariSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search your Library",
                    contentDescription = "Search your Library",
                )
            }
        }

        compose.onNodeWithContentDescription("Search your Library").performTextInput("moon")

        assertEquals("moon", query)
    }

    @Test
    fun editableSearchForwardsImeSearchAction() {
        var submitted = false
        compose.setContent {
            HikariTheme {
                HikariSearchBar(
                    value = "moon",
                    onValueChange = {},
                    placeholder = "Search",
                    contentDescription = "Search",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = true }),
                )
            }
        }

        compose.onNodeWithContentDescription("Search").performImeAction()

        compose.runOnIdle { assertEquals(true, submitted) }
    }

    @Test
    fun searchBarKeepsMinimumTouchTargetAtLargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2.5f)) {
                HikariTheme {
                    HikariSearchBar(
                        value = "Large text",
                        onValueChange = {},
                        placeholder = "Search all stories",
                        contentDescription = "Large font search",
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Large font search").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun readOnlySearchInvokesDestinationAction() {
        var clicked = false
        compose.setContent {
            HikariTheme {
                HikariSearchBar(
                    value = "",
                    onValueChange = {},
                    placeholder = "Search all stories",
                    contentDescription = "Search all stories",
                    readOnly = true,
                    onClick = { clicked = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Search all stories").performClick()

        assertEquals(true, clicked)
    }
}
