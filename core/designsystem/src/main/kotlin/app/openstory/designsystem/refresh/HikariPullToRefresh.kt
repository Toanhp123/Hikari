package app.openstory.designsystem.refresh

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikariPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val guardedRefresh = {
        if (!refreshing) {
            onRefresh()
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = guardedRefresh,
        modifier = modifier.semantics {
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
        },
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        content = content,
    )
}
