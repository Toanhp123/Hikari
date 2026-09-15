package app.openstory.catalog.feature.discover.section

import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverVisualMetrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.designsystem.content.HikariPosterGeometry

@Composable
internal fun DiscoverPosterTile(
    card: DiscoverCardUi,
    onSelected: () -> Unit,
    onCoverReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    DiscoverPosterStory(
        card = card,
        sectionKind = CatalogSectionKind.LATEST_UPDATES,
        width = DiscoverVisualMetrics.LatestUpdatesCoverWidth,
        geometry = HikariPosterGeometry.Standard,
        onSelected = onSelected,
        onCoverReady = onCoverReady,
        modifier = modifier,
    )
}
