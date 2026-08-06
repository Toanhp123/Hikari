package app.openstory.database.repository

import app.openstory.common.AppResult
import app.openstory.model.CanonicalStory
import app.openstory.model.ChapterRelease
import app.openstory.model.ContentMappingId
import app.openstory.model.LibraryEntry
import app.openstory.model.LibraryStatus
import app.openstory.model.ReadingProgress
import app.openstory.model.StoryId
import kotlinx.coroutines.flow.Flow

interface LocalStoryRepository {

    fun observeStory(
        id: StoryId,
    ): Flow<CanonicalStory?>

    fun observeLibrary():
        Flow<List<LibraryEntry>>

    suspend fun addToLibrary(
        story: CanonicalStory,
        status: LibraryStatus,
    ): AppResult<Unit>

    suspend fun purgeStory(
        storyId: StoryId,
    ): AppResult<Unit>

    suspend fun replaceSourceReleases(
        mappingId: ContentMappingId,
        releases: List<ChapterRelease>,
    ): AppResult<Unit>

    suspend fun upsertProgress(
        progress: ReadingProgress,
    ): AppResult<Unit>
}
