package app.openstory.work

import app.openstory.common.id.StoryId

object WorkNames {
    const val LIBRARY_CHAPTER_PERIODIC = "library-chapter-periodic"
    const val LIBRARY_CHAPTER_CONTINUATION = "library-chapter-continuation"

    fun storyChapterSync(storyId: StoryId): String = "initial-chapter-sync:${storyId.value}"
}
