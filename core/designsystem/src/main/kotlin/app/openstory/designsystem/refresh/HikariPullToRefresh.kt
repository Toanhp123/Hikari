package app.openstory.designsystem.refresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import app.openstory.designsystem.theme.HikariDefaultDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikariPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = HikariDefaultDimensions.zero,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val guardedRefresh = { if (!refreshing) onRefresh() }
    Box(modifier.refreshSemantics(refreshing, onRefresh)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = guardedRefresh,
            modifier = Modifier
                .padding(top = topInset)
                .fillMaxSize()
                .clipToBounds(),
            state = state,
            indicator = { HikariPullRefreshIndicator(state, refreshing) },
            content = content,
        )
    }
}

private fun Modifier.refreshSemantics(refreshing: Boolean, onRefresh: () -> Unit): Modifier =
    semantics {
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Refresh",
                action = {
                    if (!refreshing) {
                        onRefresh()
                        true
                    } else {
                        false
                    }
                },
            ),
        )
        if (refreshing) stateDescription = "Refreshing"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.HikariPullRefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = refreshing,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .testTag("hikari-pull-refresh-indicator"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        color = MaterialTheme.colorScheme.primary,
    )
}
