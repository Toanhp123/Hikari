package app.openstory.catalog.ui.components

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

data class ReaderTarget(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
)
