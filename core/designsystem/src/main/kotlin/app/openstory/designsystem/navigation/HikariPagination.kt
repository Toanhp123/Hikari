package app.openstory.designsystem.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openstory.designsystem.control.HikariCompactAction
import app.openstory.designsystem.control.HikariCompactIconAction
import app.openstory.designsystem.icon.HikariBackGlyph
import app.openstory.designsystem.icon.HikariChevronGlyph
import app.openstory.designsystem.menu.HikariDropdownMenu
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariPagination(
    currentPage: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return

    val safePage = currentPage.coerceIn(1, pageCount)
    var selectorExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            MaterialTheme.hikariSpacing.space8,
            Alignment.CenterHorizontally,
        ),
    ) {
        HikariCompactIconAction(
            onClick = { onPageSelected(safePage - 1) },
            contentDescription = "Previous page",
            enabled = safePage > 1,
        ) {
            HikariBackGlyph(
                modifier = Modifier.size(MaterialTheme.hikariDimensions.iconMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            HikariCompactAction(
                onClick = { selectorExpanded = true },
                contentDescription = "Page $safePage of $pageCount. Select page",
            ) {
                Text(
                    text = "$safePage / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            HikariDropdownMenu(
                expanded = selectorExpanded,
                onDismissRequest = { selectorExpanded = false },
            ) {
                paginationJumpPages(safePage, pageCount).forEach { page ->
                    DropdownMenuItem(
                        text = { Text("Page $page") },
                        onClick = {
                            selectorExpanded = false
                            onPageSelected(page)
                        },
                    )
                }
            }
        }
        HikariCompactIconAction(
            onClick = { onPageSelected(safePage + 1) },
            contentDescription = "Next page",
            enabled = safePage < pageCount,
        ) {
            HikariChevronGlyph(
                modifier = Modifier.size(MaterialTheme.hikariDimensions.iconMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun paginationJumpPages(currentPage: Int, pageCount: Int): List<Int> =
    when {
        pageCount <= 0 -> emptyList()
        pageCount <= MAX_DIRECT_PAGE_COUNT -> (1..pageCount).toList()
        else -> {
            val safePage = currentPage.coerceIn(1, pageCount)
            buildList {
                add(1)
                for (page in (safePage - PAGE_WINDOW_RADIUS)..(safePage + PAGE_WINDOW_RADIUS)) {
                    if (page in 1..pageCount) add(page)
                }
                add(pageCount)
            }.distinct()
        }
    }

private const val MAX_DIRECT_PAGE_COUNT = 12
private const val PAGE_WINDOW_RADIUS = 2
