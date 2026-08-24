package app.openstory.catalog.ui.discover

import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class MutableProjectionRepository(
    initial: List<CatalogStoryProjection>,
) : CatalogStoryProjectionRepository {
    private val projections = MutableStateFlow(initial)

    override fun observe(): Flow<List<CatalogStoryProjection>> = projections

    fun replace(value: List<CatalogStoryProjection>) {
        projections.value = value
    }
}

internal class DiscoverCanonicalRepository(
    states: List<CanonicalStoryState>,
) : CanonicalCatalogRepository {
    constructor(state: CanonicalStoryState) : this(listOf(state))

    private val states = states.associateBy { it.story.id }.toMutableMap()

    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> = flowOf(states[storyId])

    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> =
        flowOf(states.values.filterIsInstance<CanonicalStoryState.Ready>())

    override suspend fun state(storyId: StoryId): CanonicalStoryState? = states[storyId]

    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()

    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? =
        (states[storyId] as? CanonicalStoryState.Ready)?.generation

    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference =
        requireNotNull(states[storyId]).preference

    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) = Unit

    override suspend fun persistCandidate(
        candidate: CanonicalGeneration,
        expectedActiveGenerationId: String?,
    ): Boolean = false

    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) = Unit

    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
}

internal fun preparingDiscoverState(storyId: StoryId): CanonicalStoryState.Preparing {
    val pluginId = PluginId("catalog.a")
    val sourceKey = SourceKey(pluginId, "source-1")
    return CanonicalStoryState.Preparing(
        story = Story(storyId, ContentType.MANGA),
        health = CanonicalHealth.REEVALUATING,
        preference = CanonicalSourcePreference(
            storyId = storyId,
            mode = CanonicalSourcePreferenceMode.AUTO,
            pinnedSource = null,
            revision = 0L,
        ),
        sources = listOf(
            CanonicalSourceSummary(
                sourceKey = sourceKey,
                entry = CatalogEntry(
                    storyId = storyId,
                    pluginId = pluginId,
                    sourceId = sourceKey.sourceId,
                    title = "Fixture Novel",
                    authors = setOf("Fixture Author"),
                    contentType = ContentType.MANGA,
                ),
                summary = CatalogMetadataStamp("1.0.0", 100L),
                full = null,
                identityFingerprint = "identity:${storyId.value}",
                fusionFingerprint = "fusion:${storyId.value}",
            ),
        ),
    )
}

internal fun readyDiscoverState(storyId: StoryId): CanonicalStoryState.Ready {
    val preparing = preparingDiscoverState(storyId)
    val source = preparing.sources.single().sourceKey
    val generation = CanonicalGeneration(
        id = "generation:${storyId.value}",
        storyId = storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "generation-fusion:${storyId.value}",
        effectivePrimary = source,
        metadata = CanonicalMetadata(
            title = "Fixture Novel",
            description = null,
            coverUrl = null,
            sourceUrl = null,
            popularityRank = null,
            aliases = emptyList(),
            authors = listOf("Fixture Author"),
            genres = emptyList(),
            languageTags = emptyList(),
            publicationStatus = null,
            latestUpdate = null,
            score = null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = mapOf(
            CanonicalFieldKey.TITLE to
                CanonicalFieldProvenance(
                    field = CanonicalFieldKey.TITLE,
                    strategy = CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                    contributors = listOf(
                        CanonicalFieldContributor(
                            sourceKey = source,
                            fusionFingerprint = "fusion:${storyId.value}",
                            metadataLevel = CatalogMetadataLevel.Summary,
                        ),
                    ),
                    reasonCodes = listOf("primary"),
                    policyVersion = 1,
                ),
        ),
        createdAtEpochMillis = 100L,
    )
    return CanonicalStoryState.Ready(
        story = preparing.story,
        health = CanonicalHealth.FRESH,
        preference = preparing.preference,
        sources = preparing.sources,
        generation = generation,
    )
}
