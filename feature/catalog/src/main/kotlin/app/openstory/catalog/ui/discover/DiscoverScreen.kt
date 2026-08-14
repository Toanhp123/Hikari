package app.openstory.catalog.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.openstory.catalog.ui.components.StoryShelf
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.layout.plus

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
    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
            MaterialTheme.colorScheme.background,
        ),
    )
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        LazyColumn(
            modifier = modifier.fillMaxSize().background(background),
            contentPadding = contentPadding.plus(bottom = MaterialTheme.hikariSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.large),
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
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.2.sp,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            state.featured?.let { featured ->
                item("discover-featured") {
                    DiscoverHero(
                        entry = featured,
                        onSelected = onStorySelected,
                        modifier = Modifier.padding(horizontal = 16.dp),
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
                        modifier = Modifier.padding(horizontal = 20.dp),
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
            val cardWidth = (maxWidth - 52.dp) / 2
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    val shape = RoundedCornerShape(22.dp)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier
            .width(width)
            .heightIn(min = 64.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), shape)
            .clickable { onSelected(category) }
            .semantics {
                contentDescription =
                    "Category ${category.label} from ${category.pluginId.discoverDisplayName()}"
                traversalIndex = CATEGORY_TRAVERSAL_INDEX
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    category.presentationLabel().uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
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
            Text(">", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
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
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("combined") {
            FilterChip(
                state.selectedCatalogId == null,
                onCombinedSelected,
                { Text("All sources") },
                Modifier
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
            FilterChip(
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
            Text(
                failure.code,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
    state.refreshReport?.failed?.keys?.sortedBy { it.value }?.forEach { pluginId ->
        item("discover-failure-${pluginId.value}") {
            Text(
                "${pluginId.discoverDisplayName()} refresh failed; cached content is still available.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun RefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            "Refresh sources",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(enabled = !refreshing, onClick = onRefresh)
                .padding(14.dp),
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
