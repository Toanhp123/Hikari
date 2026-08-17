package app.openstory.catalog.ui.discover

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.openstory.designsystem.icon.HikariChevronGlyph
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariTypography

@Composable
internal fun DiscoverCategoryCard(
    category: DiscoverQuickCategory,
    selected: Boolean,
    onSelected: (DiscoverQuickCategory) -> Unit,
    width: Dp,
    modifier: Modifier,
) {
    val shape = MaterialTheme.hikariShapes.sheetCard
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(
                alpha = MaterialTheme.hikariOpacity.selectedSubtle,
            )
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier
            .width(width)
            .heightIn(min = MaterialTheme.hikariDimensions.topBarMinHeight)
            .border(
                MaterialTheme.hikariDimensions.borderThin,
                MaterialTheme.colorScheme.primary.copy(
                    alpha = MaterialTheme.hikariOpacity.accentBorder,
                ),
                shape,
            )
            .clickable { onSelected(category) }
            .semantics {
                contentDescription =
                    "Category ${category.label} from ${category.pluginId.discoverDisplayName()}"
                traversalIndex = CATEGORY_TRAVERSAL_INDEX
                this.selected = selected
            },
    ) {
        DiscoverCategoryCardContent(category)
    }
}

@Composable
private fun DiscoverCategoryCardContent(category: DiscoverQuickCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space20,
                vertical = MaterialTheme.hikariSpacing.space16,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
        ) {
            Text(
                category.presentationLabel().uppercase(),
                style = MaterialTheme.hikariTypography.categoryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                category.pluginId.discoverDisplayName(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        HikariChevronGlyph(
            modifier = Modifier.size(MaterialTheme.hikariDimensions.iconStandard),
        )
    }
}

private fun DiscoverQuickCategory.presentationLabel(): String = when {
    sourceId.contains("latest", ignoreCase = true) ||
        sourceId.contains("fresh", ignoreCase = true) ||
        label.contains("new release", ignoreCase = true) -> "New releases"
    label.endsWith(" stories", ignoreCase = true) -> label.dropLast(" stories".length)
    else -> label
}

private const val CATEGORY_TRAVERSAL_INDEX = 2f
