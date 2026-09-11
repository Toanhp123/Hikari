package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.presentation.productLabel
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun CatalogMediaDestinationNav(
    selectedMediaType: CatalogMediaType,
    onMediaSelected: (CatalogMediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = DiscoverVisualMetrics.MediaNavMaxWidth)
            .fillMaxWidth()
            .height(DiscoverVisualMetrics.MediaNavHeight)
            .testTag(DiscoverTestTags.MEDIA_NAV),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(MaterialTheme.hikariSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogMediaType.entries.forEach { mediaType ->
                MediaDestination(
                    mediaType = mediaType,
                    selected = mediaType == selectedMediaType,
                    onSelected = onMediaSelected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MediaDestination(
    mediaType: CatalogMediaType,
    selected: Boolean,
    onSelected: (CatalogMediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .height(DiscoverVisualMetrics.MediaNavSelectedHeight)
            .testTag(DiscoverTestTags.mediaDestination(mediaType))
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { if (!selected) onSelected(mediaType) },
            ),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Text(mediaType.productLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}
