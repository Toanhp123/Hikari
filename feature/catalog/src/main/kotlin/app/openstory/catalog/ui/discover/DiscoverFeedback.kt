package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.ui.components.catalogDisplayName
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.common.id.PluginId
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.discoverFeedbackItems(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
    onRetryObservation: () -> Unit,
    separatedFromPreviousSection: Boolean,
) {
    var firstFeedback = true
    state.refresh.failure?.let { failure ->
        val separated = separatedFromPreviousSection && firstFeedback
        item("discover-refresh-failure") {
            HikariInlineFeedback(
                message = catalogFailureMessage(failure.code, "Couldn't refresh Discover."),
                modifier = feedbackSectionModifier(separated)
                    .padding(horizontal = MaterialTheme.hikariSpacing.space4),
                actionLabel = if (failure.retryable) "Retry" else null,
                actionEnabled = !state.refresh.inProgress,
                onAction = if (failure.retryable) onRefresh else null,
            )
        }
        firstFeedback = false
    }
    state.observationIssue?.let { issue ->
        val separated = separatedFromPreviousSection && firstFeedback
        item("discover-observation-issue") {
            HikariInlineFeedback(
                message = catalogFailureMessage(issue.code, "Couldn't update Discover data."),
                modifier = feedbackSectionModifier(separated)
                    .padding(horizontal = MaterialTheme.hikariSpacing.space4),
                actionLabel = if (issue.retryable) "Retry" else null,
                onAction = if (issue.retryable) onRetryObservation else null,
            )
        }
        firstFeedback = false
    }
    state.failedPluginIds().forEachIndexed { index, pluginId ->
        val showRetry = state.refresh.failure == null && index == 0
        val separated = separatedFromPreviousSection && firstFeedback
        item("discover-failure-${pluginId.value}") {
            HikariInlineFeedback(
                message = "${pluginId.catalogDisplayName()} refresh failed.",
                modifier = feedbackSectionModifier(separated)
                    .padding(horizontal = MaterialTheme.hikariSpacing.space4),
                actionLabel = if (showRetry) "Retry" else null,
                actionEnabled = !state.refresh.inProgress,
                onAction = if (showRetry) onRefresh else null,
            )
        }
        firstFeedback = false
    }
}

@Composable
private fun feedbackSectionModifier(separated: Boolean): Modifier = if (separated) {
    Modifier.padding(
        top = MaterialTheme.hikariSpacing.sectionGap - MaterialTheme.hikariSpacing.itemGap,
    )
} else {
    Modifier
}

private fun DiscoverUiState.failedPluginIds(): List<PluginId> =
    refreshReport?.failed?.keys?.sortedBy(PluginId::value).orEmpty()
