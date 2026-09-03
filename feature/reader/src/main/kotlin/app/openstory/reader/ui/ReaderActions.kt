package app.openstory.reader.ui

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.assets.ReaderPageAssetRequest
import app.openstory.reader.assets.ReaderViewportSnapshot
import app.openstory.reader.progress.ReadingPosition

data class ReaderActions(
    val onRetry: () -> Unit = {},
    val onReleaseSelected: (ChapterReleaseId) -> Unit = {},
    val onPreviousChapter: (CanonicalChapterId) -> Unit = {},
    val onNextChapter: (CanonicalChapterId) -> Unit = {},
    val onIncreaseFont: () -> Unit = {},
    val onDecreaseFont: () -> Unit = {},
    val onPositionChanged: (ReadingPosition, Boolean) -> Unit = { _, _ -> },
    val onViewportChanged: (ReaderViewportSnapshot) -> Boolean = { false },
    val onAssetPresented: (ReaderPageAssetRequest) -> Unit = {},
    val onRouteInvalidated: (Long) -> Unit = {},
    val onFlushProgress: () -> Unit = {},
)
