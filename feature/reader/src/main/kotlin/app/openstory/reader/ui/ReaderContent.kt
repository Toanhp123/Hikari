package app.openstory.reader.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openstory.reader.document.ReaderBlock
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.designsystem.theme.hikariTypography
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingPosition
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun ReaderContent(
    document: ReaderDocument,
    fontScale: Float,
    restoredBlockId: String?,
    restoredCharacterOffset: Int,
    contentPadding: PaddingValues,
    onPositionChanged: (ReadingPosition, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onToggleChrome: () -> Unit = {},
) {
    val titleOffset = if (document.title == null) 0 else 1
    val listState = rememberRestoredReaderState(
        document,
        titleOffset,
        restoredBlockId,
        restoredCharacterOffset,
    )
    TrackReaderProgress(document, titleOffset, listState, onPositionChanged)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onToggleChrome) { detectTapGestures(onTap = { onToggleChrome() }) },
        contentPadding = contentPadding,
    ) {
        document.title?.let { title ->
            item(key = "reader-title") {
                Text(
                    title,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.hikariSpacing.space20,
                        vertical = MaterialTheme.hikariSpacing.space16,
                    ).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * fontScale,
                    ),
                )
            }
        }
        items(document.blocks.size, key = { document.blocks[it].id }) { index ->
            ReaderBlock(document.blocks[index], fontScale)
        }
    }
}

@Composable
private fun rememberRestoredReaderState(
    document: ReaderDocument,
    titleOffset: Int,
    restoredBlockId: String?,
    restoredCharacterOffset: Int,
): LazyListState {
    val restoredIndex = restoredReaderItemIndex(
        document.blocks,
        hasTitle = titleOffset > 0,
        restoredBlockId,
    )
    val listState = rememberSaveable(
        document.fingerprint,
        restoredIndex,
        saver = LazyListState.Saver,
    ) {
        LazyListState(firstVisibleItemIndex = restoredIndex)
    }
    LaunchedEffect(document.fingerprint, restoredIndex, restoredCharacterOffset) {
        if (restoredCharacterOffset <= 0) return@LaunchedEffect
        val itemSize = snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == restoredIndex }?.size
        }.filterNotNull().first()
        val block = document.blocks.getOrNull(restoredIndex - titleOffset) ?: return@LaunchedEffect
        val characterCount = block.characterCount().coerceAtLeast(1)
        val scrollOffset = (restoredCharacterOffset.toFloat() / characterCount * itemSize)
            .roundToInt()
            .coerceIn(0, (itemSize - 1).coerceAtLeast(0))
        listState.scrollToItem(restoredIndex, scrollOffset)
    }
    return listState
}

@Composable
private fun TrackReaderProgress(
    document: ReaderDocument,
    titleOffset: Int,
    listState: LazyListState,
    onPositionChanged: (ReadingPosition, Boolean) -> Unit,
) {
    LaunchedEffect(document.fingerprint, listState) {
        snapshotFlow { listState.viewport(document.blocks.lastIndex + titleOffset) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { viewport ->
                val update = viewport.progress(document, titleOffset) ?: return@collect
                onPositionChanged(update, viewport.reachedEnd)
            }
    }
}

@Composable
private fun ReaderBlock(block: ReaderBlock, fontScale: Float) {
    when (block) {
        is ReaderBlock.Paragraph -> Text(
            block.text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.space20,
                vertical = MaterialTheme.hikariSpacing.space12,
            ),
            style = MaterialTheme.hikariTypography.readerBody.let { style ->
                style.copy(
                    fontSize = style.fontSize * fontScale,
                    lineHeight = style.lineHeight * fontScale,
                )
            },
        )
        is ReaderBlock.Heading -> Text(
            block.text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.space20,
                vertical = MaterialTheme.hikariSpacing.space16,
            ).semantics { heading() },
            style = headingStyle(block.level, fontScale),
        )
        is ReaderBlock.Divider -> HorizontalDivider(
            Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.space32,
                vertical = MaterialTheme.hikariSpacing.space16,
            ),
        )
        is ReaderBlock.Note -> Text(
            block.text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.space32,
                vertical = MaterialTheme.hikariSpacing.space12,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.let { style ->
                style.copy(
                    fontSize = style.fontSize * fontScale,
                    lineHeight = style.lineHeight * fontScale,
                )
            },
        )
    }
}

@Composable
private fun headingStyle(level: Int, fontScale: Float) = when (level) {
    1 -> MaterialTheme.typography.headlineLarge
    2 -> MaterialTheme.typography.headlineMedium
    HEADING_LEVEL_THREE -> MaterialTheme.typography.headlineSmall
    else -> MaterialTheme.typography.titleLarge
}.let { style -> style.copy(fontSize = style.fontSize * fontScale) }

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

private fun ReaderBlock.characterCount(): Int = when (this) {
    is ReaderBlock.Paragraph -> text.length
    is ReaderBlock.Heading -> text.length
    is ReaderBlock.Divider -> 0
    is ReaderBlock.Note -> text.length
}

internal fun restoredReaderItemIndex(
    blocks: List<ReaderBlock>,
    hasTitle: Boolean,
    restoredBlockId: String?,
): Int {
    val blockIndex = blocks.indexOfFirst { block -> block.id == restoredBlockId }
    return if (blockIndex >= 0) blockIndex + if (hasTitle) 1 else 0 else 0
}

private const val HEADING_LEVEL_THREE = 3
