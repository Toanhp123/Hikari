package app.openstory.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class DownloadIndicatorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun offlineChapterHasAccessibleIndicator() {
        compose.setContent { MaterialTheme { DownloadIndicator(availableOffline = true) } }

        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chapter available offline").assertIsDisplayed()
    }
}
