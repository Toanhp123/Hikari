package app.openstory.catalog.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.openstory.catalog.ui.components.StoryShelf
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariTypography
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.icon.HikariChevronGlyph
import app.openstory.designsystem.theme.hikariAtmosphereBrush
import app.openstory.designsystem.theme.hikariLayoutPolicy

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onStorySelected: (StoryId) -> Unit,
    onCatalogSelected: (PluginId) -> Unit,
    onCombinedSelected: () -> Unit,
    onCategorySelected: (DiscoverQuickCategory) -> Unit = { onCatalogSelected(it.pluginId) },
    searchFocusRequester: FocusRequester? = null,
    searchNextFocusRequester: FocusRequester? = null,
    categoryFocusRequester: FocusRequester? = null,
    categoryNextFocusRequester: FocusRequester? = null,
    catalogFocusRequester: FocusRequester? = null,
    onUtilityRequested: () -> Unit = {},
    utilityFocusRequester: FocusRequester? = null,
    utilityNextFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    val background = MaterialTheme.hikariAtmosphereBrush
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        LazyColumn(
            modifier = modifier.fillMaxSize().background(background),
            contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.space24),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16),
        ) {
            item("discover-search") {
                HikariTopLevelHeader(
                    onAction = onUtilityRequested,
                    focusRequester = utilityFocusRequester,
                    nextFocusRequester = utilityNextFocusRequester,
                    content = {
                        HikariSearchBar(
                            value = "",
                            onValueChange = {},
                            placeholder = "Search all stories",
                            contentDescription = "Search all stories",
                            readOnly = true,
                            onClick = onSearch,
                            focusRequester = searchFocusRequester,
                            nextFocusRequester = searchNextFocusRequester,
                        )
                    },
                )
            }
            item("discover-brand") {
                Text(
                    text = "HIKARI",
                    modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.hikariTypography.brandLabel,
                )
            }
            state.featured?.let { featured ->
                item("discover-featured") {
                    DiscoverHero(
                        entry = featured,
                        onSelected = onStorySelected,
                        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space16),
                    )
                }
            }
            quickCategoryItem(
                state,
                onCategorySelected,
                categoryFocusRequester,
                categoryNextFocusRequester,
            )
            sourceFilterItem(
                state,
                onCatalogSelected,
                onCombinedSelected,
                categoryFocusRequester,
                catalogFocusRequester,
            )
            discoverFeedbackItems(state)
            state.shelves.forEach { shelf ->
                item("discover-shelf-${shelf.key}") {
                    StoryShelf(
                        title = shelf.title,
                        entries = shelf.entries,
                        onSelected = onStorySelected,
                        modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space20),
                    )
                }
            }
            item("discover-refresh-action") { RefreshAction(state.refreshing, onRefresh) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.quickCategoryItem(
    state: DiscoverUiState,
    onSelected: (DiscoverQuickCategory) -> Unit,
    focusRequester: FocusRequester?,
    nextFocusRequester: FocusRequester?,
) {
    if (state.quickCategories.isEmpty()) return
    item("discover-categories") {
        BoxWithConstraints {
            val columnCount = MaterialTheme.hikariLayoutPolicy.compactGridColumns
            val totalSpacing =
                MaterialTheme.hikariSpacing.space20 * columnCount +
                    MaterialTheme.hikariSpacing.space12 * (columnCount - 1)
            val cardWidth = (maxWidth - totalSpacing) / columnCount
            LazyRow(
                contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.space20),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
            ) {
                items(state.quickCategories, key = DiscoverQuickCategory::key) { category ->
                    val selected = state.selectedCatalogId == category.pluginId &&
                        state.selectedSourceId == category.sourceId
                    val focusModifier = if (category == state.quickCategories.first()) {
                        Modifier.optionalFocusRequester(focusRequester)
                            .optionalNextFocus(nextFocusRequester)
                    } else {
                        Modifier
                    }
                    DiscoverCategoryCard(category, selected, onSelected, cardWidth, focusModifier)
                }
            }
        }
    }
}

@Composable
private fun DiscoverCategoryCard(
    category: DiscoverQuickCategory,
    selected: Boolean,
    onSelected: (DiscoverQuickCategory) -> Unit,
    width: Dp,
    modifier: Modifier,
) {
    val shape = MaterialTheme.hikariShapes.prominentCard
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.hikariSpacing.space18,
                    vertical = MaterialTheme.hikariSpacing.space14,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space2),
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
}

private fun androidx.compose.foundation.lazy.LazyListScope.sourceFilterItem(
    state: DiscoverUiState,
    onCatalogSelected: (PluginId) -> Unit,
    onCombinedSelected: () -> Unit,
    categoryFocusRequester: FocusRequester?,
    catalogFocusRequester: FocusRequester?,
) = item("discover-sources") {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.hikariSpacing.space20),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
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
                label = { Text(catalog.pluginId.discoverDisplayName()) },
                modifier = Modifier.semantics { traversalIndex = CATALOG_TRAVERSAL_INDEX },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoverFeedbackItems(
    state: DiscoverUiState,
) {
    if (state.refreshing) {
        item("discover-refreshing") {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().semantics { contentDescription = "Refreshing Discover" },
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    state.globalFailure?.let { failure ->
        item("discover-global-failure") {
            HikariInlineFeedback(
                message = failure.code,
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space4),
            )
        }
    }
    state.refreshReport?.failed?.keys?.sortedBy { it.value }?.forEach { pluginId ->
        item("discover-failure-${pluginId.value}") {
            HikariInlineFeedback(
                message = "${pluginId.discoverDisplayName()} refresh failed; cached content is still available.",
                modifier = Modifier.padding(horizontal = MaterialTheme.hikariSpacing.space4),
            )
        }
    }
}

@Composable
private fun RefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.hikariSpacing.space20),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            "Refresh sources",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.hikariTypography.refreshAction,
            modifier = Modifier
                .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)
                .clickable(enabled = !refreshing, onClick = onRefresh)
                .padding(MaterialTheme.hikariSpacing.space14),
        )
    }
}

internal fun PluginId.discoverDisplayName(): String {
    val segments = value.split('.')
    val key = segments.last().lowercase()
    return when (key) {
        "mangadex" -> "MangaDex"
        "myanimelist", "mal" -> "MyAnimeList"
        else -> if (segments.size == 2 && segments.first().equals("catalog", ignoreCase = true)) {
            "Catalog ${key.uppercase()}"
        } else {
            key
                .split('-', '_')
                .filter(String::isNotBlank)
                .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }
        }
    }
}

private fun DiscoverQuickCategory.presentationLabel(): String = when {
    sourceId.contains("latest", ignoreCase = true) ||
        sourceId.contains("fresh", ignoreCase = true) ||
        label.contains("new release", ignoreCase = true) -> "New releases"
    label.endsWith(" stories", ignoreCase = true) -> label.dropLast(" stories".length)
    else -> label
}

private fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    requester?.let(::focusRequester) ?: this

private fun Modifier.optionalNextFocus(requester: FocusRequester?): Modifier =
    requester?.let { nextRequester ->
        focusProperties {
            next = nextRequester
            down = nextRequester
        }
    } ?: this

private const val CATEGORY_TRAVERSAL_INDEX = 2f
private const val CATALOG_TRAVERSAL_INDEX = 3f
