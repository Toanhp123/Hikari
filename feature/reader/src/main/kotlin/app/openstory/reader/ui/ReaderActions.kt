package app.openstory.reader.ui

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.progress.ReadingPosition

data class ReaderActions(
    val onRetry: () -> Unit = {},
    val onReleaseSelected: (ChapterReleaseId) -> Unit = {},
    val onPreviousChapter: (CanonicalChapterId) -> Unit = {},
    val onNextChapter: (CanonicalChapterId) -> Unit = {},
    val onIncreaseFont: () -> Unit = {},
    val onDecreaseFont: () -> Unit = {},
    val onPositionChanged: (ReadingPosition, Boolean) -> Unit = { _, _ -> },
    val onFlushProgress: () -> Unit = {},
)
