package app.openstory.reader.progress

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

interface ReadingProgressRepository {
    fun observeAll(): Flow<List<ReadingProgress>>
    fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?>
    suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress?
    suspend fun save(progress: ReadingProgress)
}
