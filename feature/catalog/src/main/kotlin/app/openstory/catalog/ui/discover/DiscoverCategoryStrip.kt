package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import app.openstory.designsystem.theme.hikariLayoutPolicy
import app.openstory.designsystem.theme.hikariSpacing

internal fun LazyListScope.quickCategoryItem(
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

internal fun Modifier.optionalFocusRequester(requester: FocusRequester?): Modifier =
    requester?.let(::focusRequester) ?: this

private fun Modifier.optionalNextFocus(requester: FocusRequester?): Modifier =
    requester?.let { nextRequester ->
        focusProperties {
            next = nextRequester
            down = nextRequester
        }
    } ?: this
