package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.evidence.toSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class RoomCanonicalCatalogRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val canonicalDao: CanonicalCatalogDao,
    private val catalogDao: CatalogDao,
    private val identity: StoryIdentityRepository,
    private val beforePromotion: suspend () -> Unit = {},
) : CanonicalCatalogRepository {
    constructor(database: OpenStoryDatabase) : this(
        database = database,
        canonicalDao = database.canonicalCatalogDao(),
        catalogDao = database.catalogDao(),
        identity = RoomStoryIdentityResolver(database),
        beforePromotion = {},
    )

    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> =
        identity.observeResolved(storyId).flatMapLatest { canonicalId ->
            combine(
                canonicalDao.observeCanonicalState(canonicalId.value),
                catalogDao.observeStory(canonicalId.value),
                catalogDao.observeEntries(canonicalId.value),
            ) { _, _, _ -> canonicalId }
                .mapLatest(::readResolvedState)
        }

    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = combine(
        canonicalDao.observeCanonicalStates(),
        catalogDao.observeStories(),
        catalogDao.observeAllEntries(),
    ) { states, _, _ -> states.map { StoryId(it.storyId) } }
        .mapLatest { storyIds ->
            storyIds.mapNotNull { readResolvedState(it) as? CanonicalStoryState.Ready }
        }

    override fun observeReadyStories(storyIds: Set<StoryId>): Flow<List<CanonicalStoryState.Ready>> {
        if (storyIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return identity.observeResolvedSet(storyIds).flatMapLatest { resolved ->
            val ids = resolved.map(StoryId::value)
            combine(
                canonicalDao.observeCanonicalStates(ids),
                catalogDao.observeStories(ids),
                catalogDao.observeEntries(ids),
            ) { states, _, _ -> states.map { StoryId(it.storyId) } }
                .mapLatest { readyIds ->
                    readyIds.mapNotNull { readResolvedState(it) as? CanonicalStoryState.Ready }
                }
        }
    }

    override suspend fun state(storyId: StoryId): CanonicalStoryState? =
        readResolvedState(identity.resolve(storyId))

    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = database.withTransaction {
        sourceRecordsResolved(identity.resolve(storyId))
    }

    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? {
        val resolved = identity.resolve(storyId)
        return database.withTransaction {
            val state = canonicalDao.canonicalState(resolved.value) ?: return@withTransaction null
            val id = state.activeGenerationId ?: return@withTransaction null
            val generation = canonicalDao.generation(id)?.takeIf(CanonicalGenerationEntity::valid)
                ?: return@withTransaction null
            generation.toModel(canonicalDao.provenance(id))
        }
    }

    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference {
        val resolved = identity.resolve(storyId)
        return requireNotNull(canonicalDao.canonicalState(resolved.value)) {
            "Missing canonical state for ${resolved.value}"
        }.toPreference()
    }

    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) {
        val resolved = identity.resolve(preference.storyId)
        database.withTransaction {
            val current = requireNotNull(canonicalDao.canonicalState(resolved.value))
            canonicalDao.upsertCanonicalState(
                current.copy(
                    health = CanonicalHealth.REEVALUATING.name,
                    preferenceMode = preference.mode.name,
                    pinnedPluginId = preference.pinnedSource?.pluginId?.value,
                    pinnedSourceId = preference.pinnedSource?.sourceId,
                    preferenceRevision = current.preferenceRevision + 1,
                ),
            )
        }
    }

    override suspend fun persistCandidate(
        candidate: CanonicalGeneration,
        expectedActiveGenerationId: String?,
    ): Boolean {
        val resolved = identity.resolve(candidate.storyId)
        if (resolved != candidate.storyId) return false
        return database.withTransaction {
            val state = canonicalDao.canonicalState(candidate.storyId.value) ?: return@withTransaction false
            if (state.activeGenerationId != expectedActiveGenerationId) return@withTransaction false
            val ownedSources = catalogDao.entriesForStory(candidate.storyId.value)
                .mapTo(linkedSetOf()) { SourceKey(PluginId(it.pluginId), it.sourceId) }
            require(candidate.effectivePrimary in ownedSources) { "Effective primary is not owned by Story" }
            val contributors = candidate.provenance.values.flatMap(CanonicalFieldProvenance::contributors)
            require(contributors.all { it.sourceKey in ownedSources }) {
                "Canonical provenance references a source not owned by Story"
            }

            canonicalDao.upsertGeneration(candidate.toEntity(valid = false))
            canonicalDao.deleteProvenance(candidate.id)
            canonicalDao.insertProvenance(candidate.toProvenanceEntities())
            beforePromotion()
            canonicalDao.markGenerationValid(candidate.id)
            check(canonicalDao.activateGeneration(candidate.storyId.value, candidate.id, candidate.health.name) == 1)
            true
        }
    }

    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) {
        val resolved = identity.resolve(storyId)
        check(canonicalDao.updateHealth(resolved.value, health.name) == 1)
    }

    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) {
        canonicalDao.deleteObsoleteSuccessfulGenerations(identity.resolve(storyId).value)
    }

    private suspend fun readResolvedState(storyId: StoryId): CanonicalStoryState? = database.withTransaction {
        val story = catalogDao.findStory(storyId.value)?.toModel() ?: return@withTransaction null
        val state = canonicalDao.canonicalState(storyId.value) ?: return@withTransaction null
        val sources = sourceRecordsResolved(storyId).map(CatalogSourceRecord::toSummary)
        val preference = state.toPreference()
        val health = CanonicalHealth.valueOf(state.health)
        val active = state.activeGenerationId?.let { id ->
            canonicalDao.generation(id)?.takeIf(CanonicalGenerationEntity::valid)?.toModel(canonicalDao.provenance(id))
        }
        if (active == null) {
            CanonicalStoryState.Preparing(story, health, preference, sources)
        } else {
            CanonicalStoryState.Ready(story, health, preference, sources, active)
        }
    }

    private suspend fun sourceRecordsResolved(storyId: StoryId): List<CatalogSourceRecord> =
        catalogDao.entriesForStory(storyId.value).map { entry ->
            val identifiers = catalogDao.identifiers(entry.pluginId, entry.sourceId)
                .map(CatalogEntryIdentifierEntity::toModel)
                .toSet()
            entry.toMetadataSnapshot(identifiers).toSourceRecord()
        }
}

internal fun CanonicalGeneration.toEntity(valid: Boolean) = CanonicalGenerationEntity(
    generationId = id,
    storyId = storyId.value,
    fusionPolicyVersion = fusionPolicyVersion,
    primaryPolicyVersion = primarySelectionPolicyVersion,
    fusionFingerprint = fusionFingerprint,
    primaryPluginId = effectivePrimary.pluginId.value,
    primarySourceId = effectivePrimary.sourceId,
    title = metadata.title,
    description = metadata.description,
    coverUrl = metadata.coverUrl,
    sourceUrl = metadata.sourceUrl,
    popularityRank = metadata.popularityRank,
    aliases = metadata.aliases.toSet(),
    authors = metadata.authors.toSet(),
    genres = metadata.genres.toSet(),
    languageTags = metadata.languageTags.toSet(),
    publicationStatus = metadata.publicationStatus?.name,
    latestUpdateAtEpochMillis = metadata.latestUpdate?.atEpochMillis,
    latestUpdateReleaseLabel = metadata.latestUpdate?.releaseLabel,
    scoreNormalizedValue = metadata.score?.normalizedValue,
    scoreContributorCount = metadata.score?.contributorCount,
    health = health.name,
    createdAtEpochMillis = createdAtEpochMillis,
    valid = valid,
)

private fun CanonicalGeneration.toProvenanceEntities(): List<CanonicalFieldProvenanceEntity> =
    provenance.values.sortedBy { it.field.name }.flatMap { field ->
        field.contributors
            .sortedWith(compareBy({ it.sourceKey.pluginId.value }, { it.sourceKey.sourceId }))
            .map { contributor ->
            CanonicalFieldProvenanceEntity(
                generationId = id,
                fieldKey = field.field.name,
                contributorPluginId = contributor.sourceKey.pluginId.value,
                contributorSourceId = contributor.sourceKey.sourceId,
                strategy = field.strategy.name,
                contributorFusionFingerprint = contributor.fusionFingerprint,
                metadataLevel = contributor.metadataLevel.name,
                reasonCodes = field.reasonCodes.toSet(),
                policyVersion = field.policyVersion,
            )
        }
    }

private fun CanonicalGenerationEntity.toModel(
    rows: List<CanonicalFieldProvenanceEntity>,
): CanonicalGeneration = CanonicalGeneration(
    id = generationId,
    storyId = StoryId(storyId),
    fusionPolicyVersion = fusionPolicyVersion,
    primarySelectionPolicyVersion = primaryPolicyVersion,
    fusionFingerprint = fusionFingerprint,
    effectivePrimary = SourceKey(PluginId(primaryPluginId), primarySourceId),
    metadata = CanonicalMetadata(
        title = title,
        description = description,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        popularityRank = popularityRank,
        aliases = aliases.sorted(),
        authors = authors.sorted(),
        genres = genres.sorted(),
        languageTags = languageTags.sorted(),
        publicationStatus = publicationStatus?.let(PublicationStatus::valueOf),
        latestUpdate = latestUpdateAtEpochMillis?.let { CatalogLatestUpdate(it, latestUpdateReleaseLabel) },
        score = if (scoreNormalizedValue != null && scoreContributorCount != null) {
            CanonicalScore(scoreNormalizedValue, scoreContributorCount)
        } else {
            null
        },
    ),
    health = CanonicalHealth.valueOf(health),
    provenance = rows.toModelProvenance(),
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun List<CanonicalFieldProvenanceEntity>.toModelProvenance(): Map<CanonicalFieldKey, CanonicalFieldProvenance> =
    groupBy { CanonicalFieldKey.valueOf(it.fieldKey) }
        .toSortedMap(compareBy(CanonicalFieldKey::name))
        .mapValues { (field, rows) ->
            val first = rows.first()
            require(rows.all { it.strategy == first.strategy && it.policyVersion == first.policyVersion })
            CanonicalFieldProvenance(
                field = field,
                strategy = CanonicalFieldStrategy.valueOf(first.strategy),
                contributors = rows.map { row ->
                    CanonicalFieldContributor(
                        sourceKey = SourceKey(PluginId(row.contributorPluginId), row.contributorSourceId),
                        fusionFingerprint = row.contributorFusionFingerprint,
                        metadataLevel = CatalogMetadataLevel.valueOf(row.metadataLevel),
                    )
                },
                reasonCodes = rows.flatMap { it.reasonCodes }.distinct().sorted(),
                policyVersion = first.policyVersion,
            )
        }

private fun StoryCanonicalStateEntity.toPreference() = CanonicalSourcePreference(
    storyId = StoryId(storyId),
    mode = CanonicalSourcePreferenceMode.valueOf(preferenceMode),
    pinnedSource = when (preferenceMode) {
        CanonicalSourcePreferenceMode.AUTO.name -> null
        CanonicalSourcePreferenceMode.PINNED.name -> SourceKey(
            PluginId(requireNotNull(pinnedPluginId)),
            requireNotNull(pinnedSourceId),
        )
        else -> error("Unknown canonical source preference mode: $preferenceMode")
    },
    revision = preferenceRevision,
)

private fun CatalogSourceRecord.toSummary() = CanonicalSourceSummary(
    sourceKey = key,
    entry = entry,
    summary = summary,
    full = full,
    identityFingerprint = identityFingerprint,
    fusionFingerprint = fusionFingerprint,
)
