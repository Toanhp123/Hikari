package app.openstory.catalog.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.ui.components.StoryShelf
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.layout.HikariTopLevelHeader
import app.openstory.designsystem.layout.plus
import app.openstory.designsystem.layout.withTop
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.theme.hikariAtmosphereBrush
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariTypography

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
    val refreshTopInset = contentPadding.calculateTopPadding()
    val listContentPadding = contentPadding
        .withTop(MaterialTheme.hikariDimensions.zero)
        .plus(bottom = MaterialTheme.hikariSpacing.space24)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        HikariPullToRefresh(
            refreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = modifier
                .fillMaxSize()
                .background(background)
                .testTag("discover-pull-refresh"),
            topInset = refreshTopInset,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("discover-list"),
                contentPadding = listContentPadding,
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
                                modifier = Modifier.testTag("discover-search"),
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
                discoverFeedbackItems(state, onRefresh)
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
            }
        }
    }
}
