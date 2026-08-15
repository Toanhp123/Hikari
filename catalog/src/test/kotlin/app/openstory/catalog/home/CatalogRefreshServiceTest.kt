package app.openstory.catalog.home

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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogRefreshServiceTest {
    @Test
    fun sourceFailureDoesNotBlockSuccessfulCommit() = runTest {
        val repository = RecordingRepository()
        val registry = Registry(
            listOf(
                Source(
                    "a",
                    CatalogSourceResult.Failure(CatalogSourceFailure("down", true)),
                ),
                Source(
                    "b",
                    CatalogSourceResult.Success(listOf(section("b-1"))),
                ),
            ),
        )

        val results = service(registry, repository, 42).refresh()

        assertEquals(listOf("b"), repository.mutations.map { it.pluginId.value })
        assertEquals(2, results.size)
    }

    @Test
    fun oneMutationCapturesOneTimestamp() = runTest {
        val repository = RecordingRepository()
        val registry = Registry(
            listOf(Source("a", CatalogSourceResult.Success(listOf(section("a-1"))))),
        )

        val results = service(registry, repository, 99).refresh()

        assertEquals(99, repository.mutations.single().refreshedAtEpochMillis)
        assertEquals(99, (results.single() as CatalogRefreshResult.Success).refreshedAtEpochMillis)
    }

    @Test
    fun incomingOrderDoesNotChangeResolvedStories() = runTest {
        suspend fun resolve(items: List<SourceItem>): List<StoryId> {
            val repository = RecordingRepository()
            val page = SourceSection("s", "S", items)
            val registry = Registry(
                listOf(Source("a", CatalogSourceResult.Success(listOf(page)))),
            )
            service(registry, repository, 1).refresh()
            return repository.mutations.single().entries
                .sortedBy { it.sourceId }
                .map { it.storyId }
        }
        val items = listOf(item("one", "One"), item("two", "Two"))

        assertEquals(resolve(items), resolve(items.reversed()))
    }

    private fun service(
        registry: CatalogSourceRegistry,
        repository: CatalogRepository,
        epochMillis: Long,
    ) = CatalogRefreshService(registry, repository, StoryMatcher(), Clock { epochMillis })

    private fun section(id: String) = SourceSection(
        "section",
        "Section",
        listOf(item(id, id)),
    )

    private fun item(id: String, title: String) = SourceItem(
        id,
        title,
        SourceContentType.MANGA,
        emptySet(),
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

    private class Source(
        id: String,
        private val result: CatalogSourceResult<List<SourceSection>>,
    ) : CatalogSource {
        override val pluginId = PluginId(id)
        override val version = "1"
        override suspend fun home(request: SourceHomeRequest) = result
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
            error("unused")
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> =
            error("unused")
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
    }

    private class RecordingRepository : CatalogRepository {
        val mutations = mutableListOf<CatalogHomeMutation>()

        override fun observeHomes() = emptyFlow<List<CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())

        override suspend fun commitHomeRefresh(
            mutation: CatalogHomeMutation,
        ): Outcome<Unit, CatalogStoreFailure> {
            mutations += mutation
            return Outcome.Success(Unit)
        }

        override suspend fun commitDetails(
            mutation: CatalogDetailsMutation,
        ): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
    }
}
