package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.openstory.catalog.ui.components.catalogDisplayName
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.common.id.PluginId
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverFeedbackItems(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
) {
    val globalRetryVisible = state.globalFailure?.retryable == true
    state.globalFailure?.let { failure ->
        item("discover-global-failure") {
            HikariInlineFeedback(
                message = catalogFailureMessage(
                    failure.code,
                    "Couldn't refresh Discover. Cached content is still available.",
                ),
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space4),
                actionLabel = if (failure.retryable) "Retry" else null,
                actionEnabled = !state.refreshing,
                onAction = if (failure.retryable) onRefresh else null,
            )
        }
    }
    state.failedPluginIds().forEachIndexed { index, pluginId ->
        val showRetry = !globalRetryVisible && index == 0
        item("discover-failure-${pluginId.value}") {
            HikariInlineFeedback(
                message = "${pluginId.catalogDisplayName()} refresh failed; cached content is still available.",
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space4),
                actionLabel = if (showRetry) "Retry" else null,
                actionEnabled = !state.refreshing,
                onAction = if (showRetry) onRefresh else null,
            )
        }
    }
}

private fun DiscoverUiState.failedPluginIds(): List<PluginId> =
    refreshReport?.failed?.keys?.sortedBy(PluginId::value).orEmpty()
