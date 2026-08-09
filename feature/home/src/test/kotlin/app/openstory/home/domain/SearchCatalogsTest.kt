package app.openstory.home.domain

import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.repository.*
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.catalog.source.*
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCatalogsTest {
    @Test
    fun searchResultUsesCatalogServiceCanonicalStory() = runTest {
        val source = SearchSource(PluginId("catalog.a"))
        val registry = SingleSearchRegistry(source)
        val search = SearchCatalogs(CatalogSearchService(registry, EmptyRepository(), StoryMatcher()), registry, debounceMillis = 0)
        val pages = search.results(flowOf(SearchRequest("novel"))).toList()
        assertEquals("novel", pages.last().query)
        assertEquals("Novel", pages.last().results.single().title)
    }
}

private class SearchSource(override val pluginId: PluginId) : CatalogSource {
    override val version = "1.0.0"
    override suspend fun home(request: SourceHomeRequest) = CatalogSourceResult.Success(emptyList<SourceSection>())
    override suspend fun search(request: SourceSearchRequest) = CatalogSourceResult.Success(SourceSearchPage(listOf(SourceItem("source-1", "Novel", SourceContentType.WEB_NOVEL, setOf("Author"), null, null, null)), null))
    override suspend fun details(sourceId: String) = CatalogSourceResult.Failure(CatalogSourceFailure("unused", false))
    override suspend fun filters() = CatalogSourceResult.Success(emptyList<SourceFilter>())
}

private class SingleSearchRegistry(private val sourceValue: CatalogSource) : CatalogSourceRegistry {
    override suspend fun enabled() = listOf(sourceValue)
    override suspend fun source(pluginId: PluginId) = sourceValue.takeIf { it.pluginId == pluginId }
}

private class EmptyRepository : CatalogRepository {
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)
    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
}
