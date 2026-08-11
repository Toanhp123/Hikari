package app.openstory.chapters.repository

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.PluginId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

data class CanonicalChapterGroup(
    val chapter: CanonicalChapter,
    val releases: List<ChapterRelease>,
)

data class ChapterGraphSnapshot(
    val chapters: List<CanonicalChapter>,
    val releases: List<ChapterRelease>,
    val overrides: List<ChapterAggregationOverride>,
)

enum class ChapterSyncPhase {
    RECENT,
    FULL,
    INCREMENTAL,
}

data class ChapterSyncState(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceStoryId: String,
    val phase: ChapterSyncPhase,
    val cursor: String?,
    val checkpoint: String?,
    val fingerprint: String?,
    val updatedAtEpochMillis: Long,
)

interface ChapterRepository {
    fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>>

    suspend fun snapshot(storyId: StoryId): ChapterGraphSnapshot

    suspend fun commit(mutation: ChapterMutation): ChapterCommitResult

    suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride)

    suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String): ChapterSyncState?
}

fun interface ChapterReleaseLookup {
    suspend fun findRelease(releaseId: ChapterReleaseId): ChapterRelease?
}
