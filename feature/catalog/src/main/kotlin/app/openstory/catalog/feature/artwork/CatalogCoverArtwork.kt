package app.openstory.catalog.feature.artwork

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

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

@Composable
internal fun CoverArtwork(
    locator: CoverLocator?,
    assetKey: CoverAssetKey?,
    modifier: Modifier,
    onStateChanged: (CoverArtworkState) -> Unit = {},
) {
    val artworkLoader = LocalArtworkLoader.current
    if (assetKey != null && artworkLoader != null) {
        LifecycleStartEffect(artworkLoader, assetKey) {
            artworkLoader.onDemandStarted()
            onStopOrDispose { artworkLoader.onDemandStopped() }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val imageRequest = remember(assetKey, locator, context) { assetKey.toImageRequest(context, locator) }
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            imageLoader = artworkLoader.imageLoader(),
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onLoading = { onStateChanged(CoverArtworkState.Loading) },
            onSuccess = {
                artworkLoader.onArtworkReady()
                onStateChanged(CoverArtworkState.Ready)
            },
            onError = { state: AsyncImagePainter.State.Error ->
                onStateChanged(CoverArtworkState.Failed(state.result.throwable.toCoverArtworkFailure()))
            },
        )
    }
}

internal fun Throwable.toCoverArtworkFailure(): CatalogFailure {
    var current: Throwable? = this
    var mappedFailure: CatalogFailure? = null
    while (current != null && mappedFailure == null) {
        val candidate = current
        mappedFailure = when (candidate) {
            is ArtworkFailureException -> CatalogFailure.Artwork(candidate.reason.toCatalogReason())
            is CatalogFailureException -> candidate.failure.takeIf { it is CatalogFailure.Artwork }
            else -> null
        }
        current = candidate.cause
    }
    return mappedFailure ?: CatalogFailure.Artwork(CatalogArtworkFailureReason.DECODE_FAILED)
}

private fun ArtworkFailureReason.toCatalogReason(): CatalogArtworkFailureReason = when (this) {
    ArtworkFailureReason.INVALID_LOCATOR -> CatalogArtworkFailureReason.INVALID_LOCATOR
    ArtworkFailureReason.POLICY_REJECTED -> CatalogArtworkFailureReason.POLICY_REJECTED
    ArtworkFailureReason.REDIRECT_REJECTED -> CatalogArtworkFailureReason.REDIRECT_REJECTED
    ArtworkFailureReason.MEDIA_TYPE_REJECTED -> CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED
    ArtworkFailureReason.ENCODED_TOO_LARGE -> CatalogArtworkFailureReason.ENCODED_TOO_LARGE
    ArtworkFailureReason.DIMENSIONS_TOO_LARGE -> CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE
    ArtworkFailureReason.DECODE_FAILED -> CatalogArtworkFailureReason.DECODE_FAILED
    ArtworkFailureReason.TIMEOUT -> CatalogArtworkFailureReason.TIMEOUT
    ArtworkFailureReason.SATURATED,
    ArtworkFailureReason.IO_FAILED,
    -> CatalogArtworkFailureReason.IO_FAILED
}
