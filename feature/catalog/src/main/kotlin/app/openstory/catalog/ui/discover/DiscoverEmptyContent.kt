package app.openstory.catalog.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.state.HikariEmptyState

@Composable
internal fun DiscoverEmptyContent(
    reason: DiscoverNoContentReason,
    modifier: Modifier = Modifier,
) {
    val copy = when (reason) {
        DiscoverNoContentReason.EMPTY_FEED -> DiscoverEmptyCopy(
            title = "Nothing to discover yet",
            message = "Pull to refresh when catalog content becomes available.",
        )
        DiscoverNoContentReason.NO_ENABLED_PROVIDERS -> DiscoverEmptyCopy(
            title = "No catalog sources enabled",
            message = "Enable a catalog source before discovering stories.",
        )
    }
    HikariEmptyState(
        title = copy.title,
        message = copy.message,
        modifier = modifier.testTag("discover-empty"),
    )
}

private data class DiscoverEmptyCopy(
    val title: String,
    val message: String,
)
