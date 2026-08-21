package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.evidence.toSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
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
    private val canonicalDao: CanonicalCatalogDao,
) : CatalogRepository {
    constructor(database: OpenStoryDatabase) : this(
        database,
        database.catalogDao(),
        database.catalogHomeDao(),
        database.canonicalCatalogDao(),
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
        val entries = dao.entries()
        val identifiersBySource = entries.associate { entry ->
            (entry.pluginId to entry.sourceId) to dao.identifiers(entry.pluginId, entry.sourceId)
                .map(CatalogEntryIdentifierEntity::toModel)
                .toSet()
        }
        val candidates = entries
            .groupBy(CatalogEntryEntity::storyId)
            .toSortedMap()
            .mapNotNull { (storyId, sourceEntries) ->
                storiesById[storyId]?.let { story -> sourceEntries.toCandidate(story, identifiersBySource) }
            }
        CatalogMatchSnapshot(candidates)
    }

    override suspend fun metadataSnapshot(
        key: CatalogMetadataKey,
    ): CatalogMetadataSnapshot? = database.withTransaction {
        val entry = dao.findEntry(key.pluginId.value, key.sourceId) ?: return@withTransaction null
        entry.toMetadataSnapshot(identifierModels(entry))
    }

    override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = database.withTransaction {
        val entry = dao.findEntry(key.pluginId.value, key.sourceId) ?: return@withTransaction null
        entry.toMetadataSnapshot(identifierModels(entry)).toSourceRecord()
    }

    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = database.withTransaction {
        dao.entriesForStory(storyId.value).map { entry ->
            entry.toMetadataSnapshot(identifierModels(entry)).toSourceRecord()
        }
    }

    override suspend fun sourceRecords(): List<CatalogSourceRecord> = database.withTransaction {
        dao.allEntries().map { entry ->
            entry.toMetadataSnapshot(identifierModels(entry)).toSourceRecord()
        }
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
                val durableEntries = mutation.entries.map { entry ->
                    merge(existing[entry.pluginId.value to entry.sourceId], entry, mutation)
                }
                val durableStoryIds = durableEntries.mapTo(hashSetOf(), CatalogEntryEntity::storyId)
                val newStoryIds = durableStoryIds.filterTo(linkedSetOf()) { storyId ->
                    dao.findStory(storyId) == null
                }
                dao.upsertStories(
                    mutation.stories
                        .filter { it.id.value in durableStoryIds }
                        .map { it.toEntity() },
                )
                newStoryIds.forEach { storyId ->
                    ensureCanonicalStateForNewStory(storyId, mutation.refreshedAtEpochMillis)
                }
                dao.upsertEntries(durableEntries)
                for (entry in mutation.entries) replaceIdentifiers(entry)
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


    override suspend fun commitSearchSummaries(
        mutation: CatalogSearchSummaryMutation,
    ): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure> = try {
        val result = database.withTransaction {
            val ownership = linkedMapOf<SourceKey, StoryId>()
            val affectedStoryIds = linkedSetOf<StoryId>()
            val storiesById = mutation.stories.associateBy { it.id }
            mutation.entries.sortedWith(compareBy({ it.sourceId })).forEach { proposedEntry ->
                val key = SourceKey(proposedEntry.pluginId, proposedEntry.sourceId)
                val existing = dao.findEntry(key.pluginId.value, key.sourceId)
                val durableStoryId = StoryId(existing?.storyId ?: proposedEntry.storyId.value)
                if (dao.findStory(durableStoryId.value) == null) {
                    val story = storiesById[proposedEntry.storyId]
                        ?: error("Missing proposed Story for ${proposedEntry.storyId.value}")
                    dao.upsertStories(listOf(story.copy(id = durableStoryId).toEntity()))
                    ensureCanonicalStateForNewStory(durableStoryId.value, mutation.resolvedAtEpochMillis)
                }
                val durableEntry = proposedEntry.copy(storyId = durableStoryId)
                val incoming = durableEntry.toHomeEntity(mutation.pluginVersion, mutation.resolvedAtEpochMillis)
                dao.upsertEntries(listOf(mergeHome(existing, incoming)))
                replaceIdentifiers(durableEntry)
                ownership[key] = durableStoryId
                affectedStoryIds += durableStoryId
            }
            affectedStoryIds.forEach { storyId ->
                canonicalDao.upsertWork(
                    CanonicalEngineWorkEntity(
                        storyId = storyId.value,
                        workType = CanonicalEngineWorkType.FUSION_REBUILD.name,
                        reason = "search-summary-changed",
                        attemptCount = 0,
                        nextAttemptAtEpochMillis = 0,
                        lastErrorCode = null,
                        requiredPolicyVersion = FUSION_POLICY_VERSION,
                    ),
                )
            }
            CatalogSearchSummaryCommitResult(ownership)
        }
        Outcome.Success(result)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Outcome.Failure(CatalogStoreFailure("catalog.search.commit_failed", retryable = true))
    }

    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> =
        try {
            val durableStoryId = database.withTransaction {
                val existing = dao.findEntry(mutation.entry.pluginId.value, mutation.entry.sourceId)
                val durableStoryId = StoryId(existing?.storyId ?: mutation.storyId.value)
                if (existing == null && dao.findStory(durableStoryId.value) == null) {
                    dao.upsertStories(
                        listOf(StoryEntity(durableStoryId.value, mutation.entry.contentType.name)),
                    )
                    ensureCanonicalStateForNewStory(durableStoryId.value, mutation.resolvedAtEpochMillis)
                }
                val durableEntry = mutation.entry.copy(storyId = durableStoryId)
                dao.upsertEntries(
                    listOf(mergeDetails(existing, durableEntry, mutation)),
                )
                replaceIdentifiers(durableEntry)
                durableStoryId
            }
            Outcome.Success(durableStoryId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Outcome.Failure(CatalogStoreFailure("catalog.details.commit_failed", retryable = true))
        }

    private suspend fun ensureCanonicalStateForNewStory(storyId: String, createdAtEpochMillis: Long) {
        if (canonicalDao.canonicalState(storyId) != null) return
        canonicalDao.upsertCanonicalState(
            StoryCanonicalStateEntity(
                storyId = storyId,
                activeGenerationId = null,
                health = CanonicalHealth.REEVALUATING.name,
                preferenceMode = CanonicalSourcePreferenceMode.AUTO.name,
                pinnedPluginId = null,
                pinnedSourceId = null,
                preferenceRevision = 0,
                identityRevision = 0,
                createdAtEpochMillis = createdAtEpochMillis,
            ),
        )
        canonicalDao.upsertWork(
            CanonicalEngineWorkEntity(
                storyId = storyId,
                workType = CanonicalEngineWorkType.FUSION_REBUILD.name,
                reason = "story-created",
                attemptCount = 0,
                nextAttemptAtEpochMillis = 0,
                lastErrorCode = null,
                requiredPolicyVersion = null,
            ),
        )
    }

    private suspend fun identifierModels(entry: CatalogEntryEntity) =
        dao.identifiers(entry.pluginId, entry.sourceId).map(CatalogEntryIdentifierEntity::toModel).toSet()

    private suspend fun replaceIdentifiers(entry: CatalogEntry) {
        val key = SourceKey(entry.pluginId, entry.sourceId)
        dao.deleteIdentifiers(entry.pluginId.value, entry.sourceId)
        val identifiers = entry.externalIdentifiers
            .sortedWith(compareBy({ it.namespace }, { it.scope.name }, { it.value }))
            .map { it.toEntity(key) }
        if (identifiers.isNotEmpty()) dao.insertIdentifiers(identifiers)
    }

    private fun merge(existing: CatalogEntryEntity?, entry: CatalogEntry, mutation: CatalogHomeMutation) =
        mergeHome(existing, entry.toHomeEntity(mutation.pluginVersion, mutation.refreshedAtEpochMillis))

    private fun mergeDetails(
        existing: CatalogEntryEntity?,
        entry: CatalogEntry,
        mutation: CatalogDetailsMutation,
    ) = mergeDetails(existing, entry.toDetailsEntity(mutation.pluginVersion, mutation.resolvedAtEpochMillis))

    private fun mergeHome(existing: CatalogEntryEntity?, incoming: CatalogEntryEntity): CatalogEntryEntity {
        if (existing == null) return incoming
        return mergeContent(existing, incoming).copy(
            storyId = existing.storyId,
            fullPluginVersion = existing.fullPluginVersion,
            fullResolvedAtEpochMillis = existing.fullResolvedAtEpochMillis,
        )
    }

    private fun mergeDetails(existing: CatalogEntryEntity?, incoming: CatalogEntryEntity): CatalogEntryEntity {
        if (existing == null) return incoming
        return mergeContent(existing, incoming).copy(
            storyId = existing.storyId,
            fullPluginVersion = incoming.fullPluginVersion,
            fullResolvedAtEpochMillis = incoming.fullResolvedAtEpochMillis,
        )
    }

    private fun mergeContent(existing: CatalogEntryEntity, incoming: CatalogEntryEntity): CatalogEntryEntity {
        val latestUpdate = mergeLatestUpdate(
            existing.latestUpdateAtEpochMillis,
            existing.latestUpdateReleaseLabel,
            incoming.latestUpdateAtEpochMillis,
            incoming.latestUpdateReleaseLabel,
        )
        return existing.copy(
            title = incoming.title,
            aliases = incoming.aliases.ifEmpty { existing.aliases },
            authors = incoming.authors.ifEmpty { existing.authors },
            description = incoming.description ?: existing.description,
            genres = incoming.genres.ifEmpty { existing.genres },
            contentType = incoming.contentType,
            languageTags = incoming.languageTags.ifEmpty { existing.languageTags },
            coverUrl = incoming.coverUrl?.takeIf(String::isNotBlank) ?: existing.coverUrl,
            sourceUrl = incoming.sourceUrl ?: existing.sourceUrl,
            scoreValue = incoming.scoreValue ?: existing.scoreValue,
            scoreScale = incoming.scoreScale ?: existing.scoreScale,
            popularityRank = incoming.popularityRank ?: existing.popularityRank,
            publicationStatus = incoming.publicationStatus ?: existing.publicationStatus,
            latestUpdateAtEpochMillis = latestUpdate.first,
            latestUpdateReleaseLabel = latestUpdate.second,
            pluginVersion = incoming.summaryPluginVersion,
            fetchedAtEpochMillis = incoming.summaryResolvedAtEpochMillis,
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
