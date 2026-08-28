package app.openstory.catalog.ui.discover

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.FeatureNoOpCanonicalEngineEventSink
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.ReconciliationPolicy
import app.openstory.catalog.repository.CatalogDetailsCommitResult
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeCommitResult
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.FakeClock
import app.openstory.common.Outcome
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoverRefreshPipelineTest {
    @Test
    fun noEnabledProvidersIsClassifiedFromEmptyRefreshReport() = runTest {
        val repository = RefreshRepository(emptyList())
        val pipeline = pipeline(repository, emptyList())

        val execution = pipeline.refresh()

        assertTrue(execution.noEnabledProviders)
        assertFalse(execution.allProvidersFailed)
        assertFalse(execution.anyRetryableFailure)
        assertEquals(emptyList(), execution.homes)
    }

    @Test
    fun allProviderFailuresRetainRawRetryability() = runTest {
        val repository = RefreshRepository(emptyList())
        val source = RefreshSource(
            pluginId = PluginId("catalog.failed"),
            homeResult = CatalogSourceResult.Failure(
                CatalogSourceFailure("catalog.source.offline", retryable = true),
            ),
        )
        val pipeline = pipeline(repository, listOf(source))

        val execution = pipeline.refresh()

        assertFalse(execution.noEnabledProviders)
        assertTrue(execution.allProvidersFailed)
        assertTrue(execution.anyRetryableFailure)
        assertEquals(
            mapOf(PluginId("catalog.failed") to "catalog.source.offline"),
            execution.report.failed,
        )
    }

    @Test
    fun nonRetryableProviderFailureDoesNotInventRetryabilityFromFailureCode() = runTest {
        val repository = RefreshRepository(emptyList())
        val source = RefreshSource(
            pluginId = PluginId("catalog.invalid"),
            homeResult = CatalogSourceResult.Failure(
                CatalogSourceFailure("catalog.source.offline", retryable = false),
            ),
        )
        val pipeline = pipeline(repository, listOf(source))

        val execution = pipeline.refresh()

        assertTrue(execution.allProvidersFailed)
        assertFalse(execution.anyRetryableFailure)
        assertEquals(1, repository.observeHomesCalls)
    }

    @Test
    fun retryableStoreFailureRetainsRawRetryability() = runTest {
        val pluginId = PluginId("catalog.store-failed")
        val repository = RefreshRepository(
            postRefreshHomes = emptyList(),
            commitFailure = CatalogStoreFailure("catalog.store.busy", retryable = true),
        )
        val source = RefreshSource(
            pluginId = pluginId,
            homeResult = CatalogSourceResult.Success(emptyList()),
        )
        val pipeline = pipeline(repository, listOf(source))

        val execution = pipeline.refresh()

        assertTrue(execution.allProvidersFailed)
        assertTrue(execution.anyRetryableFailure)
        assertEquals(
            mapOf(pluginId to "catalog.store.busy"),
            execution.report.failed,
        )
    }

    @Test
    fun successfulRefreshReturnsFreshRepositoryHomesAndTimestamps() = runTest {
        val pluginId = PluginId("catalog.success")
        val postRefreshHomes = listOf(
            CatalogHomeSnapshot(
                pluginId = pluginId,
                pluginVersion = "1.0.0",
                refreshedAtEpochMillis = 777L,
                sections = emptyList(),
            ),
        )
        val repository = RefreshRepository(postRefreshHomes, requireCommitBeforeObserve = true)
        val source = RefreshSource(
            pluginId = pluginId,
            homeResult = CatalogSourceResult.Success(emptyList()),
        )
        val pipeline = pipeline(repository, listOf(source))

        val execution = pipeline.refresh()

        assertEquals(postRefreshHomes, execution.homes)
        assertEquals(mapOf(pluginId to 777L), execution.report.refreshedAtEpochMillis)
        assertEquals(setOf(pluginId), execution.report.succeeded)
        assertFalse(execution.noEnabledProviders)
        assertFalse(execution.allProvidersFailed)
        assertEquals(1, repository.observeHomesCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.pipeline(
        repository: CatalogRepository,
        sources: List<CatalogSource>,
    ): DiscoverRefreshPipeline {
        val registry = object : CatalogSourceRegistry {
            override suspend fun enabled(): List<CatalogSource> = sources
            override suspend fun source(pluginId: PluginId): CatalogSource? = sources.firstOrNull { it.pluginId == pluginId }
        }
        val refreshService = CatalogRefreshService(
            sources = registry,
            repository = repository,
            reconciliationEngine = CatalogReconciliationEngine(ReconciliationPolicy()),
            storyIdFactory = CatalogStoryIdFactory(),
            orchestrator = FeatureNoOpCanonicalEngineEventSink,
            clock = FakeClock(100L),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DiscoverRefreshPipeline(
            refreshService = refreshService,
            repository = repository,
            dispatchers = FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )
    }
}

private class RefreshSource(
    override val pluginId: PluginId,
    private val homeResult: CatalogSourceResult<List<SourceSection>>,
) : CatalogSource {
    override val version: String = "1.0.0"

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = homeResult

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
        CatalogSourceResult.Failure(CatalogSourceFailure("unused", retryable = false))

    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> =
        CatalogSourceResult.Failure(CatalogSourceFailure("unused", retryable = false))

    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = CatalogSourceResult.Success(emptyList())
}

private class RefreshRepository(
    private val postRefreshHomes: List<CatalogHomeSnapshot>,
    private val requireCommitBeforeObserve: Boolean = false,
    private val commitFailure: CatalogStoreFailure? = null,
) : CatalogRepository {
    var observeHomesCalls: Int = 0
        private set
    private var committed = false

    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> {
        check(!requireCommitBeforeObserve || committed) { "post-refresh homes were observed before commit" }
        observeHomesCalls += 1
        return flowOf(postRefreshHomes)
    }

    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null
    override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = null
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
    override suspend fun sourceRecords(): List<CatalogSourceRecord> = emptyList()

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<CatalogHomeCommitResult, CatalogStoreFailure> {
        val failure = commitFailure
        if (failure != null) return Outcome.Failure(failure)
        committed = true
        return Outcome.Success(CatalogHomeCommitResult(emptyList()))
    }

    override suspend fun commitSearchSummaries(
        mutation: CatalogSearchSummaryMutation,
    ): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure> = error("unused")

    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<CatalogDetailsCommitResult, CatalogStoreFailure> = error("unused")
}
