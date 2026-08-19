package app.openstory.catalog.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.state.HikariEmptyState

@Composable
internal fun DiscoverEmptyContent(
    modifier: Modifier = Modifier,
) {
    HikariEmptyState(
        title = "Nothing to discover yet",
        message = "Pull to refresh when catalog content becomes available.",
        modifier = modifier.testTag("discover-empty"),
    )
}
