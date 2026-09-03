package app.openstory.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.reader.assets.ReaderAssetFailure
import app.openstory.reader.assets.ReaderAssetLoadException
import app.openstory.reader.assets.ReaderPageAssetRequest
import app.openstory.reader.assets.ReaderViewportSnapshot
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.CachePolicy
import coil3.request.ImageRequest

@Composable
internal fun ReaderImagePage(
    request: ReaderPageAssetRequest,
    visibleViewport: ReaderViewportSnapshot?,
    acceptedViewport: ReaderViewportSnapshot?,
    isActuallyVisible: (ReaderPageAssetRequest) -> Boolean,
    onAssetPresented: (ReaderPageAssetRequest) -> Unit,
    onRouteInvalidated: (Long) -> Unit,
    onImageMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val sizeResolver = rememberConstraintsSizeResolver()
    val cacheKey = remember(request.descriptor.key.hash.value) { readerAssetMemoryCacheKey(request) }
    val imageRequest = remember(context, request, cacheKey, sizeResolver) {
        ImageRequest.Builder(context)
            .data(request)
            .size(sizeResolver)
            .memoryCacheKey(cacheKey)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = imageRequest,
        contentScale = ContentScale.FillWidth,
    )
    val state by painter.state.collectAsState()
    val latestActuallyVisible by rememberUpdatedState(isActuallyVisible)
    val latestPresented by rememberUpdatedState(onAssetPresented)
    val latestRouteInvalidated by rememberUpdatedState(onRouteInvalidated)

    LaunchedEffect(state, request, visibleViewport, acceptedViewport) {
        if (
            state is AsyncImagePainter.State.Success &&
            visibleViewport.matches(request) &&
            acceptedViewport.matches(request) &&
            latestActuallyVisible(request)
        ) {
            latestPresented(request)
        }
    }

    val failure = (state as? AsyncImagePainter.State.Error)
        ?.result
        ?.throwable
        ?.readerAssetFailure()
    val failureAction = readerImageFailureAction(failure)
    LaunchedEffect(failureAction, request.manifestRevision) {
        if (failureAction == ReaderImageFailureAction.RELOAD_ROUTE) {
            latestRouteInvalidated(request.manifestRevision)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.readerImagePlaceholderHeight)
            .then(sizeResolver)
            .testTag("reader-image-${request.descriptor.uiBlockId}"),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is AsyncImagePainter.State.Success -> Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        if (size.height > 0) onImageMeasured(size.height)
                    },
            )
            is AsyncImagePainter.State.Error -> when (failureAction) {
                ReaderImageFailureAction.WAIT_FOR_REPLACEMENT,
                ReaderImageFailureAction.RELOAD_ROUTE -> HikariLoadingState(label = "Loading page")
                ReaderImageFailureAction.RETRY_PAGE -> HikariInlineFeedback(
                    message = "Page image unavailable",
                    actionLabel = "Retry",
                    onAction = painter::restart,
                )
            }
            is AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> HikariLoadingState(label = "Loading page")
        }
    }
}

internal fun readerAssetMemoryCacheKey(request: ReaderPageAssetRequest): String =
    "reader-asset:${request.descriptor.key.hash.value}"

internal enum class ReaderImageFailureAction {
    RETRY_PAGE,
    WAIT_FOR_REPLACEMENT,
    RELOAD_ROUTE,
}

internal fun readerImageFailureAction(failure: ReaderAssetFailure?): ReaderImageFailureAction = when (failure) {
    ReaderAssetFailure.Superseded -> ReaderImageFailureAction.WAIT_FOR_REPLACEMENT
    ReaderAssetFailure.RouteInvalidated -> ReaderImageFailureAction.RELOAD_ROUTE
    else -> ReaderImageFailureAction.RETRY_PAGE
}

private fun ReaderViewportSnapshot?.matches(request: ReaderPageAssetRequest): Boolean =
    this != null &&
        sessionId == request.sessionId &&
        manifestRevision == request.manifestRevision &&
        contains(request.descriptor.imageOrdinal)

private fun Throwable.readerAssetFailure(): ReaderAssetFailure? {
    var current: Throwable? = this
    while (current != null) {
        if (current is ReaderAssetLoadException) return current.failure
        current = current.cause
    }
    return null
}
