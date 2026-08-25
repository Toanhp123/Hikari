package app.openstory.work

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

object WorkInput {
    const val STORY_ID = "story_id"
    const val CHAPTER_RELEASE_ID = "release_id"
    const val CHAPTER_SYNC_CURSOR = "chapter_sync_cursor"
    const val CHAPTER_SYNC_CURSOR_V1 = "chapter_sync_cursor_v1"

    fun storyId(value: String?): Result<StoryId> = parse(value, ::StoryId)

    fun chapterReleaseId(value: String?): Result<ChapterReleaseId> =
        parse(value, ::ChapterReleaseId)

    private fun <T> parse(value: String?, factory: (String) -> T): Result<T> = runCatching {
        factory(requireNotNull(value) { "Work input is missing" })
    }
}
