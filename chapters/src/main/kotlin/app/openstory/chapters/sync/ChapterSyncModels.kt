package app.openstory.chapters.sync

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class ChapterSyncFailure(
    val pluginId: PluginId?,
    val code: String,
    val retryable: Boolean,
)

data class ChapterSyncSourceSuccess(
    val pluginId: PluginId,
)

sealed interface ChapterSyncReport {
    data class Success(val sources: List<ChapterSyncSourceSuccess>) : ChapterSyncReport

    data class Failure(val failures: List<ChapterSyncFailure>) : ChapterSyncReport
}

fun interface InitialChapterSyncScheduler {
    fun schedule(storyId: StoryId)
}
