package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.evidence.toSourceRecord
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.catalog.repository.CatalogDetailsCommitResult
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeCommitResult
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.orchestration.CanonicalEngineWorkReasons
import app.openstory.catalog.orchestration.CatalogEvidenceLevel
import app.openstory.common.Outcome
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomCatalogRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: CatalogDao,
    private val homeDao: CatalogHomeDao,
    private val canonicalDao: CanonicalCatalogDao,
    private val identity: StoryIdentityRepository,
) : CatalogRepository {
    constructor(database: OpenStoryDatabase) : this(
        database,
        database.catalogDao(),
        database.catalogHomeDao(),
        database.canonicalCatalogDao(),
        RoomStoryIdentityResolver(database),
    )

    // Invalidation is only a reload trigger; the public Home graph is assembled from one DB read state.
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = database.invalidationTracker
        .createFlow(
            "catalog_home_snapshots",
            "catalog_home_sections",
            "catalog_home_items",
            "catalog_entries",
        )
        .map { readCoherentHomes() }
        .distinctUntilChanged()

    private suspend fun readCoherentHomes(): List<CatalogHomeSnapshot> = database.withTransaction {
        val snapshots = homeDao.snapshots()
        val sections = homeDao.sections()
        val items = homeDao.items()
        val sectionsByPlugin = sections.groupBy(CatalogHomeSectionEntity::pluginId)
        val itemsByPlugin = items.groupBy(CatalogHomeItemEntity::pluginId)
        val storedEntries = dao.homeEntries()
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

    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> =
        identity.observeResolved(storyId).flatMapLatest { resolved ->
            combine(
                dao.observeStory(resolved.value),
                dao.observeEntries(resolved.value),
            ) { story, entries ->
                story?.toModel()?.let { value -> StoryCatalogSnapshot(value, entries.map(CatalogEntryEntity::toModel)) }
            }
        }

    override suspend fun matchSnapshot(): CatalogMatchSnapshot = database.withTransaction {
        val storiesById = dao.stories().associateBy(StoryEntity::storyId)
        val entries = dao.entries()
        val identifiersBySource = dao.allIdentifiers().toIdentifierModelsBySource()
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
        val resolved = identity.resolve(storyId)
        val entries = dao.entriesForStory(resolved.value)
        val identifiersBySource = dao.identifiersForStories(listOf(resolved.value)).toIdentifierModelsBySource()
        entries.map { entry ->
            entry.toMetadataSnapshot(identifiersBySource[entry.sourceKey()].orEmpty()).toSourceRecord()
        }
    }

    override suspend fun sourceRecords(): List<CatalogSourceRecord> = database.withTransaction {
        val identifiersBySource = dao.allIdentifiers().toIdentifierModelsBySource()
        dao.allEntries().map { entry ->
            entry.toMetadataSnapshot(identifiersBySource[entry.sourceKey()].orEmpty()).toSourceRecord()
        }
    }

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<CatalogHomeCommitResult, CatalogStoreFailure> = try {
        val result = database.withTransaction {
            val sourceIds = mutation.entries.map(CatalogEntry::sourceId).distinct()
            val existing = if (sourceIds.isEmpty()) {
                emptyMap()
            } else {
                sourceIds.chunked(ROOM_CATALOG_IN_QUERY_CHUNK_SIZE)
                    .flatMap { chunk -> dao.entries(mutation.pluginId.value, chunk) }
                    .associateBy { it.pluginId to it.sourceId }
            }
            val beforeIdentifiers = identifiersForStoryIdsChunked(existing.values.map(CatalogEntryEntity::storyId))
                .toIdentifierModelsBySource()
            val before = existing.mapValues { (_, entry) ->
                entry.toMetadataSnapshot(beforeIdentifiers[entry.sourceKey()].orEmpty()).toSourceRecord()
            }
            val resolvedProposals = identity.resolveAll(mutation.stories.map(Story::id))
            val existingOwnerIds = existing.values.map { entry -> StoryId(entry.storyId) }
            val resolvedExistingOwners = identity.resolveAll(existingOwnerIds)
            val durableEntries = mutation.entries.map { entry ->
                val existingEntry = existing[entry.pluginId.value to entry.sourceId]
                val resolvedStoryId = existingEntry?.storyId?.let { resolvedExistingOwners.getValue(StoryId(it)) }
                    ?: resolvedProposals.getValue(entry.storyId)
                merge(
                    existingEntry?.copy(storyId = resolvedStoryId.value),
                    entry.copy(storyId = resolvedStoryId),
                    mutation,
                )
            }
            val durableStoryIds = durableEntries.mapTo(hashSetOf(), CatalogEntryEntity::storyId)
            val existingStoryIds = if (durableStoryIds.isEmpty()) {
                emptySet()
            } else {
                durableStoryIds.chunked(ROOM_CATALOG_IN_QUERY_CHUNK_SIZE)
                    .flatMap { chunk -> dao.existingStoryIds(chunk) }
                    .toSet()
            }
            val newStoryIds = durableStoryIds.filterTo(linkedSetOf()) { it !in existingStoryIds }
            dao.upsertStories(
                mutation.stories
                    .map { story -> story.copy(id = resolvedProposals.getValue(story.id)) }
                    .distinctBy { it.id }
                    .filter { it.id.value in newStoryIds }
                    .map { it.toEntity() },
            )
            newStoryIds.forEach { storyId ->
                ensureCanonicalStateForNewStory(storyId, mutation.refreshedAtEpochMillis)
            }
            dao.upsertEntries(durableEntries)
            replaceSummaryIdentifiers(
                mutation.entries,
                durableEntries.associateBy { entry -> entry.sourceKey() },
                beforeIdentifiers,
            )
            homeDao.deleteSections(mutation.pluginId.value)
            homeDao.upsertSnapshot(
                CatalogHomeSnapshotEntity(
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
            val afterIdentifiers = identifiersForStoryIdsChunked(durableStoryIds).toIdentifierModelsBySource()
            val durableEntriesByKey = durableEntries.associateBy { entry -> entry.sourceKey() }
            val changes = mutation.entries.sortedBy(CatalogEntry::sourceId).map { entry ->
                val key = entry.pluginId.value to entry.sourceId
                val durable = requireNotNull(durableEntriesByKey[key])
                val after = durable.toMetadataSnapshot(afterIdentifiers[key].orEmpty()).toSourceRecord()
                commitChange(before[key], after)
            }
            persistCanonicalOutbox(changes, CatalogEvidenceLevel.SUMMARY, mutation.refreshedAtEpochMillis)
            CatalogHomeCommitResult(changes)
        }
        Outcome.Success(result)
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
            val durableEntries = linkedMapOf<SourceKey, CatalogEntryEntity>()
            val storiesById = mutation.stories.associateBy { it.id }
            val sourceIds = mutation.entries.map(CatalogEntry::sourceId).distinct()
            val existingByKey = if (sourceIds.isEmpty()) {
                emptyMap()
            } else {
                sourceIds.chunked(ROOM_CATALOG_IN_QUERY_CHUNK_SIZE)
                    .flatMap { chunk -> dao.entries(mutation.pluginId.value, chunk) }
                    .associateBy { it.pluginId to it.sourceId }
            }
            val ownerCandidates = mutation.entries.map { proposedEntry ->
                val existing = existingByKey[proposedEntry.pluginId.value to proposedEntry.sourceId]
                StoryId(existing?.storyId ?: proposedEntry.storyId.value)
            }
            val resolvedOwners = identity.resolveAll(ownerCandidates)
            val durableOwnerIds = ownerCandidates.map { resolvedOwners.getValue(it).value }.toSet()
            val existingStoryIds = if (durableOwnerIds.isEmpty()) {
                mutableSetOf()
            } else {
                durableOwnerIds.chunked(ROOM_CATALOG_IN_QUERY_CHUNK_SIZE)
                    .flatMap { chunk -> dao.existingStoryIds(chunk) }
                    .toMutableSet()
            }
            val beforeIdentifiers = identifiersForStoryIdsChunked(existingByKey.values.map(CatalogEntryEntity::storyId))
                .toIdentifierModelsBySource()
            mutation.entries.sortedBy(CatalogEntry::sourceId).forEach { proposedEntry ->
                val key = SourceKey(proposedEntry.pluginId, proposedEntry.sourceId)
                val existing = existingByKey[key.pluginId.value to key.sourceId]
                val ownerCandidate = StoryId(existing?.storyId ?: proposedEntry.storyId.value)
                val durableStoryId = resolvedOwners.getValue(ownerCandidate)
                if (durableStoryId.value !in existingStoryIds) {
                    val story = storiesById[proposedEntry.storyId]
                        ?: error("Missing proposed Story for ${proposedEntry.storyId.value}")
                    dao.upsertStories(listOf(story.copy(id = durableStoryId).toEntity()))
                    ensureCanonicalStateForNewStory(durableStoryId.value, mutation.resolvedAtEpochMillis)
                    existingStoryIds += durableStoryId.value
                }
                val durableEntry = proposedEntry.copy(storyId = durableStoryId)
                val incoming = durableEntry.toHomeEntity(mutation.pluginVersion, mutation.resolvedAtEpochMillis)
                val storedEntry = mergeHome(existing?.copy(storyId = durableStoryId.value), incoming)
                dao.upsertEntries(listOf(storedEntry))
                durableEntries[key] = storedEntry
                ownership[key] = durableStoryId
            }
            replaceSummaryIdentifiers(
                mutation.entries,
                durableEntries.mapKeys { (key, _) -> key.pluginId.value to key.sourceId },
                beforeIdentifiers,
            )
            val afterIdentifiers = identifiersForStoryIdsChunked(durableOwnerIds).toIdentifierModelsBySource()
            val changes = mutation.entries.sortedBy(CatalogEntry::sourceId).map { proposedEntry ->
                val key = SourceKey(proposedEntry.pluginId, proposedEntry.sourceId)
                val sourceKey = key.pluginId.value to key.sourceId
                val before = existingByKey[sourceKey]?.let { entry ->
                    entry.toMetadataSnapshot(beforeIdentifiers[sourceKey].orEmpty()).toSourceRecord()
                }
                val durable = requireNotNull(durableEntries[key])
                val after = durable.toMetadataSnapshot(afterIdentifiers[sourceKey].orEmpty()).toSourceRecord()
                commitChange(before, after)
            }
            persistCanonicalOutbox(changes, CatalogEvidenceLevel.SUMMARY, mutation.resolvedAtEpochMillis)
            CatalogSearchSummaryCommitResult(ownership, changes)
        }
        Outcome.Success(result)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Outcome.Failure(CatalogStoreFailure("catalog.search.commit_failed", retryable = true))
    }

    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<CatalogDetailsCommitResult, CatalogStoreFailure> = try {
        val result = database.withTransaction {
            val existing = dao.findEntry(mutation.entry.pluginId.value, mutation.entry.sourceId)
            val before = existing?.let { sourceRecordFor(it) }
            val durableStoryId = identity.resolve(StoryId(existing?.storyId ?: mutation.storyId.value))
            if (existing == null && dao.findStory(durableStoryId.value) == null) {
                dao.upsertStories(
                    listOf(StoryEntity(durableStoryId.value, mutation.entry.contentType.name)),
                )
                ensureCanonicalStateForNewStory(durableStoryId.value, mutation.resolvedAtEpochMillis)
            }
            val durableEntry = mutation.entry.copy(storyId = durableStoryId)
            dao.upsertEntries(
                listOf(mergeDetails(existing?.copy(storyId = durableStoryId.value), durableEntry, mutation)),
            )
            replaceIdentifiers(durableEntry, CatalogMetadataLevel.Full)
            val afterEntity = requireNotNull(dao.findEntry(mutation.entry.pluginId.value, mutation.entry.sourceId))
            val changes = listOf(commitChange(before, sourceRecordFor(afterEntity)))
            persistCanonicalOutbox(changes, CatalogEvidenceLevel.FULL, mutation.resolvedAtEpochMillis)
            CatalogDetailsCommitResult(
                storyId = durableStoryId,
                changes = changes,
            )
        }
        Outcome.Success(result)
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
    }

    private suspend fun identifierModels(entry: CatalogEntryEntity) =
        dao.identifiers(entry.pluginId, entry.sourceId).map(CatalogEntryIdentifierEntity::toModel).toSet()

    private suspend fun identifiersForStoryIdsChunked(storyIds: Collection<String>) =
        storyIds.distinct().chunked(ROOM_CATALOG_IN_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> dao.identifiersForStories(chunk) }

    private fun CatalogEntryEntity.sourceKey(): Pair<String, String> = pluginId to sourceId

    private fun List<CatalogEntryIdentifierEntity>.toIdentifierModelsBySource() =
        groupBy { it.pluginId to it.sourceId }
            .mapValues { (_, rows) -> rows.mapTo(linkedSetOf(), CatalogEntryIdentifierEntity::toModel) }

    private suspend fun sourceRecordFor(entry: CatalogEntryEntity): CatalogSourceRecord =
        entry.toMetadataSnapshot(identifierModels(entry)).toSourceRecord()

    private suspend fun persistCanonicalOutbox(
        changes: List<CatalogCommitChange>,
        level: CatalogEvidenceLevel,
        createdAtEpochMillis: Long,
    ) {
        val effective = changes.filter { change ->
            change.identityFingerprintChanged || change.fusionFingerprintChanged
        }
        if (effective.isEmpty()) return
        canonicalDao.insertOutbox(
            effective.map { change ->
                CatalogChangeOutboxEntity(
                    storyId = change.storyId.value,
                    pluginId = change.sourceKey.pluginId.value,
                    sourceId = change.sourceKey.sourceId,
                    identityFingerprintChanged = change.identityFingerprintChanged,
                    fusionFingerprintChanged = change.fusionFingerprintChanged,
                    availabilityChanged = false,
                    evidenceLevel = level.name,
                    reason = if (level == CatalogEvidenceLevel.FULL) {
                        CanonicalEngineWorkReasons.SOURCE_FULL_CHANGED
                    } else {
                        CanonicalEngineWorkReasons.SOURCE_SUMMARY_CHANGED
                    },
                    createdAtEpochMillis = createdAtEpochMillis,
                )
            },
        )
    }

    private fun commitChange(before: CatalogSourceRecord?, after: CatalogSourceRecord): CatalogCommitChange =
        CatalogCommitChange(
            storyId = after.storyId,
            sourceKey = after.key,
            identityFingerprintChanged = before?.identityFingerprint != after.identityFingerprint,
            fusionFingerprintChanged = before?.fusionFingerprint != after.fusionFingerprint,
        )

    private suspend fun replaceIdentifiers(entry: CatalogEntry, level: CatalogMetadataLevel) {
        val key = SourceKey(entry.pluginId, entry.sourceId)
        val durable = dao.findEntry(entry.pluginId.value, entry.sourceId)
        val identifiers = if (level == CatalogMetadataLevel.Summary && durable?.fullResolvedAtEpochMillis != null) {
            dao.identifiers(entry.pluginId.value, entry.sourceId).mapTo(linkedSetOf()) { it.toModel() } +
                entry.externalIdentifiers
        } else {
            entry.externalIdentifiers
        }
        dao.deleteIdentifiers(entry.pluginId.value, entry.sourceId)
        val rows = identifiers.sortedWith(compareBy({ it.namespace }, { it.scope.name }, { it.value }))
            .map { it.toEntity(key) }
        if (rows.isNotEmpty()) dao.insertIdentifiers(rows)
    }

    private suspend fun replaceSummaryIdentifiers(
        entries: List<CatalogEntry>,
        durableEntries: Map<Pair<String, String>, CatalogEntryEntity>,
        existingIdentifiers: Map<Pair<String, String>, Set<ExternalIdentifier>>,
    ) {
        entries.groupBy { entry -> entry.pluginId.value }.forEach { (pluginId, pluginEntries) ->
            pluginEntries.map(CatalogEntry::sourceId).distinct()
                .chunked(ROOM_CATALOG_IN_QUERY_CHUNK_SIZE)
                .forEach { sourceIds -> dao.deleteIdentifiers(pluginId, sourceIds) }
        }
        val rows = entries.distinctBy { entry -> entry.pluginId.value to entry.sourceId }
            .flatMap { entry ->
                val key = entry.pluginId.value to entry.sourceId
                val durable = requireNotNull(durableEntries[key])
                val identifiers = if (durable.fullResolvedAtEpochMillis != null) {
                    existingIdentifiers[key].orEmpty() + entry.externalIdentifiers
                } else {
                    entry.externalIdentifiers
                }
                identifiers.sortedWith(compareBy({ it.namespace }, { it.scope.name }, { it.value }))
                    .map { identifier -> identifier.toEntity(SourceKey(entry.pluginId, entry.sourceId)) }
            }
        if (rows.isNotEmpty()) dao.insertIdentifiers(rows)
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

}

private const val ROOM_CATALOG_IN_QUERY_CHUNK_SIZE = 900
