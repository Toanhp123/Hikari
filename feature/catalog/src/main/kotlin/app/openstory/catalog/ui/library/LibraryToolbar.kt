package app.openstory.catalog.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.layout.HikariSearchBar

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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        LibraryToolbarButton(
            contentDescription = "Open Library filters",
            onClick = onFilterRequested,
            focusRequester = filterFocusRequester,
            nextFocusRequester = viewFocusRequester,
        ) { FilterGlyph() }
        val targetMode = if (displayMode == LibraryDisplayMode.GRID) {
            LibraryDisplayMode.LIST
        } else {
            LibraryDisplayMode.GRID
        }
        LibraryToolbarButton(
            contentDescription = "Switch to ${targetMode.label().lowercase()} view",
            onClick = { onDisplayModeSelected(targetMode) },
            focusRequester = viewFocusRequester,
            nextFocusRequester = contentFocusRequester,
        ) { ViewGlyph(targetMode) }
    }
}

@Composable
private fun LibraryToolbarButton(
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    nextFocusRequester: FocusRequester,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), CircleShape)
            .focusRequester(focusRequester)
            .focusProperties { next = nextFocusRequester; down = nextFocusRequester }
            .semantics { this.contentDescription = contentDescription },
    ) {
        content()
    }
}

@Composable
private fun FilterGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(48.dp).padding(14.dp)) {
        val stroke = 1.8.dp.toPx()
        listOf(0.24f to 0.66f, 0.5f to 0.36f, 0.76f to 0.58f).forEach { (y, knob) ->
            drawLine(
                color = color,
                start = Offset(size.width * 0.08f, size.height * y),
                end = Offset(size.width * 0.92f, size.height * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(size.width * knob, size.height * y))
        }
    }
}

@Composable
private fun ViewGlyph(targetMode: LibraryDisplayMode) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(48.dp).padding(14.dp)) {
        val stroke = 1.7.dp.toPx()
        if (targetMode == LibraryDisplayMode.GRID) {
            val cell = size.minDimension * 0.34f
            listOf(0f to 0f, 0.58f to 0f, 0f to 0.58f, 0.58f to 0.58f).forEach { (x, y) ->
                drawRect(
                    color = color,
                    topLeft = Offset(size.width * x, size.height * y),
                    size = androidx.compose.ui.geometry.Size(cell, cell),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            }
        } else {
            listOf(0.2f, 0.5f, 0.8f).forEach { y ->
                drawLine(
                    color = color,
                    start = Offset(0f, size.height * y),
                    end = Offset(size.width, size.height * y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
