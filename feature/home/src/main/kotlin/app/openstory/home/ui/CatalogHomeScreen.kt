package app.openstory.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openstory.common.AppError
import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeCatalogSection

@Composable
fun CatalogHomeScreen(
    catalog: HomeCatalog,
    refreshing: Boolean,
    failure: AppError?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    coverRenderer: HomeCoverRenderer = PlaceholderHomeCoverRenderer,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "catalog-header") {
            CatalogHeader(
                catalog = catalog,
                refreshing = refreshing,
                onRefresh = actions.refresh,
            )
        }
        item(key = "catalog-combined-switch") {
            Button(
                onClick = actions.showCombined,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("Across catalogs")
            }
        }
        if (refreshing) {
            item(key = "catalog-refreshing") {
                HomeRefreshIndicator()
            }
        }
        if (failure != null) {
            item(key = "catalog-failure") {
                RefreshFailureMessage(
                    pluginId = catalog.pluginId,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        catalog.sections.forEach { section ->
            item(key = "catalog-section-title-${section.sourceId}") {
                Text(
                    text = section.title,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item(key = "catalog-section-items-${section.sourceId}") {
                CatalogSectionRow(
                    section = section,
                    actions = actions,
                    coverRenderer = coverRenderer,
                )
            }
        }
    }
}

@Composable
private fun CatalogSectionRow(
    section: HomeCatalogSection,
    actions: HomeActions,
    coverRenderer: HomeCoverRenderer,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = section.items,
            key = { it.storyId.value },
        ) { item ->
            HomeCard(
                card = HomeCardPresentation(
                    storyId = item.storyId,
                    title = item.title,
                    contentType = item.contentType,
                    authors = item.authors,
                    coverReference = item.coverReference,
                    score = item.score,
                    scoreScale = item.scoreScale,
                    scoreSource = item.pluginId,
                    sectionTitle = section.title,
                ),
                onClick = actions.storySelected,
                coverRenderer = coverRenderer,
            )
        }
    }
}

@Composable
private fun CatalogHeader(
    catalog: HomeCatalog,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = catalog.pluginId.value,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Version ${catalog.pluginVersion}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onRefresh,
            enabled = !refreshing,
        ) {
            Text("Refresh")
        }
    }
}
