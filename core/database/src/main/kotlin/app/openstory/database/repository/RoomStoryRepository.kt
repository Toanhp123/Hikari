package app.openstory.database.repository

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.database.OpenStoryDatabase
import app.openstory.database.entity.CanonicalChapterReleaseEntity
import app.openstory.database.entity.ReadingProgressEntity
import app.openstory.database.entity.StoryCatalogEntryEntity
import app.openstory.database.mapping.toDomain
import app.openstory.database.mapping.toEntity
import app.openstory.model.CanonicalStory
import app.openstory.model.ChapterRelease
import app.openstory.model.ContentMappingId
import app.openstory.model.LibraryEntry
import app.openstory.model.LibraryStatus
import app.openstory.model.ReadingProgress
import app.openstory.model.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomStoryRepository(
    database: OpenStoryDatabase,
    private val clock: Clock = SystemClock,
) : LocalStoryRepository {

    private val storyDao =
        database.storyDao()

    private val chapterDao =
        database.chapterDao()

    private val progressDao =
        database.progressDao()

    override fun observeStory(
        id: StoryId,
    ): Flow<CanonicalStory?> =
        storyDao
            .observeStory(id.value)
            .map { aggregate ->
                aggregate?.toDomain()
            }
            .distinctUntilChanged()

    override fun observeLibrary():
        Flow<List<LibraryEntry>> =
        storyDao
            .observeLibrary()
            .map { entries ->
                entries.map { entry ->
                    entry.toDomain()
                }
            }
            .distinctUntilChanged()

    override suspend fun addToLibrary(
        story: CanonicalStory,
        status: LibraryStatus,
    ): AppResult<Unit> =
        storageWrite {
            val catalogEntries =
                story.catalogEntries.map { entry ->
                    entry.toEntity()
                }

            storyDao.addToLibrary(
                story = story.toEntity(),
                catalogEntries = catalogEntries,
                catalogLinks =
                    catalogEntries.map { entry ->
                        StoryCatalogEntryEntity(
                            storyId = story.id.value,
                            catalogEntryId =
                                entry.catalogEntryId,
                        )
                    },
                status = status.name,
                nowEpochMillis =
                    clock.nowEpochMillis(),
            )
        }

    override suspend fun replaceSourceReleases(
        mappingId: ContentMappingId,
        releases: List<ChapterRelease>,
    ): AppResult<Unit> =
        storageWrite {
            require(
                releases.all { release ->
                    release.contentMappingId ==
                        mappingId
                },
            ) {
                "All releases must belong to the replaced mapping"
            }

            chapterDao.replaceSourceReleases(
                mappingId = mappingId.value,
                releases =
                    releases.map { release ->
                        release.toEntity()
                    },
                links =
                    releases.map { release ->
                        CanonicalChapterReleaseEntity(
                            chapterId =
                                release.chapterId.value,
                            releaseId =
                                release.id.value,
                        )
                    },
            )
        }

    override suspend fun upsertProgress(
        progress: ReadingProgress,
    ): AppResult<Unit> =
        storageWrite {
            progressDao.upsertProgress(
                ReadingProgressEntity(
                    storyId = progress.storyId.value,
                    chapterId = progress.chapterId.value,
                    releaseId =
                        progress.releaseId?.value,
                    position = progress.position,
                    completed = progress.completed,
                    updatedAtEpochMillis =
                        progress.updatedAtEpochMillis,
                ),
            )
        }

    private suspend fun storageWrite(
        block: suspend () -> Unit,
    ): AppResult<Unit> =
        try {
            block()
            AppResult.Success(Unit)
        }
        catch (_: Exception) {
            AppResult.Failure(
                AppError.Storage(
                    code = "storage.write_failed",
                    retryable = true,
                ),
            )
        }
}
