package app.openstory.catalog.feature.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException

@Composable
internal fun CoverArtwork(
    title: String,
    locator: app.openstory.catalog.domain.asset.CoverLocator?,
    assetKey: app.openstory.catalog.domain.asset.CoverAssetKey?,
    modifier: Modifier,
    onStateChanged: (CoverArtworkState) -> Unit = {},
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.surfaceVariant,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        title.firstOrNull()?.uppercase()?.let { placeholder ->
            Text(
                text = placeholder,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            )
        }
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
                modifier = Modifier.fillMaxSize(),
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
