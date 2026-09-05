package app.openstory.designsystem.scroll

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import app.openstory.designsystem.theme.HikariTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HikariScrollToTopTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rememberedActionCancelsAnInFlightScrollBeforeStartingTheNextOne() {
        lateinit var onScrollToTop: () -> Unit
        var started = 0
        var cancelled = 0
        var completed = 0
        compose.setContent {
            onScrollToTop = rememberHikariScrollToTopAction {
                val attempt = ++started
                try {
                    if (attempt == 1) awaitCancellation()
                    completed += 1
                } finally {
                    if (attempt == 1) cancelled += 1
                }
            }
        }

        compose.runOnIdle { onScrollToTop() }
        compose.runOnIdle { onScrollToTop() }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(2, started)
            assertEquals(1, cancelled)
            assertEquals(1, completed)
        }
    }

    @Test
    fun lazyListReturnsToExactTopFromFarIndex() {
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        compose.setContent {
            state = androidx.compose.foundation.lazy.rememberLazyListState()
            scope = rememberCoroutineScope()
            HikariTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("list"), state = state) {
                    items((0..40).toList()) { Text("Item $it") }
                }
            }
        }

        compose.onNodeWithTag("list").performScrollToIndex(30)
        compose.runOnIdle { scope.launch { state.hikariScrollToTop() } }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun lazyGridReturnsToExactTopFromFarIndex() {
        lateinit var state: LazyGridState
        lateinit var scope: CoroutineScope
        compose.setContent {
            state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            scope = rememberCoroutineScope()
            HikariTheme {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("grid"),
                    state = state,
                ) {
                    items((0..40).toList()) { Text("Item $it") }
                }
            }
        }

        compose.onNodeWithTag("grid").performScrollToIndex(30)
        compose.runOnIdle { scope.launch { state.hikariScrollToTop() } }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }
}
