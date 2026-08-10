package app.openstory.catalog.search

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.CatalogHomeSnapshot
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogSearchServiceTest {
    @Test
    fun selectingFreshResultLoadsDetailsBeforeReturningCanonicalStoryId() = runTest {
        val repository = FakeRepository()
        val source = Source("a", page(item("source-a", "Fresh Story", setOf("Author"))))
        val service = service(Registry(listOf(source)), repository)
        val result = service.search(CatalogSearchRequest("fresh")).stories.single()

        val selection = service.select(result)

        assertEquals(1, repository.detailCommits)
        assertEquals(result.story.id, assertIs<CatalogSearchSelectionResult.Success>(selection).storyId)
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

    private fun service(sources: CatalogSourceRegistry, repository: CatalogRepository) =
        CatalogSearchService(
            sources,
            repository,
            StoryMatcher(),
            app.openstory.catalog.details.CatalogDetailsService(
                sources,
                repository,
                StoryMatcher(),
                Clock { 100L },
            ),
        )

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
        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
            error("unused")
        override suspend fun search(request: SourceSearchRequest) = page
            ?.let { CatalogSourceResult.Success(it) }
            ?: CatalogSourceResult.Failure(CatalogSourceFailure("down", true))
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> =
            CatalogSourceResult.Success(
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

    private class FakeRepository : CatalogRepository {
        var homeCommits = 0
        var detailCommits = 0
        override fun observeHomes() = emptyFlow<List<CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
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
            return Outcome.Success(mutation.storyId)
        }
    }
}
