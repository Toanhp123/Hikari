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
            ReaderBlock(document.blocks[index], textStyles)
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
private fun ReaderBlock(
    block: ReaderBlock,
    textStyles: ReaderTextStyles,
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

private fun ReaderBlock.contentType(): String = when (this) {
    is ReaderBlock.Paragraph -> "reader-paragraph"
    is ReaderBlock.Heading -> "reader-heading"
    is ReaderBlock.Divider -> "reader-divider"
    is ReaderBlock.Note -> "reader-note"
}
