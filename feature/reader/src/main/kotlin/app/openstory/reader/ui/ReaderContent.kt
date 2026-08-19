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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openstory.reader.document.ReaderBlock
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingPosition
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun ReaderContent(
    document: ReaderDocument,
    fontScale: Float,
    restoredBlockId: String?,
    restoredCharacterOffset: Int,
    restoredProgressFraction: Float = 0f,
    contentPadding: PaddingValues,
    onPositionChanged: (ReadingPosition, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onToggleChrome: () -> Unit = {},
    onReloadDocument: () -> Unit = {},
) {
    val titleOffset = if (document.title == null) 0 else 1
    val measuredImageHeights = remember(document.fingerprint) { mutableStateMapOf<String, Int>() }
    val restoredImageHeight = document.blocks
        .firstOrNull { block -> block.id == restoredBlockId }
        ?.let { block -> (block as? ReaderBlock.ImagePage)?.let { measuredImageHeights[it.id] } }
    val listState = rememberRestoredReaderState(
        document,
        titleOffset,
        restoredBlockId,
        restoredCharacterOffset,
        restoredProgressFraction,
        restoredImageHeight,
    )
    val textStyles = rememberReaderTextStyles(fontScale)
    TrackReaderProgress(document, titleOffset, listState, onPositionChanged)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onToggleChrome) { detectTapGestures(onTap = { onToggleChrome() }) },
        contentPadding = contentPadding,
    ) {
        document.title?.let { title ->
            item(key = "reader-title", contentType = "reader-title") {
                Text(
                    title,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.hikariSpacing.space20,
                        vertical = MaterialTheme.hikariSpacing.space16,
                    ).semantics { heading() },
                    style = textStyles.title,
                )
            }
        }
        items(
            count = document.blocks.size,
            key = { document.blocks[it].id },
            contentType = { document.blocks[it].contentType() },
        ) { index ->
            ReaderBlock(
                block = document.blocks[index],
                textStyles = textStyles,
                documentFingerprint = document.fingerprint,
                onReloadDocument = onReloadDocument,
                onImageMeasured = { blockId, height -> measuredImageHeights[blockId] = height },
            )
        }
    }
}

@Composable
private fun rememberRestoredReaderState(
    document: ReaderDocument,
    titleOffset: Int,
    restoredBlockId: String?,
    restoredCharacterOffset: Int,
    restoredProgressFraction: Float,
    restoredImageHeight: Int?,
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
    LaunchedEffect(
        document.fingerprint,
        restoredIndex,
        restoredBlockId,
        restoredCharacterOffset,
        restoredProgressFraction,
        restoredImageHeight,
    ) {
        val blockIndex = document.blocks.indexOfFirst { block -> block.id == restoredBlockId }
        if (blockIndex < 0) return@LaunchedEffect
        val block = document.blocks[blockIndex]
        val itemSize: Int
        val withinBlock: Float
        if (block is ReaderBlock.ImagePage) {
            itemSize = restoredImageHeight ?: return@LaunchedEffect
            withinBlock = restoredImagePageFraction(
                blockIndex = blockIndex,
                blockCount = document.blocks.size,
                documentFraction = restoredProgressFraction,
            )
        } else {
            if (restoredCharacterOffset <= 0) return@LaunchedEffect
            itemSize = snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == restoredIndex }?.size
            }.filterNotNull().first()
            withinBlock = restoredCharacterOffset.toFloat() / block.progressExtent().coerceAtLeast(1)
        }
        val scrollOffset = (withinBlock * itemSize)
            .roundToInt()
            .coerceIn(0, (itemSize - 1).coerceAtLeast(0))
        listState.scrollToItem(restoredIndex, scrollOffset)
    }
    return listState
}

@Composable
private fun ReaderBlock(
    block: ReaderBlock,
    textStyles: ReaderTextStyles,
    documentFingerprint: String,
    onReloadDocument: () -> Unit,
    onImageMeasured: (String, Int) -> Unit,
) {
    when (block) {
        is ReaderBlock.Paragraph -> Text(
            block.text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.space20,
                vertical = MaterialTheme.hikariSpacing.space12,
            ),
            style = textStyles.paragraph,
        )
        is ReaderBlock.Heading -> Text(
            block.text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.hikariSpacing.space20,
                vertical = MaterialTheme.hikariSpacing.space16,
            ).semantics { heading() },
            style = textStyles.heading(block.level),
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
            style = textStyles.note,
        )
        is ReaderBlock.ImagePage -> ReaderImagePage(
            block = block,
            documentFingerprint = documentFingerprint,
            onReloadDocument = onReloadDocument,
            onImageMeasured = { height -> onImageMeasured(block.id, height) },
        )
    }
}

internal fun restoredReaderItemIndex(
    blocks: List<ReaderBlock>,
    hasTitle: Boolean,
    restoredBlockId: String?,
): Int {
    val blockIndex = blocks.indexOfFirst { block -> block.id == restoredBlockId }
    return if (blockIndex >= 0) blockIndex + if (hasTitle) 1 else 0 else 0
}

internal fun restoredImagePageFraction(
    blockIndex: Int,
    blockCount: Int,
    documentFraction: Float,
): Float {
    if (blockCount <= 0 || blockIndex !in 0 until blockCount) return 0f
    return (documentFraction.coerceIn(0f, 1f) * blockCount - blockIndex).coerceIn(0f, 1f)
}

private fun ReaderBlock.contentType(): String = when (this) {
    is ReaderBlock.Paragraph -> "reader-paragraph"
    is ReaderBlock.Heading -> "reader-heading"
    is ReaderBlock.Divider -> "reader-divider"
    is ReaderBlock.Note -> "reader-note"
    is ReaderBlock.ImagePage -> "reader-image-page"
}
