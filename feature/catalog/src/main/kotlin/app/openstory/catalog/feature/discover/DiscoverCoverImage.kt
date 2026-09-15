package app.openstory.catalog.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.catalog.feature.assets.CoverArtworkState

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
