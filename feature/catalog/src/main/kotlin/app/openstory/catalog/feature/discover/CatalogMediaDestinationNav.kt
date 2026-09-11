package app.openstory.catalog.feature.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.presentation.productLabel
import app.openstory.designsystem.theme.hikariSpacing

private val NAV_BORDER_WIDTH = 1.dp
private val NAV_CONTAINER_ELEVATION = 8.dp
private val NAV_CONTAINER_TONAL = 3.dp
private val NAV_SELECTED_ELEVATION = 3.dp
private val NAV_ROW_HORIZONTAL_PADDING = 6.dp
private val NAV_ROW_VERTICAL_PADDING = 5.dp
private const val MANGA_TAB_WEIGHT = 1.0f
private const val HOME_TAB_WEIGHT = 1.0f
private const val LN_TAB_WEIGHT = 1.25f
private const val BORDER_ALPHA = 0.35f

internal enum class DiscoverNavTab {
    MANGA,
    HOME,
    LIGHT_NOVEL,
}

internal fun DiscoverNavTab.mediaSelectionFrom(
    selectedMediaType: CatalogMediaType,
): CatalogMediaType? = when (this) {
    DiscoverNavTab.MANGA -> CatalogMediaType.MANGA.takeUnless { it == selectedMediaType }
    DiscoverNavTab.HOME -> null
    DiscoverNavTab.LIGHT_NOVEL -> CatalogMediaType.LIGHT_NOVEL.takeUnless { it == selectedMediaType }
}

@Composable
internal fun CatalogMediaDestinationNav(
    selectedMediaType: CatalogMediaType,
    onMediaSelected: (CatalogMediaType) -> Unit,
    modifier: Modifier = Modifier,
    onHomeSelected: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .widthIn(max = DiscoverVisualMetrics.MediaNavMaxWidth)
            .fillMaxWidth()
            .height(DiscoverVisualMetrics.MediaNavHeight)
            .testTag(DiscoverTestTags.MEDIA_NAV),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(NAV_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant.copy(alpha = BORDER_ALPHA)),
        shadowElevation = NAV_CONTAINER_ELEVATION,
        tonalElevation = NAV_CONTAINER_TONAL,
    ) {
        NavTabsRow(
            selectedMediaType = selectedMediaType,
            onTabSelected = { tab ->
                if (tab == DiscoverNavTab.HOME) {
                    onHomeSelected()
                } else {
                    tab.mediaSelectionFrom(selectedMediaType)?.let(onMediaSelected)
                }
            },
        )
    }
}

@Composable
private fun NavTabsRow(
    selectedMediaType: CatalogMediaType,
    onTabSelected: (DiscoverNavTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .selectableGroup()
            .padding(horizontal = NAV_ROW_HORIZONTAL_PADDING, vertical = NAV_ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavPillTab(
            label = CatalogMediaType.MANGA.productLabel,
            selected = selectedMediaType == CatalogMediaType.MANGA,
            testTag = DiscoverTestTags.mediaDestination(CatalogMediaType.MANGA),
            onClick = { onTabSelected(DiscoverNavTab.MANGA) },
            modifier = Modifier.weight(MANGA_TAB_WEIGHT),
        )
        NavPillTab(
            label = "Home",
            selected = false,
            testTag = DiscoverTestTags.NAV_HOME,
            onClick = { onTabSelected(DiscoverNavTab.HOME) },
            modifier = Modifier.weight(HOME_TAB_WEIGHT),
        )
        NavPillTab(
            label = CatalogMediaType.LIGHT_NOVEL.productLabel,
            selected = selectedMediaType == CatalogMediaType.LIGHT_NOVEL,
            testTag = DiscoverTestTags.mediaDestination(CatalogMediaType.LIGHT_NOVEL),
            onClick = { onTabSelected(DiscoverNavTab.LIGHT_NOVEL) },
            modifier = Modifier.weight(LN_TAB_WEIGHT),
        )
    }
}

@Composable
private fun NavPillTab(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .height(DiscoverVisualMetrics.MediaNavSelectedHeight)
            .testTag(testTag)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shadowElevation = if (selected) NAV_SELECTED_ELEVATION else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
