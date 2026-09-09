package app.openstory.catalog.storage

import androidx.room.withTransaction
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.validation.CatalogPublicationValidator
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.CatalogMutationBounds
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.storage.story.StoryArtistEntity
import app.openstory.catalog.storage.story.StoryAuthorEntity
import app.openstory.catalog.storage.story.StoryDetailEntity
import app.openstory.catalog.storage.story.StoryDetailRecord
import app.openstory.catalog.storage.story.StoryGenreEntity
import app.openstory.catalog.storage.story.matches
import app.openstory.catalog.storage.story.toIdentityEntity
import app.openstory.catalog.storage.retention.StoryRetentionStorage
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class StoryStorage(
    private val database: CatalogDatabase,
) {
    private val identityDao = database.discoverDao()
    private val storyDao = database.storyDetailDao()
    private val retentionDao = database.storyRetentionDao()
    private val retentionStorage = StoryRetentionStorage(retentionDao)

    fun observe(ref: StorySourceRef): Flow<StoryDetailProjection?> = database.invalidationTracker
        .createFlow(*STORY_OBSERVED_TABLES)
        .map { readStory(ref)?.toDomainProjection() }
        .catch { error -> throw error.toStorageFailure(CatalogStorageOperation.READ_STORY) }

    private suspend fun readStory(ref: StorySourceRef): StoryDetailRecord? = database.withTransaction {
        val storyId = ref.storyId.value
        storyDao.storyRow(
            storyId = storyId,
            sourceKey = ref.catalogSourceKey.value,
            sourceStoryId = ref.sourceStoryId,
        )?.let { row ->
            StoryDetailRecord(
                row = row,
                authors = storyDao.authors(storyId),
                artists = storyDao.artists(storyId),
                genres = storyDao.genres(storyId),
            )
        }
    }

    suspend fun publishStoryDetail(command: StoryDetailPublicationCommand) {
        val detail = command.detail.snapshot()
        validateDetailPublication(command, detail)
        runCatching {
            database.withTransaction {
                ensureIdentity(command.ref)
                val storedContentType = storyDao.summaryContentType(command.ref.storyId.value)
                if (storedContentType != null && storedContentType != command.summary.contentType.name) {
                    throwValidation("summary.contentType", CatalogValidationReason.AUTHORITY_MISMATCH)
                }
                storyDao.upsertSummary(
                    command.summary.toSummaryEntity(command.provenance.acquiredAtEpochMs),
                )
                val previousAccess = storyDao.lastAccessedEpochMs(command.ref.storyId.value)
                storyDao.upsertDetail(
                    StoryDetailEntity(
                        storyId = command.ref.storyId.value,
                        sourceKey = command.ref.catalogSourceKey.value,
                        sourceStoryId = command.ref.sourceStoryId,
                        sourceVersion = command.provenance.sourceVersion,
                        description = detail.description,
                        publicationStatus = detail.publicationStatus,
                        language = detail.language,
                        fetchedAtEpochMs = command.provenance.acquiredAtEpochMs,
                        lastAccessedEpochMs = maxOf(
                            previousAccess ?: command.provenance.acquiredAtEpochMs,
                            command.provenance.acquiredAtEpochMs,
                        ),
                    ),
                )
                replaceChildren(command.ref.storyId.value, detail)
                retentionDao.touchExistingOrphan(
                    command.ref.storyId.value,
                    command.provenance.acquiredAtEpochMs,
                )
            }
        }.getOrElse { error ->
            throw error.toStorageFailure(CatalogStorageOperation.PUBLISH_STORY)
        }
    }

    suspend fun touchStoryAccess(ref: StorySourceRef, accessedAtEpochMs: Long) {
        runCatching {
            database.withTransaction {
                requireMatchingIdentity(ref)
                storyDao.touchAccess(ref.storyId.value, accessedAtEpochMs)
                retentionDao.touchExistingOrphan(ref.storyId.value, accessedAtEpochMs)
            }
        }.getOrElse { error ->
            throw error.toStorageFailure(CatalogStorageOperation.RETENTION)
        }
    }

    suspend fun releaseStoryDemand(
        ref: StorySourceRef,
        retentionProtectedStoryIds: Set<StoryId>,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics {
        requireProtectedStoryBound(retentionProtectedStoryIds)
        return runCatching {
            database.withTransaction {
                val identityExists = matchingIdentityExists(ref)
                val touched = linkedSetOf(ref.storyId)
                retentionStorage.classify(
                    storyId = ref.storyId,
                    identityExists = identityExists,
                    protectedStoryIds = retentionProtectedStoryIds,
                    lastAccessedEpochMs = releasedAtEpochMs,
                )
                retentionStorage.evictOneOverflow(retentionProtectedStoryIds)?.let(touched::add)
                retentionStorage.requireWithinLimit()
                if (touched.size > CatalogMutationBounds.MAX_RELEASE_TOUCHED_STORY_IDS) {
                    throw CatalogFailureException(
                        CatalogFailure.InternalInvariant("release_mutation_touch_limit"),
                    )
                }
                CatalogMutationDiagnostics(touched)
            }
        }.getOrElse { error ->
            throw error.toStorageFailure(CatalogStorageOperation.RETENTION)
        }
    }

    private suspend fun ensureIdentity(ref: StorySourceRef) {
        if (!matchingIdentityExists(ref)) {
            identityDao.insertIdentity(ref.toIdentityEntity())
            val persisted = identityDao.identityByStoryId(ref.storyId.value)
            if (persisted == null || !persisted.matches(ref)) throwIdentityCollision(ref.storyId)
        }
    }

    private suspend fun requireMatchingIdentity(ref: StorySourceRef) {
        matchingIdentityExists(ref)
    }

    private suspend fun matchingIdentityExists(ref: StorySourceRef): Boolean {
        val byStoryId = identityDao.identityByStoryId(ref.storyId.value)
        if (byStoryId != null && !byStoryId.matches(ref)) throwIdentityCollision(ref.storyId)
        val bySourceKey = identityDao.identityBySourceStoryKey(ref.catalogSourceKey.value, ref.sourceStoryId)
        if (bySourceKey != null && bySourceKey.storyId != ref.storyId.value) throwIdentityCollision(ref.storyId)
        return byStoryId != null
    }

    private suspend fun replaceChildren(storyId: String, detail: StoryRichDetailProjection) {
        storyDao.deleteAuthors(storyId)
        storyDao.deleteArtists(storyId)
        storyDao.deleteGenres(storyId)
        storyDao.insertAuthors(detail.authors.mapIndexed { position, value ->
            StoryAuthorEntity(storyId, position, value)
        })
        storyDao.insertArtists(detail.artists.mapIndexed { position, value ->
            StoryArtistEntity(storyId, position, value)
        })
        storyDao.insertGenres(detail.genres.mapIndexed { position, value ->
            StoryGenreEntity(storyId, position, value)
        })
    }

    private companion object {
        val STORY_OBSERVED_TABLES = arrayOf(
            "story_source_identity",
            "story_source_summary",
            "story_detail",
            "story_author",
            "story_artist",
            "story_genre",
        )
    }
}

private fun StoryRichDetailProjection.snapshot() = copy(
    authors = authors.toList(),
    artists = artists.toList(),
    genres = genres.toList(),
)

private fun validateDetailPublication(
    command: StoryDetailPublicationCommand,
    detail: StoryRichDetailProjection,
) {
    if (command.provenance.catalogSourceKey != command.ref.catalogSourceKey) {
        throwValidation("provenance.catalogSourceKey", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
    if (command.summary.ref != command.ref) {
        throwValidation("summary.ref", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
    if (command.summary.sourceVersion != command.provenance.sourceVersion) {
        throwValidation("summary.sourceVersion", CatalogValidationReason.AUTHORITY_MISMATCH)
    }
    CatalogPublicationValidator.requireValidStoryDetail(command.summary, detail)
}
