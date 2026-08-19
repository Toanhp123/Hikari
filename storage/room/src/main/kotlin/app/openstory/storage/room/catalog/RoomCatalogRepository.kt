package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.Outcome
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException

class RoomCatalogRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: CatalogDao,
    private val homeDao: CatalogHomeDao,
) : CatalogRepository {
    constructor(database: OpenStoryDatabase) : this(
        database,
        database.catalogDao(),
        database.catalogHomeDao(),
    )

    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = combine(
        homeDao.observeSnapshots(),
        homeDao.observeSections(),
        homeDao.observeItems(),
        dao.observeHomeEntries(),
    ) { snapshots, sections, items, storedEntries ->
        val sectionsByPlugin = sections.groupBy(CatalogHomeSectionEntity::pluginId)
        val itemsByPlugin = items.groupBy(CatalogHomeItemEntity::pluginId)
        val entries = storedEntries.associate { entry ->
            (entry.pluginId to entry.sourceId) to entry.toModel()
        }
        snapshots.map { snapshot ->
            snapshot.toModel(
                sectionsByPlugin[snapshot.pluginId].orEmpty(),
                itemsByPlugin[snapshot.pluginId].orEmpty(),
                entries,
            )
        }
    }

    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = combine(
        dao.observeStory(storyId.value),
        dao.observeEntries(storyId.value),
    ) { story, entries ->
        story?.toModel()?.let { value -> StoryCatalogSnapshot(value, entries.map(CatalogEntryEntity::toModel)) }
    }

    override suspend fun matchSnapshot(): CatalogMatchSnapshot = database.withTransaction {
        val storiesById = dao.stories().associateBy(StoryEntity::storyId)
        val candidates = dao.entries()
            .groupBy(CatalogEntryEntity::storyId)
            .toSortedMap()
            .mapNotNull { (storyId, entries) ->
                storiesById[storyId]?.let(entries::toCandidate)
            }
        CatalogMatchSnapshot(candidates)
    }

    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> =
        try {
            database.withTransaction {
                val sourceIds = mutation.entries.map(CatalogEntry::sourceId).distinct()
                val existing = if (sourceIds.isEmpty()) {
                    emptyMap()
                } else {
                    dao.entries(mutation.pluginId.value, sourceIds)
                        .associateBy { it.pluginId to it.sourceId }
                }
                dao.upsertStories(mutation.stories.map { it.toEntity() })
                dao.upsertEntries(
                    mutation.entries.map { entry ->
                        merge(existing[entry.pluginId.value to entry.sourceId], entry, mutation)
                    },
                )
                homeDao.deleteSections(mutation.pluginId.value)
                homeDao.upsertSnapshot(
                    app.openstory.storage.room.catalog.CatalogHomeSnapshotEntity(
                        mutation.pluginId.value,
                        mutation.pluginVersion,
                        mutation.refreshedAtEpochMillis,
                    ),
                )
                homeDao.upsertSections(mutation.sections.mapIndexed { index, section ->
                    CatalogHomeSectionEntity(
                        pluginId = mutation.pluginId.value,
                        sectionId = section.sourceId,
                        title = section.title,
                        position = index,
                        feedKind = section.kind.name,
                    )
                })
                homeDao.upsertItems(mutation.sections.flatMap { section ->
                    section.items.mapIndexed { index, entry ->
                        CatalogHomeItemEntity(mutation.pluginId.value, section.sourceId, index, entry.sourceId)
                    }
                })
            }
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Outcome.Failure(CatalogStoreFailure("catalog.home.commit_failed", retryable = true))
        }

    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> =
        try {
            database.withTransaction {
                if (dao.findStory(mutation.storyId.value) == null) {
                    dao.upsertStories(
                        listOf(StoryEntity(mutation.storyId.value, mutation.entry.contentType.name)),
                    )
                }
                val existing = dao.findEntry(mutation.entry.pluginId.value, mutation.entry.sourceId)
                dao.upsertEntries(
                    listOf(merge(existing, mutation.entry, mutation)),
                )
            }
            Outcome.Success(mutation.storyId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Outcome.Failure(CatalogStoreFailure("catalog.details.commit_failed", retryable = true))
        }

    private fun merge(existing: CatalogEntryEntity?, entry: CatalogEntry, mutation: CatalogHomeMutation) =
        merge(existing, entry.toEntity(mutation.pluginVersion, mutation.refreshedAtEpochMillis))

    private fun merge(existing: CatalogEntryEntity?, entry: CatalogEntry, mutation: CatalogDetailsMutation) =
        merge(existing, entry.toEntity(mutation.pluginVersion, mutation.fetchedAtEpochMillis))

    private fun merge(existing: CatalogEntryEntity?, incoming: CatalogEntryEntity): CatalogEntryEntity {
        if (existing == null) return incoming
        val latestUpdate = mergeLatestUpdate(
            existing.latestUpdateAtEpochMillis,
            existing.latestUpdateReleaseLabel,
            incoming.latestUpdateAtEpochMillis,
            incoming.latestUpdateReleaseLabel,
        )
        return existing.copy(
            storyId = incoming.storyId,
            title = incoming.title,
            aliases = incoming.aliases.ifEmpty { existing.aliases },
            authors = incoming.authors.ifEmpty { existing.authors },
            description = incoming.description ?: existing.description,
            genres = incoming.genres.ifEmpty { existing.genres },
            contentType = incoming.contentType,
            languageTags = incoming.languageTags.ifEmpty { existing.languageTags },
            coverUrl = incoming.coverUrl ?: existing.coverUrl,
            sourceUrl = incoming.sourceUrl ?: existing.sourceUrl,
            scoreValue = incoming.scoreValue ?: existing.scoreValue,
            scoreScale = incoming.scoreScale ?: existing.scoreScale,
            popularityRank = incoming.popularityRank ?: existing.popularityRank,
            publicationStatus = incoming.publicationStatus ?: existing.publicationStatus,
            latestUpdateAtEpochMillis = latestUpdate.first,
            latestUpdateReleaseLabel = latestUpdate.second,
            pluginVersion = incoming.pluginVersion,
            fetchedAtEpochMillis = incoming.fetchedAtEpochMillis,
        )
    }

    private fun mergeLatestUpdate(
        existingAt: Long?,
        existingLabel: String?,
        incomingAt: Long?,
        incomingLabel: String?,
    ): Pair<Long?, String?> = when {
        incomingAt == null -> existingAt to existingLabel
        existingAt == null -> incomingAt to incomingLabel
        incomingAt > existingAt -> incomingAt to incomingLabel
        else -> existingAt to existingLabel
    }
}
