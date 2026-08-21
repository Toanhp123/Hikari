package app.openstory.catalog.search

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSearchServiceTest {

    @Test
    fun filtersReuseUnchangedPluginVersion() = runTest {
        val source = FilterSource("a", "1")
        val service = service(Registry(listOf(source)), FakeRepository())

        service.filters()
        service.filters()

        assertEquals(1, source.filterCalls)
    }

    @Test
    fun filtersReloadWhenPluginVersionChanges() = runTest {
        val registry = MutableRegistry(listOf(FilterSource("a", "1")))
        val service = service(registry, FakeRepository())
        service.filters()
        val updated = FilterSource("a", "2")
        registry.values = listOf(updated)

        service.filters()

        assertEquals(1, updated.filterCalls)
    }

    @Test
    fun disabledPluginFilterEntryIsEvicted() = runTest {
        val first = FilterSource("a", "1")
        val registry = MutableRegistry(listOf(first))
        val service = service(registry, FakeRepository())
        service.filters()
        registry.values = emptyList()
        service.filters()
        val reenabled = FilterSource("a", "1")
        registry.values = listOf(reenabled)

        service.filters()

        assertEquals(1, reenabled.filterCalls)
    }

    @Test
    fun failedFilterLoadIsRetriedOnNextRequest() = runTest {
        val source = FilterSource("a", "1", failuresRemaining = 1)
        val service = service(Registry(listOf(source)), FakeRepository())

        service.filters()
        service.filters()

        assertEquals(2, source.filterCalls)
    }

    @Test
    fun selectingFreshResultLoadsDetailsBeforeReturningCanonicalStoryId() = runTest {
        val repository = FakeRepository()
        val source = Source("a", page(item("source-a", "Fresh Story", setOf("Author"))))
        val service = service(Registry(listOf(source)), repository)
        val result = service.search(CatalogSearchRequest("fresh")).stories.single()

        val selection = service.select(result)

        assertEquals(1, source.detailsCalls)
        assertEquals(1, repository.detailCommits)
        assertEquals(result.story.id, assertIs<CatalogSearchSelectionResult.Success>(selection).storyId)
    }

    @Test
    fun selectionCurrentlyRequestsOnlyTheFirstSearchSource() = runTest {
        // Characterization only: Phase 2 replaces this with CanonicalGeneration policy.
        val repository = FakeRepository()
        val first = Source("a", page(item("a-source", "Same", setOf("Author"))))
        val second = Source("b", page(item("b-source", "Same", setOf("Author"))))
        val service = service(Registry(listOf(second, first)), repository)
        val story = service.search(CatalogSearchRequest("same")).stories.single()

        service.select(story)

        assertEquals(1, first.detailsCalls)
        assertEquals(0, second.detailsCalls)
    }

    @Test
    fun searchQueryDoesNotLoadDetails() = runTest {
        val source = Source("a", page(item("source-a", "Fresh Story", setOf("Author"))))
        val service = service(Registry(listOf(source)), FakeRepository())

        service.search(CatalogSearchRequest("fresh"))

        assertEquals(0, source.detailsCalls)
    }

    @Test
    fun selectingFreshPersistedFullReturnsCanonicalStoryWithoutDetails() = runTest {
        val key = CatalogMetadataKey(PluginId("a"), "source-a")
        val canonical = StoryId("canonical:source-a")
        val repository = FakeRepository(mapOf(key to metadataSnapshot(key, canonical, fullAt = TEST_NOW)))
        val source = Source("a", page(item("source-a", "Fresh Story", setOf("Author"))))
        val service = service(Registry(listOf(source)), repository)
        val result = service.search(CatalogSearchRequest("fresh")).stories.single()

        val selection = service.select(result)
        runCurrent()

        assertEquals(canonical, assertIs<CatalogSearchSelectionResult.Success>(selection).storyId)
        assertEquals(0, source.detailsCalls)
    }

    @Test
    fun selectingStalePersistedFullReturnsCanonicalStoryAndRevalidatesInBackground() = runTest {
        val key = CatalogMetadataKey(PluginId("a"), "source-a")
        val canonical = StoryId("canonical:source-a")
        val repository = FakeRepository(mapOf(key to metadataSnapshot(key, canonical, fullAt = 0L)))
        val source = Source("a", page(item("source-a", "Fresh Story", setOf("Author"))))
        val service = service(Registry(listOf(source)), repository)
        val result = service.search(CatalogSearchRequest("fresh")).stories.single()

        val selection = service.select(result)
        assertEquals(canonical, assertIs<CatalogSearchSelectionResult.Success>(selection).storyId)
        assertEquals(0, source.detailsCalls)

        runCurrent()
        assertEquals(1, source.detailsCalls)
    }

    @Test
    fun selectionMapsCoordinatorFailureToExistingSearchFailure() = runTest {
        val source = Source("a", page(item("source-a", "Fresh Story", setOf("Author")))).apply {
            detailsResult = CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true))
        }
        val service = service(Registry(listOf(source)), FakeRepository())
        val result = service.search(CatalogSearchRequest("fresh")).stories.single()

        val selection = assertIs<CatalogSearchSelectionResult.Failure>(service.select(result))

        assertEquals("catalog.offline", selection.code)
        assertEquals(true, selection.retryable)
    }

    @Test
    fun sameStoryFromTwoSourcesAppearsOnceWithBothCardsAndDoesNotCommitHome() = runTest {
        val repository = FakeRepository()
        val sources = Registry(
            listOf(
                Source("a", page(item("a", "Same", setOf("Author")))),
                Source("b", page(item("b", "Same", setOf("Author")))),
            ),
        )

        val result = service(sources, repository).search(CatalogSearchRequest("same"))

        assertEquals(1, result.stories.size)
        assertEquals(
            setOf("a", "b"),
            result.stories.single().sources.map { it.pluginId.value }.toSet(),
        )
        assertEquals(0, repository.homeCommits)
    }

    @Test
    fun failingSourceDoesNotEraseSuccessfulSource() = runTest {
        val sources = Registry(
            listOf(
                Source("a", page(item("a", "A", emptySet()))),
                Source("b", null),
            ),
        )

        val result = service(sources, FakeRepository()).search(CatalogSearchRequest("a"))

        assertEquals(1, result.stories.size)
        assertEquals(listOf("b"), result.failures.map { it.pluginId.value })
    }

    @Test
    fun sourcePageIsDiscardedAtomicallyWhenOneItemIsInvalid() = runTest {
        val valid = item("valid", "Valid", emptySet())
        val invalid = valid.copy(
            sourceId = "invalid",
            scoreValue = Double.POSITIVE_INFINITY,
            scoreScale = Double.POSITIVE_INFINITY,
        )
        val sources = Registry(listOf(Source("a", SourceSearchPage(listOf(valid, invalid), null))))

        val result = service(sources, FakeRepository()).search(CatalogSearchRequest("query"))

        assertEquals(emptyList(), result.stories)
        assertEquals(listOf("catalog.source.invalid"), result.failures.map { it.code })
    }

    @Test
    fun allSourcesStartBeforeSlowSourceCompletes() = runTest {
        val release = CompletableDeferred<Unit>()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val sources = Registry(
            listOf(
                GatedSource("a", firstStarted, release),
                GatedSource("b", secondStarted, release),
            ),
        )

        val result = async {
            service(sources, FakeRepository()).search(CatalogSearchRequest("query"))
        }
        firstStarted.await()
        secondStarted.await()
        release.complete(Unit)

        assertEquals(2, result.await().stories.size)
    }

    @Test
    fun eachSourceReceivesOnlyItsOwnFilters() = runTest {
        val sourceA = RecordingSource("a")
        val sourceB = RecordingSource("b")
        val filtersA = mapOf("genre" to listOf("fantasy"))
        val filtersB = mapOf("status" to listOf("completed"))

        service(Registry(listOf(sourceA, sourceB)), FakeRepository()).search(
            CatalogSearchRequest(
                query = "story",
                filterValues = mapOf(
                    sourceA.pluginId to filtersA,
                    sourceB.pluginId to filtersB,
                ),
            ),
        )

        assertEquals(filtersA, sourceA.request?.filterValues)
        assertEquals(filtersB, sourceB.request?.filterValues)
    }

    private fun TestScope.service(sources: CatalogSourceRegistry, repository: CatalogRepository): CatalogSearchService {
        val clock = Clock { TEST_NOW }
        val matcher = StoryMatcher()
        val metadata = CatalogMetadataCoordinator(
            repository = repository,
            sources = sources,
            loader = CatalogDetailsLoader(sources, repository, matcher, clock),
            policy = CatalogMetadataPolicy(clock),
            clock = clock,
            processScope = backgroundScope,
        )
        return CatalogSearchService(
            sources,
            repository,
            matcher,
            metadata,
            CatalogFilterCache(),
        )
    }

    private fun page(item: SourceItem) = SourceSearchPage(listOf(item), null)

    private fun item(id: String, title: String, authors: Set<String>) = SourceItem(
        id,
        title,
        SourceContentType.MANGA,
        authors,
        null,
        null,
        null,
    )

    private fun metadataSnapshot(
        key: CatalogMetadataKey,
        storyId: StoryId,
        fullAt: Long,
    ) = CatalogMetadataSnapshot(
        entry = CatalogEntry(
            storyId = storyId,
            pluginId = key.pluginId,
            sourceId = key.sourceId,
            title = "Cached",
            contentType = ContentType.MANGA,
        ),
        summary = CatalogMetadataStamp("1", TEST_NOW),
        full = CatalogMetadataStamp("1", fullAt),
    )

    private companion object {
        const val TEST_NOW = CatalogMetadataPolicy.FULL_TTL_MILLIS + 1L
    }

    private class Registry(
        private val values: List<CatalogSource>,
    ) : CatalogSourceRegistry {
        override suspend fun enabled() = values
        override suspend fun source(pluginId: PluginId) =
            values.firstOrNull { it.pluginId == pluginId }
    }

    private open class Source(
        id: String,
        private val page: SourceSearchPage?,
    ) : CatalogSource {
        override val pluginId = PluginId(id)
        override val version = "1"
        var detailsCalls = 0
        var detailsResult: CatalogSourceResult<SourceDetails>? = null
        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
            error("unused")
        override suspend fun search(request: SourceSearchRequest) = page
            ?.let { CatalogSourceResult.Success(it) }
            ?: CatalogSourceResult.Failure(CatalogSourceFailure("down", true))
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
            detailsCalls++
            return detailsResult ?: CatalogSourceResult.Success(
                SourceDetails(
                    sourceId = sourceId,
                    sourceUrl = "https://example.test/$sourceId",
                    title = page?.items?.firstOrNull { it.sourceId == sourceId }?.title ?: sourceId,
                    aliases = emptySet(),
                    authors = page?.items?.firstOrNull { it.sourceId == sourceId }?.authors.orEmpty(),
                    description = "Loaded details",
                    genres = emptySet(),
                    contentType = SourceContentType.MANGA,
                    languageTags = emptySet(),
                    coverUrl = null,
                    scoreValue = null,
                    scoreScale = null,
                    popularityRank = null,
                ),
            )
        }
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
    }

    private class GatedSource(
        id: String,
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
    ) : Source(id, null) {
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> {
            started.complete(Unit)
            release.await()
            val item = SourceItem(
                pluginId.value,
                pluginId.value,
                SourceContentType.MANGA,
                emptySet(),
                null,
                null,
                null,
            )
            return CatalogSourceResult.Success(SourceSearchPage(listOf(item), null))
        }
    }

    private class RecordingSource(id: String) : Source(id, SourceSearchPage(emptyList(), null)) {
        var request: SourceSearchRequest? = null

        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> {
            this.request = request
            return CatalogSourceResult.Success(SourceSearchPage(emptyList(), null))
        }
    }


    private class MutableRegistry(
        var values: List<CatalogSource>,
    ) : CatalogSourceRegistry {
        override suspend fun enabled() = values
        override suspend fun source(pluginId: PluginId) = values.firstOrNull { it.pluginId == pluginId }
    }

    private class FilterSource(
        id: String,
        override val version: String,
        private var failuresRemaining: Int = 0,
    ) : Source(id, SourceSearchPage(emptyList(), null)) {
        var filterCalls = 0

        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> {
            filterCalls++
            return if (failuresRemaining-- > 0) {
                CatalogSourceResult.Failure(CatalogSourceFailure("filters.down", true))
            } else {
                CatalogSourceResult.Success(emptyList())
            }
        }
    }

    private class FakeRepository(
        initialMetadata: Map<CatalogMetadataKey, CatalogMetadataSnapshot> = emptyMap(),
    ) : CatalogRepository {
        private val metadata = initialMetadata.toMutableMap()
        var homeCommits = 0
        var detailCommits = 0
        override fun observeHomes() = emptyFlow<List<CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = metadata[key]
        override suspend fun commitHomeRefresh(
            mutation: CatalogHomeMutation,
        ): Outcome<Unit, CatalogStoreFailure> {
            homeCommits++
            return Outcome.Success(Unit)
        }
        override suspend fun commitDetails(
            mutation: CatalogDetailsMutation,
        ): Outcome<StoryId, CatalogStoreFailure> {
            detailCommits++
            val key = CatalogMetadataKey(mutation.entry.pluginId, mutation.entry.sourceId)
            val stamp = CatalogMetadataStamp(mutation.pluginVersion, mutation.resolvedAtEpochMillis)
            metadata[key] = CatalogMetadataSnapshot(
                entry = mutation.entry,
                summary = stamp,
                full = stamp,
            )
            return Outcome.Success(mutation.storyId)
        }
    }
}
