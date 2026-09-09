package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi

@Composable
internal fun DiscoverScreen(
    state: DiscoverUiState,
    onMediaSelected: (CatalogMediaType) -> Unit,
    onStorySelected: (StorySourceRef, CoverAssetKey?) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DiscoverTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "discover-header") {
            DiscoverHeader(
                state = state,
                onMediaSelected = onMediaSelected,
            )
        }

        when (val content = state.content) {
            DiscoverContentState.NoContentLoading -> loadingSections()
            is DiscoverContentState.NoContentFailure -> item(key = "discover-failure") {
                DiscoverIssuePanel(content.issue, onRetry)
            }
            is DiscoverContentState.Empty -> {
                if (content.refreshing) refreshIndicator()
                content.issue?.let { issue -> item(key = "discover-empty-issue") {
                    DiscoverIssuePanel(issue, onRetry)
                } }
                item(key = "discover-empty") { DiscoverEmptyState(onRetry) }
            }
            is DiscoverContentState.Content -> {
                if (content.refreshing) refreshIndicator()
                content.issue?.let { issue -> item(key = "discover-content-issue") {
                    DiscoverIssuePanel(issue, onRetry)
                } }
                items(
                    items = content.sections,
                    key = { section -> section.kind.name },
                ) { section ->
                    DiscoverSection(
                        section = section,
                        onStorySelected = onStorySelected,
                    )
                }
            }
        }

        item(key = "discover-bottom-space") { Spacer(modifier = Modifier.padding(bottom = 12.dp)) }
    }
}

@Composable
private fun DiscoverHeader(
    state: DiscoverUiState,
    onMediaSelected: (CatalogMediaType) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Three distinct signals. One deliberately bounded shelf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.mediaOptions.forEach { option ->
                FilterChip(
                    selected = state.selectedMediaType == option.mediaType,
                    onClick = { onMediaSelected(option.mediaType) },
                    enabled = option.enabled,
                    label = { Text(option.mediaType.label) },
                    modifier = Modifier.testTag(option.mediaType.testTag),
                )
            }
        }
    }
}

@Composable
private fun DiscoverIssuePanel(issue: CatalogIssueUi, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(issue.kind.message, style = MaterialTheme.typography.titleMedium)
            if (issue.retryable) {
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun DiscoverEmptyState(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag(DiscoverTestTags.EMPTY),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nothing published yet", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This source completed successfully, but has no eligible stories for this medium.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("Refresh") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.refreshIndicator() {
    item(key = "discover-refreshing") {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag(DiscoverTestTags.REFRESHING),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = Color.Transparent,
        )
    }
}

private val CatalogMediaType.label: String
    get() = when (this) {
        CatalogMediaType.MANGA -> "Manga"
        CatalogMediaType.LIGHT_NOVEL -> "Light Novel"
    }

private val CatalogMediaType.testTag: String
    get() = when (this) {
        CatalogMediaType.MANGA -> DiscoverTestTags.MEDIA_MANGA
        CatalogMediaType.LIGHT_NOVEL -> DiscoverTestTags.MEDIA_LIGHT_NOVEL
    }

private val CatalogIssueKind.message: String
    get() = when (this) {
        CatalogIssueKind.SOURCE_UNAVAILABLE -> "No catalog source is available in this build."
        CatalogIssueKind.INVALID_SOURCE_DATA -> "The source returned data that could not be safely displayed."
        CatalogIssueKind.ACQUISITION_FAILED -> "The catalog could not be refreshed."
        CatalogIssueKind.STORAGE_FAILED -> "Saved catalog data could not be read."
        CatalogIssueKind.ARTWORK_FAILED -> "Some artwork could not be displayed."
        CatalogIssueKind.INTERNAL_FAILURE -> "The catalog is temporarily unavailable."
    }
