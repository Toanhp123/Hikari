package app.openstory.reader.progress

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReadingProgressRepository {
    fun observeAll(): Flow<List<ReadingProgress>>
    fun observeForStories(storyIds: Set<StoryId>): Flow<List<ReadingProgress>> =
        observeAll().map { progress -> progress.filter { it.storyId in storyIds } }
    fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?>
    suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress?
    suspend fun save(progress: ReadingProgress)
}
