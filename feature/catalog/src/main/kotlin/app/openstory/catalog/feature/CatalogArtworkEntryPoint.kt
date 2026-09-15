package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.openstory.artwork.ArtworkLoader
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.catalog.feature.assets.LocalArtworkLoader

@Composable
fun CatalogCoverArtwork(
    artworkLoader: ArtworkLoader,
    locator: CoverLocator?,
    assetKey: CoverAssetKey?,
    modifier: Modifier,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val activeLoader = artworkLoader.takeIf {
        lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    }
    CompositionLocalProvider(LocalArtworkLoader provides activeLoader) {
        CoverArtwork(
            locator = locator,
            assetKey = assetKey,
            modifier = modifier,
        )
    }
}
