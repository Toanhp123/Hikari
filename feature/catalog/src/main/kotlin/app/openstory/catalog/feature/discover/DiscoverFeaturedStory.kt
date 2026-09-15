package app.openstory.catalog.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.designsystem.content.HikariPosterGeometry

@Composable
internal fun DiscoverFeaturedStory(
    card: DiscoverCardUi,
    onSelected: () -> Unit,
    onCoverReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    DiscoverPosterStory(
        card = card,
        sectionKind = CatalogSectionKind.POPULAR,
        width = DiscoverVisualMetrics.TrendingCoverWidth,
        geometry = HikariPosterGeometry.Featured,
        onSelected = onSelected,
        onCoverReady = onCoverReady,
        modifier = modifier,
    )
}
