package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.feature.assets.CoverArtwork
import app.openstory.catalog.feature.assets.LocalCatalogImageLoader

@Composable
fun CatalogCoverArtwork(
    runtimeAccess: CatalogRuntimeAccess,
    title: String,
    locator: CoverLocator?,
    assetKey: CoverAssetKey?,
    modifier: Modifier,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val imageLoader = runtimeAccess.holder.images.takeIf {
        lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    }
    CompositionLocalProvider(LocalCatalogImageLoader provides imageLoader) {
        CoverArtwork(
            title = title,
            locator = locator,
            assetKey = assetKey,
            modifier = modifier,
        )
    }
}
