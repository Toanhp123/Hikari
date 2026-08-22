package app.openstory.catalog.details

import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.fusion.CatalogFusionEngine
import app.openstory.catalog.fusion.CatalogSourceAvailabilityResolver
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.metadata.CatalogMetadataAccess
import app.openstory.catalog.metadata.CatalogMetadataFailure
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.metadata.CatalogMetadataResult
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogFullMetadataFallbackServiceTest {
    @Test
    fun effectivePinnedPrimaryFailsThenNextRankedSourceSucceeds() = runTest {
        val storyId = StoryId("story:1")
        val a = record(storyId, "provider.a")
        val b = record(storyId, "provider.b")
        val metadata = RecordingMetadataAccess(
            mapOf(
                b.key to CatalogMetadataResult.Failure(CatalogMetadataFailure.SourceFailure("b.down", true)),
                a.key to CatalogMetadataResult.Ready(storyId, sparseEntry(storyId, a.key)),
            ),
        )
        val service = service(
            storyId = storyId,
            records = listOf(a, b),
            metadata = metadata,
            preference = CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.PINNED, b.key, 1L),
        )

        val result = assertIs<CatalogFullFallbackResult.Ready>(service.requireFull(storyId))

        assertEquals(listOf(b.key, a.key), metadata.attempts)
        assertEquals(a.key, result.sourceKey)
    }

    @Test
    fun successfulSparseFullStopsFallbackWithoutFieldHealing() = runTest {
        val storyId = StoryId("story:1")
        val a = record(storyId, "provider.a")
        val b = record(storyId, "provider.b")
        val sparse = sparseEntry(storyId, a.key)
        val metadata = RecordingMetadataAccess(
            mapOf(
                a.key to CatalogMetadataResult.Ready(storyId, sparse),
                b.key to CatalogMetadataResult.Ready(storyId, sparseEntry(storyId, b.key).copy(description = "richer")),
            ),
        )
        val service = service(storyId, listOf(a, b), metadata)

        val result = assertIs<CatalogFullFallbackResult.Ready>(service.requireFull(storyId))

        assertEquals(listOf(a.key), metadata.attempts)
        assertEquals(null, result.entry.description)
        assertTrue(result.entry.authors.isEmpty())
    }

    @Test
    fun sourceUnavailableAndStoreFailureAreAggregatedInAttemptOrder() = runTest {
        val storyId = StoryId("story:1")
        val a = record(storyId, "provider.a")
        val b = record(storyId, "provider.b")
        val metadata = RecordingMetadataAccess(
            mapOf(
                a.key to CatalogMetadataResult.Failure(CatalogMetadataFailure.SourceUnavailable(a.key.pluginId)),
                b.key to CatalogMetadataResult.Failure(CatalogMetadataFailure.StoreFailure("store.down", true)),
            ),
        )
        val service = service(storyId, listOf(a, b), metadata)

        val failure = assertIs<CatalogFullFallbackResult.Failure>(service.requireFull(storyId))

        assertEquals(listOf(a.key, b.key), failure.attempts.map { it.sourceKey })
        assertEquals(2, failure.attempts.size)
    }

    @Test
    fun noEligibleSourceReturnsWithoutMetadataAttempt() = runTest {
        val storyId = StoryId("story:1")
        val record = record(storyId, "provider.missing")
        val metadata = RecordingMetadataAccess(emptyMap())
        val canonical = FakeCanonicalRepository(storyId, listOf(record), automaticPreference(storyId))
        val service = CatalogFullMetadataFallbackService(
            canonical = canonical,
            metadata = metadata,
            fusion = CatalogFusionEngine(),
            availability = CatalogSourceAvailabilityResolver(Registry(emptyList()), CatalogMetadataPolicy(Clock { 10L })),
            identity = FakeIdentityRepository(),
        )

        val failure = assertIs<CatalogFullFallbackResult.Failure>(service.requireFull(storyId))

        assertTrue(failure.attempts.isEmpty())
        assertTrue(metadata.attempts.isEmpty())
    }

    @Test
    fun retiredStoryIsResolvedBeforeLookupAndReadyResultIsResolvedAgain() = runTest {
        val retired = StoryId("story:retired")
        val survivor = StoryId("story:survivor")
        val key = SourceKey(PluginId("provider.a"), "source")
        val record = record(survivor, key.pluginId.value)
        val metadata = RecordingMetadataAccess(
            mapOf(key to CatalogMetadataResult.Ready(retired, sparseEntry(retired, key))),
        )
        val canonical = FakeCanonicalRepository(survivor, listOf(record), automaticPreference(survivor))
        val identity = FakeIdentityRepository(mapOf(retired to survivor))
        val service = CatalogFullMetadataFallbackService(
            canonical = canonical,
            metadata = metadata,
            fusion = CatalogFusionEngine(),
            availability = CatalogSourceAvailabilityResolver(Registry(listOf(key.pluginId)), CatalogMetadataPolicy(Clock { 10L })),
            identity = identity,
        )

        val result = assertIs<CatalogFullFallbackResult.Ready>(service.requireFull(retired))

        assertEquals(listOf(retired, retired), identity.resolutions)
        assertEquals(survivor, canonical.stateRequests.single())
        assertEquals(survivor, result.storyId)
        assertEquals(survivor, result.entry.storyId)
    }

    private fun service(
        storyId: StoryId,
        records: List<CatalogSourceRecord>,
        metadata: RecordingMetadataAccess,
        preference: CanonicalSourcePreference = automaticPreference(storyId),
    ): CatalogFullMetadataFallbackService = CatalogFullMetadataFallbackService(
        canonical = FakeCanonicalRepository(storyId, records, preference),
        metadata = metadata,
        fusion = CatalogFusionEngine(),
        availability = CatalogSourceAvailabilityResolver(
            Registry(records.map { it.key.pluginId }.distinct()),
            CatalogMetadataPolicy(Clock { 10L }),
        ),
        identity = FakeIdentityRepository(),
    )

    private fun record(storyId: StoryId, pluginId: String): CatalogSourceRecord {
        val key = SourceKey(PluginId(pluginId), "source")
        val entry = sparseEntry(storyId, key)
        return CatalogSourceRecord(
            key = key,
            storyId = storyId,
            entry = entry,
            summary = CatalogMetadataStamp("1", 1L),
            full = null,
            identityFingerprint = "identity:$pluginId",
            fusionFingerprint = "fusion:$pluginId",
        )
    }

    private fun sparseEntry(storyId: StoryId, key: SourceKey): CatalogEntry = CatalogEntry(
        storyId = storyId,
        pluginId = key.pluginId,
        sourceId = key.sourceId,
        title = "Title ${key.pluginId.value}",
        contentType = ContentType.MANGA,
    )

    private fun automaticPreference(storyId: StoryId) = CanonicalSourcePreference(
        storyId = storyId,
        mode = CanonicalSourcePreferenceMode.AUTO,
        pinnedSource = null,
        revision = 0L,
    )
}

private class RecordingMetadataAccess(
    private val results: Map<SourceKey, CatalogMetadataResult>,
) : CatalogMetadataAccess {
    val attempts = mutableListOf<SourceKey>()

    override suspend fun require(key: CatalogMetadataKey, level: CatalogMetadataLevel): CatalogMetadataResult {
        val sourceKey = SourceKey(key.pluginId, key.sourceId)
        attempts += sourceKey
        return results[sourceKey] ?: CatalogMetadataResult.Missing
    }

    override suspend fun refresh(key: CatalogMetadataKey, level: CatalogMetadataLevel): CatalogMetadataResult =
        error("Explicit refresh is not part of AUTO fallback")
}

private class FakeCanonicalRepository(
    private val storyId: StoryId,
    private val records: List<CatalogSourceRecord>,
    private val preference: CanonicalSourcePreference,
) : CanonicalCatalogRepository {
    val stateRequests = mutableListOf<StoryId>()
    private val state = CanonicalStoryState.Preparing(
        story = Story(storyId, ContentType.MANGA),
        health = CanonicalHealth.FRESH,
        preference = preference,
        sources = records.map { record ->
            CanonicalSourceSummary(
                sourceKey = record.key,
                entry = record.entry,
                summary = record.summary,
                full = record.full,
                identityFingerprint = record.identityFingerprint,
                fusionFingerprint = record.fusionFingerprint,
            )
        },
    )

    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> = flowOf(state)
    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = flowOf(emptyList())
    override suspend fun state(storyId: StoryId): CanonicalStoryState? {
        stateRequests += storyId
        return state.takeIf { storyId == this.storyId }
    }
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> =
        records.takeIf { storyId == this.storyId }.orEmpty()
    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? = null
    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference = preference
    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) = Unit
    override suspend fun persistCandidate(candidate: CanonicalGeneration, expectedActiveGenerationId: String?): Boolean = false
    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) = Unit
    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
}

private class FakeIdentityRepository(
    private val redirects: Map<StoryId, StoryId> = emptyMap(),
) : StoryIdentityRepository {
    val resolutions = mutableListOf<StoryId>()
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(redirects[storyId] ?: storyId)
    override suspend fun resolve(storyId: StoryId): StoryId {
        resolutions += storyId
        return redirects[storyId] ?: storyId
    }
    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
}

private class Registry(pluginIds: List<PluginId>) : CatalogSourceRegistry {
    private val sources = pluginIds.associateWith { pluginId -> StubSource(pluginId) }
    override suspend fun enabled(): List<CatalogSource> = sources.values.toList()
    override suspend fun source(pluginId: PluginId): CatalogSource? = sources[pluginId]
}

private class StubSource(override val pluginId: PluginId) : CatalogSource {
    override val version: String = "1"
    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("unused")
    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> = error("unused")
    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = error("unused")
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
}
