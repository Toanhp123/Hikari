package app.openstory.designsystem.refresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikariPullToRefresh(
    refreshing: Boolean,
    enabled: Boolean = true,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    val accessibleModifier = modifier.semantics {
        if (refreshing) stateDescription = "Refreshing"
        customActions = listOf(
            CustomAccessibilityAction("Refresh") {
                if (refreshing) {
                    false
                } else {
                    onRefresh()
                    true
                }
            },
        )
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (!refreshing) onRefresh() },
        modifier = accessibleModifier,
        content = content,
    )
}
