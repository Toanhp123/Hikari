package app.openstory.designsystem.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.openstory.designsystem.motion.LocalHikariMotionPolicy
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade

@Immutable
data class HikariArtworkModel(
    val url: String?,
    val stableKey: String,
    val title: String,
)

@Stable
class HikariArtworkState internal constructor(
    internal val painter: Painter,
    val fallback: HikariArtworkFallback,
    val loading: Boolean,
)

@Composable
fun rememberHikariArtwork(model: HikariArtworkModel): HikariArtworkState {
    val context = LocalPlatformContext.current
    return rememberHikariArtwork(
        model = model,
        imageLoader = context.imageLoader,
    )
}

@Composable
internal fun rememberHikariArtwork(
    model: HikariArtworkModel,
    imageLoader: ImageLoader,
): HikariArtworkState {
    val context = LocalPlatformContext.current
    val reduceMotion = LocalHikariMotionPolicy.current.reduceMotion
    val fallback = remember(model.stableKey, model.title) {
        fallbackFor(model.stableKey, model.title)
    }
    val cacheKey = remember(model.stableKey, model.url) {
        "hikari-artwork:${model.stableKey}:${model.url.orEmpty()}"
    }
    val request = remember(context, model.url, cacheKey, reduceMotion) {
        ImageRequest.Builder(context)
            .data(model.url)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .crossfade(!reduceMotion)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
        contentScale = ContentScale.Crop,
    )
    val painterState by painter.state.collectAsState()
    val loading = model.url != null && (
        painterState is AsyncImagePainter.State.Empty ||
            painterState is AsyncImagePainter.State.Loading
        )

    return remember(painter, fallback, loading) {
        HikariArtworkState(
            painter = painter,
            fallback = fallback,
            loading = loading,
        )
    }
}

@Composable
fun HikariArtwork(
    state: HikariArtworkState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    ArtworkLayer(
        state = state,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
fun HikariArtworkBackdrop(
    state: HikariArtworkState,
    modifier: Modifier = Modifier,
    scrim: Brush = HikariBackdropDefaults.scrim,
) {
    Box(modifier = modifier) {
        ArtworkLayer(
            state = state,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(scrim),
        )
    }
}

object HikariBackdropDefaults {
    val scrim: Brush
        @Composable
        get() = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.54f),
                Color.Black.copy(alpha = 0.88f),
            ),
        )
}

@Composable
private fun ArtworkLayer(
    state: HikariArtworkState,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    state.fallback.startColor,
                    state.fallback.endColor,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.fallback.monogram,
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Image(
            painter = state.painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
        )
    }
}
