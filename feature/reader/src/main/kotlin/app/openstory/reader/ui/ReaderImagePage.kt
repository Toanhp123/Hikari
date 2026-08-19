package app.openstory.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.reader.document.ReaderBlock
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.CachePolicy
import coil3.request.ImageRequest

@Composable
internal fun ReaderImagePage(
    block: ReaderBlock.ImagePage,
    documentFingerprint: String,
    onReloadDocument: () -> Unit,
    onImageMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val sizeResolver = rememberConstraintsSizeResolver()
    val cacheKey = remember(documentFingerprint, block.id) {
        "reader-page:$documentFingerprint:${block.id}"
    }
    val request = remember(context, block.imageUrl, cacheKey, sizeResolver) {
        ImageRequest.Builder(context)
            .data(block.imageUrl)
            .size(sizeResolver)
            .memoryCacheKey(cacheKey)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        contentScale = ContentScale.FillWidth,
    )
    val state by painter.state.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.readerImagePlaceholderHeight)
            .then(sizeResolver)
            .testTag("reader-image-${block.id}"),
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
            is AsyncImagePainter.State.Error -> HikariInlineFeedback(
                message = "Page image unavailable",
                actionLabel = "Retry",
                onAction = onReloadDocument,
            )
            is AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> HikariLoadingState(label = "Loading page")
        }
    }
}
