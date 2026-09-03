package app.openstory.reader.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.openstory.reader.assets.ReaderViewportDirection
import app.openstory.reader.assets.ReaderViewportSnapshot
import app.openstory.reader.document.ReaderDocument
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun TrackReaderAssetViewport(
    document: ReaderDocument,
    assets: ReaderAssetUiState,
    listState: LazyListState,
    onViewportChanged: (ReaderViewportSnapshot) -> Boolean,
    onViewportObserved: (ReaderViewportSnapshot?) -> Unit,
    onViewportAccepted: (ReaderViewportSnapshot) -> Unit,
) {
    val blockIndexById = remember(document.fingerprint) {
        document.blocks.mapIndexed { index, block -> block.id to index }.toMap()
    }
    val latestViewportChanged by rememberUpdatedState(onViewportChanged)
    val latestViewportObserved by rememberUpdatedState(onViewportObserved)
    val latestViewportAccepted by rememberUpdatedState(onViewportAccepted)
    LaunchedEffect(document.fingerprint, assets.manifest.sessionId, assets.manifestRevision, listState) {
        var previousAnchor: ReaderViewportAnchor? = null
        var initialPublished = false
        var lastObservedViewport: ReaderViewportSnapshot? = null
        var lastAcceptedViewport: ReaderViewportSnapshot? = null
        snapshotFlow {
            readerViewportObservation(
                blockIndexById = blockIndexById,
                blockCount = document.blocks.size,
                lastBlockId = document.blocks.lastOrNull()?.id,
                listState = listState,
            )
        }
            .distinctUntilChanged()
            .collectLatest { observation ->
                if (observation.visibleItemKeys.isEmpty()) {
                    if (lastObservedViewport != null) {
                        lastObservedViewport = null
                        latestViewportObserved(null)
                    }
                    return@collectLatest
                }
                val direction = readerViewportDirection(previousAnchor, observation.anchor)
                previousAnchor = observation.anchor
                val visibleImageOrdinals = readerVisibleImageOrdinalBounds(
                    observation.visibleItemKeys,
                    assets,
                )
                val snapshot = ReaderViewportSnapshot(
                    sessionId = assets.manifest.sessionId,
                    manifestRevision = assets.manifestRevision,
                    leadingVisibleImageOrdinal = visibleImageOrdinals?.first,
                    trailingVisibleImageOrdinal = visibleImageOrdinals?.last,
                    direction = direction,
                    chapterProgressBasisPoints = observation.chapterProgressBasisPoints,
                )
                if (!lastObservedViewport.hasSameVisibleAssetWindow(snapshot)) {
                    lastObservedViewport = snapshot
                    latestViewportObserved(snapshot)
                }
                val accepted = if (!initialPublished) {
                    initialPublished = true
                    latestViewportChanged(snapshot)
                } else {
                    delay(READER_VIEWPORT_DEBOUNCE_MILLIS)
                    latestViewportChanged(snapshot)
                }
                if (accepted && !lastAcceptedViewport.hasSameVisibleAssetWindow(snapshot)) {
                    lastAcceptedViewport = snapshot
                    latestViewportAccepted(snapshot)
                }
            }
    }
}

internal fun readerVisibleImageOrdinalBounds(
    visibleItemKeys: List<String>,
    assets: ReaderAssetUiState,
): IntRange? {
    val ordinals = visibleItemKeys
        .asSequence()
        .mapNotNull(assets::requestForBlockId)
        .map { request -> request.descriptor.imageOrdinal }
        .toList()
    if (ordinals.isEmpty()) return null
    return ordinals.min()..ordinals.max()
}

private data class ReaderViewportAnchor(
    val itemIndex: Int,
    val itemScrollOffset: Int,
)

private data class ReaderViewportObservation(
    val visibleItemKeys: List<String>,
    val anchor: ReaderViewportAnchor,
    val chapterProgressBasisPoints: Int,
)

private fun readerViewportObservation(
    blockIndexById: Map<String, Int>,
    blockCount: Int,
    lastBlockId: String?,
    listState: LazyListState,
): ReaderViewportObservation {
    val layout = listState.layoutInfo
    val visible = layout.visibleItemsInfo
    val visibleKeys = visible.mapNotNull { item -> item.key as? String }
    return ReaderViewportObservation(
        visibleItemKeys = visibleKeys,
        anchor = ReaderViewportAnchor(
            itemIndex = listState.firstVisibleItemIndex,
            itemScrollOffset = listState.firstVisibleItemScrollOffset,
        ),
        chapterProgressBasisPoints = readerChapterProgressBasisPoints(
            blockIndexById = blockIndexById,
            blockCount = blockCount,
            lastBlockId = lastBlockId,
            listState = listState,
        ),
    )
}

private fun readerViewportDirection(
    previous: ReaderViewportAnchor?,
    current: ReaderViewportAnchor,
): ReaderViewportDirection = when {
    previous == null -> ReaderViewportDirection.IDLE
    current.itemIndex > previous.itemIndex -> ReaderViewportDirection.FORWARD
    current.itemIndex < previous.itemIndex -> ReaderViewportDirection.BACKWARD
    current.itemScrollOffset > previous.itemScrollOffset -> ReaderViewportDirection.FORWARD
    current.itemScrollOffset < previous.itemScrollOffset -> ReaderViewportDirection.BACKWARD
    else -> ReaderViewportDirection.IDLE
}

private fun readerChapterProgressBasisPoints(
    blockIndexById: Map<String, Int>,
    blockCount: Int,
    lastBlockId: String?,
    listState: LazyListState,
): Int {
    if (blockCount == 0) return 0
    val layout = listState.layoutInfo
    val visible = layout.visibleItemsInfo
    val firstBlockItem = visible.firstOrNull { item -> (item.key as? String) in blockIndexById }
        ?: return 0
    val blockIndex = blockIndexById[firstBlockItem.key as String] ?: return 0
    val lastBlockItem = visible.lastOrNull { item -> (item.key as? String) in blockIndexById }
    val reachedEnd = lastBlockItem != null &&
        lastBlockItem.key == lastBlockId &&
        lastBlockItem.offset + lastBlockItem.size <= layout.viewportEndOffset
    if (reachedEnd) return BASIS_POINTS

    val withinBlock = (-firstBlockItem.offset).coerceAtLeast(0).toFloat() /
        firstBlockItem.size.coerceAtLeast(1)
    return (((blockIndex + withinBlock.coerceIn(0f, 1f)) / blockCount) * BASIS_POINTS)
        .roundToInt()
        .coerceIn(0, BASIS_POINTS)
}

internal fun ReaderViewportSnapshot?.hasSameVisibleAssetWindow(
    other: ReaderViewportSnapshot?,
): Boolean = when {
    this == null || other == null -> this == other
    else -> sessionId == other.sessionId &&
        manifestRevision == other.manifestRevision &&
        leadingVisibleImageOrdinal == other.leadingVisibleImageOrdinal &&
        trailingVisibleImageOrdinal == other.trailingVisibleImageOrdinal
}

internal const val READER_VIEWPORT_DEBOUNCE_MILLIS = 50L
private const val BASIS_POINTS = 10_000
