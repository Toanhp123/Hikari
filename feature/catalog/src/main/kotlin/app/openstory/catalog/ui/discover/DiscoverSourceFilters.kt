package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import app.openstory.catalog.ui.components.catalogDisplayName
import app.openstory.common.id.PluginId
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.sourceFilterItem(
    state: DiscoverUiState,
    onCatalogSelected: (PluginId) -> Unit,
    onCombinedSelected: () -> Unit,
    categoryFocusRequester: FocusRequester?,
    catalogFocusRequester: FocusRequester?,
) = item("discover-sources") {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionContentGap),
    ) {
        item("combined") {
            HikariFilterChip(
                selected = state.selectedCatalogId == null,
                onClick = onCombinedSelected,
                label = { Text("All sources") },
                modifier = Modifier
                    .then(
                        if (state.quickCategories.isEmpty()) {
                            Modifier.optionalFocusRequester(categoryFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .optionalFocusRequester(catalogFocusRequester)
                    .semantics { traversalIndex = CATALOG_TRAVERSAL_INDEX },
            )
        }
        items(state.catalogs, key = { it.pluginId.value }) { catalog ->
            HikariFilterChip(
                selected = state.selectedCatalogId == catalog.pluginId,
                onClick = { onCatalogSelected(catalog.pluginId) },
                label = { Text(catalog.pluginId.catalogDisplayName()) },
                modifier = Modifier.semantics { traversalIndex = CATALOG_TRAVERSAL_INDEX },
            )
        }
    }
}

private const val CATALOG_TRAVERSAL_INDEX = 3f
