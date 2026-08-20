package app.openstory.designsystem.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun topLevelHeaderStaysPinnedWhileItsContentScrolls() {
        compose.setContent {
            HikariTheme {
                HikariTopLevelScaffold(
                    contentPadding = PaddingValues(top = 24.dp),
                    header = {
                        HikariTopLevelHeader(
                            title = "Home",
                            action = {
                                Text(
                                    "HK",
                                    Modifier.semantics { contentDescription = "Open quick access" },
                                )
                            },
                        )
                    },
                ) { bodyPadding ->
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("scrolling-content"),
                        contentPadding = bodyPadding,
                    ) {
                        items((1..30).toList()) { Text("Story $it") }
                    }
                }
            }
        }

        val headerTop = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot.top
        assertTrue(headerTop >= 24f)
        compose.onNodeWithTag("scrolling-content").performScrollToIndex(29)
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open quick access").assertIsDisplayed()
    }


    @Test
    fun topLevelBodyKeepsSharedGapBelowPinnedHeaderAfterDeepScroll() {
        compose.setContent {
            HikariTheme {
                HikariTopLevelScaffold(
                    contentPadding = PaddingValues.Zero,
                    header = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pinned-header"),
                        )
                    },
                ) { bodyPadding ->
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("gap-scroll-content"),
                        contentPadding = bodyPadding,
                    ) {
                        items((1..30).toList()) { index ->
                            Text(
                                text = "Gap story $index",
                                modifier = Modifier.height(48.dp),
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("gap-scroll-content").performScrollToIndex(10)
        val headerBottom = compose.onNodeWithTag("pinned-header").fetchSemanticsNode().boundsInRoot.bottom
        val scrolledItemTop = compose.onNodeWithText("Gap story 11").fetchSemanticsNode().boundsInRoot.top
        val expectedGapPx = with(compose.density) { 16.dp.toPx() }
        assertEquals(
            "Sticky destinations must keep the shared header-to-content rhythm after deep scroll",
            expectedGapPx,
            scrolledItemTop - headerBottom,
            1f,
        )
    }

    @Test
    fun bottomSeparationShadowAppearsOnlyAfterStickyContentScrolls() {
        val scrolled = mutableStateOf(false)
        compose.setContent {
            HikariTheme {
                HikariStickyDestinationScaffold(
                    contentPadding = PaddingValues.Zero,
                    header = { HikariTopLevelHeader(title = "Home") },
                    headerScrolled = scrolled.value,
                ) {
                    Box(Modifier.fillMaxSize().testTag("sticky-body"))
                }
            }
        }

        compose.onNodeWithTag("hikari-bottom-separation-shadow").assertDoesNotExist()
        compose.runOnIdle { scrolled.value = true }
        compose.onNodeWithTag("hikari-bottom-separation-shadow").assertIsDisplayed()
        val shadowBottom = compose.onNodeWithTag("hikari-bottom-separation-shadow")
            .fetchSemanticsNode().boundsInRoot.bottom
        val bodyTop = compose.onNodeWithTag("sticky-body").fetchSemanticsNode().boundsInRoot.top
        assertEquals(
            "Sticky separation shadow must end at the body edge so no blank space follows it",
            bodyTop,
            shadowBottom,
            1f,
        )
    }

    @Test
    fun scrollToTopActionIsSharedChromeAndInvokesItsCallback() {
        var calls = 0
        compose.setContent {
            HikariTheme {
                HikariTopLevelScaffold(
                    contentPadding = PaddingValues.Zero,
                    header = { HikariTopLevelHeader(title = "Home") },
                    showScrollToTop = true,
                    onScrollToTop = { calls += 1 },
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        compose.onNodeWithContentDescription("Back to top").assertIsDisplayed().performClick()
        assertEquals(1, calls)
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
