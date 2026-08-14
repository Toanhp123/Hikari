package app.openstory.designsystem.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HikariScrollableHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun topLevelHeaderScrollsAwayWithItsContent() {
        compose.setContent {
            HikariTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("scrolling-content")) {
                    item {
                        HikariTopLevelHeader(
                            title = "Home",
                            action = {
                                Text(
                                    "HK",
                                    Modifier.semantics { contentDescription = "Open quick access" },
                                )
                            },
                        )
                    }
                    items((1..30).toList()) { Text("Story $it") }
                }
            }
        }

        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithContentDescription("Open quick access").assertExists()
        compose.onNodeWithTag("scrolling-content").performScrollToIndex(30)
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open quick access").assertDoesNotExist()
    }

    @Test
    fun flexibleHeaderUsesContentInsteadOfDestinationTitle() {
        compose.setContent {
            HikariTheme {
                HikariTopLevelHeader(
                    title = null,
                    content = { Text("Search all stories") },
                    action = { Text("HK") },
                )
            }
        }

        compose.onNodeWithText("Search all stories").assertExists()
        compose.onNodeWithText("Discover").assertDoesNotExist()
    }
}
