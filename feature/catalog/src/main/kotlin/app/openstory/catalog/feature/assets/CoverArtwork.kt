package app.openstory.catalog.feature.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
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
        val catalogImageLoader = LocalCatalogImageLoader.current
        if (assetKey != null && catalogImageLoader != null) {
            DisposableEffect(catalogImageLoader, assetKey) {
                catalogImageLoader.onDemandStarted()
                onDispose(catalogImageLoader::onDemandStopped)
            }
            val request = remember(assetKey, locator) { CoverRequest(assetKey, locator) }
            val context = androidx.compose.ui.platform.LocalContext.current
            val imageRequest = remember(request, context) { request.toImageRequest(context) }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                imageLoader = catalogImageLoader.imageLoader(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = { onStateChanged(CoverArtworkState.Loading) },
                onSuccess = {
                    catalogImageLoader.onCoverReady()
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
    while (current != null) {
        if (current is CatalogFailureException && current.failure is CatalogFailure.Artwork) {
            return current.failure
        }
        current = current.cause
    }
    return CatalogFailure.Artwork(CatalogArtworkFailureReason.DECODE_FAILED)
}
