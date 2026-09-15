package app.openstory.catalog.feature.discover

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.catalog.feature.assets.CoverArtworkState
import app.openstory.designsystem.content.HikariPosterCard
import app.openstory.designsystem.content.HikariPosterGeometry

@Composable
internal fun DiscoverPosterTile(
    card: DiscoverCardUi,
    onSelected: () -> Unit,
    onCoverReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HikariPosterCard(
        title = card.title,
        supportingText = card.supportingLabel ?: card.ratingLabel,
        onClick = onSelected,
        modifier = modifier
            .width(DiscoverVisualMetrics.LatestUpdatesCoverWidth)
            .testTag(DiscoverTestTags.card(CatalogSectionKind.LATEST_UPDATES, card.ref)),
        geometry = HikariPosterGeometry.Standard,
    ) {
        CoverArtwork(
            title = card.title,
            locator = card.coverLocator,
            assetKey = card.coverAssetKey,
            modifier = Modifier.matchParentSize(),
            onStateChanged = { state ->
                if (state == CoverArtworkState.Ready) onCoverReady()
            },
        )
    }
}
