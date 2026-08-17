package app.openstory.reader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingPosition
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun TrackReaderProgress(
    document: ReaderDocument,
    titleOffset: Int,
    listState: LazyListState,
    onPositionChanged: (ReadingPosition, Boolean) -> Unit,
) {
    LaunchedEffect(document.fingerprint, listState) {
        var hasActiveScrollSession = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (scrolling) {
                    hasActiveScrollSession = true
                    while (listState.isScrollInProgress) {
                        reportReaderProgress(document, titleOffset, listState, onPositionChanged)
                        delay(READER_PROGRESS_SAMPLE_MILLIS)
                    }
                } else if (hasActiveScrollSession) {
                    reportReaderProgress(document, titleOffset, listState, onPositionChanged)
                    hasActiveScrollSession = false
                }
            }
    }
}

private fun reportReaderProgress(
    document: ReaderDocument,
    titleOffset: Int,
    listState: LazyListState,
    onPositionChanged: (ReadingPosition, Boolean) -> Unit,
) {
    val viewport = listState.viewport(document.blocks.lastIndex + titleOffset) ?: return
    val update = viewport.progress(document, titleOffset) ?: return
    onPositionChanged(update, viewport.reachedEnd)
}

private data class ReaderViewport(
    val itemIndex: Int,
    val itemOffset: Int,
    val itemSize: Int,
    val reachedEnd: Boolean,
)

private fun LazyListState.viewport(lastItemIndex: Int): ReaderViewport? {
    val layout = layoutInfo
    val first = layout.visibleItemsInfo.firstOrNull() ?: return null
    val last = layout.visibleItemsInfo.lastOrNull()
    return ReaderViewport(
        first.index,
        first.offset,
        first.size,
        last?.index == lastItemIndex && last.offset + last.size <= layout.viewportEndOffset,
    )
}

private fun ReaderViewport.progress(
    document: ReaderDocument,
    titleOffset: Int,
): ReadingPosition? {
    val blockIndex = (itemIndex - titleOffset).coerceAtLeast(0)
    val block = document.blocks.getOrNull(blockIndex) ?: return null
    val blockCharacters = block.characterCount()
    val characterOffset = if (itemIndex < titleOffset) {
        0
    } else {
        (
            (-itemOffset).coerceAtLeast(0).toFloat() /
                itemSize.coerceAtLeast(1) * blockCharacters
            ).roundToInt().coerceIn(0, blockCharacters)
    }
    val fraction = if (reachedEnd) {
        1f
    } else {
        val withinBlock = characterOffset.toFloat() / blockCharacters.coerceAtLeast(1)
        ((blockIndex + withinBlock) / document.blocks.size).coerceIn(0f, 1f)
    }
    return ReadingPosition(block.id, characterOffset, fraction)
}

internal fun ReaderBlock.characterCount(): Int = when (this) {
    is ReaderBlock.Paragraph -> text.length
    is ReaderBlock.Heading -> text.length
    is ReaderBlock.Divider -> 0
    is ReaderBlock.Note -> text.length
}

internal const val READER_PROGRESS_SAMPLE_MILLIS = 100L
