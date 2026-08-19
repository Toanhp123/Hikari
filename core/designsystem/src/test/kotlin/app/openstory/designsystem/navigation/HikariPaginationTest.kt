package app.openstory.designsystem.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HikariPaginationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nextAndPreviousActionsSelectAdjacentPages() {
        val selected = mutableListOf<Int>()
        compose.setContent {
            HikariTheme {
                HikariPagination(
                    currentPage = 2,
                    pageCount = 4,
                    onPageSelected = selected::add,
                )
            }
        }

        compose.onNodeWithText("2 / 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous page").performClick()
        compose.onNodeWithContentDescription("Next page").performClick()

        assertEquals(listOf(1, 3), selected)
    }

    @Test
    fun pageIndicatorOffersQuickPageSelection() {
        var selected = 0
        compose.setContent {
            HikariTheme {
                HikariPagination(
                    currentPage = 1,
                    pageCount = 9,
                    onPageSelected = { selected = it },
                )
            }
        }

        compose.onNodeWithText("1 / 9").performClick()
        compose.onNodeWithText("Page 5").performClick()

        assertEquals(5, selected)
    }
}
