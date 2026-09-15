package app.openstory.catalog.feature.discover.header

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.discover.DiscoverTestTags
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariIconActionStyle
import app.openstory.designsystem.presentation.HikariFocusedHeader
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun DiscoverHeader(
    mediaType: CatalogMediaType,
    horizontalInset: Dp,
) {
    HikariFocusedHeader(
        title = "Discover",
        titleModifier = Modifier.testTag(DiscoverTestTags.PAGE_IDENTITY),
        subtitle = "Amazing stories await you.",
        titleTrailingContent = {
            Text(
                text = "\u2022 ${mediaType.productLabel}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = { DiscoverSearchPreview() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalInset,
                top = MaterialTheme.hikariSpacing.space16,
                end = horizontalInset,
            ),
    )
}

@Composable
private fun DiscoverSearchPreview() {
    HikariIconAction(
        onClick = {},
        contentDescription = "Search",
        enabled = false,
        style = HikariIconActionStyle.TONAL,
    ) {
        DiscoverSearchIcon(
            size = SEARCH_ICON_SIZE,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val CatalogMediaType.productLabel: String
    get() = when (this) {
        CatalogMediaType.MANGA -> "Manga"
        CatalogMediaType.LIGHT_NOVEL -> "Light Novel"
    }

private val SEARCH_ICON_SIZE = 18.dp
