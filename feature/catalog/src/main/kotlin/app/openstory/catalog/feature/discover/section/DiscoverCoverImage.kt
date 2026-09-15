package app.openstory.catalog.feature.discover.section

import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.artwork.CoverArtwork
import app.openstory.catalog.feature.artwork.CoverArtworkState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun DiscoverCoverImage(
    card: DiscoverCardUi,
    onCoverReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverArtwork(
        locator = card.coverLocator,
        assetKey = card.coverAssetKey,
        modifier = modifier,
        onStateChanged = { state ->
            if (state == CoverArtworkState.Ready) onCoverReady()
        },
    )
}
