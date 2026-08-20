package app.openstory.catalog.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariIconActionStyle
import app.openstory.designsystem.icon.HikariFilterGlyph
import app.openstory.designsystem.icon.HikariGridGlyph
import app.openstory.designsystem.icon.HikariListGlyph
import app.openstory.designsystem.layout.HikariSearchBar
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun LibraryToolbar(
    query: String,
    displayMode: LibraryDisplayMode,
    onQueryChange: (String) -> Unit,
    onFilterRequested: () -> Unit,
    onDisplayModeSelected: (LibraryDisplayMode) -> Unit,
    searchFocusRequester: FocusRequester,
    filterFocusRequester: FocusRequester,
    viewFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    horizontalPadding: Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HikariSearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search your Library",
            contentDescription = "Search your Library",
            modifier = Modifier.weight(1f),
            focusRequester = searchFocusRequester,
            nextFocusRequester = filterFocusRequester,
        )
        HikariIconAction(
            contentDescription = "Open Library filters",
            onClick = onFilterRequested,
            focusRequester = filterFocusRequester,
            nextFocusRequester = viewFocusRequester,
            style = HikariIconActionStyle.ACCENTED_SURFACE,
        ) { HikariFilterGlyph() }
        val targetMode = if (displayMode == LibraryDisplayMode.GRID) {
            LibraryDisplayMode.LIST
        } else {
            LibraryDisplayMode.GRID
        }
        HikariIconAction(
            contentDescription = "Switch to ${targetMode.label().lowercase()} view",
            onClick = { onDisplayModeSelected(targetMode) },
            modifier = Modifier.testTag("library-view-switch"),
            focusRequester = viewFocusRequester,
            nextFocusRequester = contentFocusRequester,
            style = HikariIconActionStyle.ACCENTED_SURFACE,
        ) {
            if (targetMode == LibraryDisplayMode.GRID) HikariGridGlyph() else HikariListGlyph()
        }
    }
}
