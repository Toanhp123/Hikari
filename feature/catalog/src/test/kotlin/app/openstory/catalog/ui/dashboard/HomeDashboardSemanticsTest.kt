package app.openstory.catalog.ui.dashboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onNodeWithText
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HomeDashboardSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shelfHeadingAndDirectionalFocusFollowVisualOrder() {
        compose.setContent { HikariTheme { HomeDashboardScreen(fixture(), {}, {}, {}) } }

        compose.onNodeWithText("Continue Reading", useUnmergedTree = true)
            .assertIsDisplayed()
        val continueCard = compose.onNodeWithContentDescription(
            "Resume The Fox of the Moonlit Archive, Chapter 12, 64 percent read",
        )
        val readingCard = compose.onNodeWithContentDescription(
            "The Fox of the Moonlit Archive. Section Reading",
        )
        continueCard.performKeyInput { pressKey(Key.Tab) }
        continueCard.assertIsFocused()
        continueCard.performKeyInput { pressKey(Key.DirectionDown) }
        readingCard.assertIsFocused()
    }
}
