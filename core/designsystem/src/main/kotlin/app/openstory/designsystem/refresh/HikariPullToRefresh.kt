package app.openstory.designsystem.refresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.openstory.designsystem.R

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

    val refreshLabel = stringResource(R.string.hikari_refresh_action)
    val refreshingLabel = stringResource(R.string.hikari_refreshing_state)
    val accessibleModifier = modifier.semantics {
        if (refreshing) stateDescription = refreshingLabel
        customActions = listOf(
            CustomAccessibilityAction(refreshLabel) {
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
