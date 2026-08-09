package app.openstory.catalog.search

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.*
import app.openstory.catalog.source.*
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogSearchServiceTest {
    @Test fun sameStoryFromTwoSourcesAppearsOnceWithBothCardsAndDoesNotCommitHome() = runTest {
        val repository = FakeRepository()
        val sources = Registry(listOf(Source("a", SourceSearchPage(listOf(item("a", "Same", setOf("Author"))), null)), Source("b", SourceSearchPage(listOf(item("b", "Same", setOf("Author"))), null))))
        val result = CatalogSearchService(sources, repository, StoryMatcher()).search(CatalogSearchRequest("same"))
        assertEquals(1, result.stories.size)
        assertEquals(setOf("a", "b"), result.stories.single().sources.map { it.pluginId.value }.toSet())
        assertEquals(0, repository.homeCommits)
    }
    @Test fun failingSourceDoesNotEraseSuccessfulSource() = runTest {
        val result = CatalogSearchService(Registry(listOf(Source("a", SourceSearchPage(listOf(item("a", "A", emptySet())), null)), Source("b", null))), FakeRepository(), StoryMatcher()).search(CatalogSearchRequest("a"))
        assertEquals(1, result.stories.size)
        assertEquals(listOf("b"), result.failures.map { it.pluginId.value })
    }
    private fun item(id: String, title: String, authors: Set<String>) = SourceItem(id, title, SourceContentType.MANGA, authors, null, null, null)
    private class Registry(private val values: List<CatalogSource>) : CatalogSourceRegistry { override suspend fun enabled() = values; override suspend fun source(pluginId: PluginId) = values.firstOrNull { it.pluginId == pluginId } }
    private class Source(id: String, private val page: SourceSearchPage?) : CatalogSource { override val pluginId = PluginId(id); override val version = "1"; override suspend fun home(request: SourceHomeRequest) = error("unused"); override suspend fun search(request: SourceSearchRequest) = page?.let { CatalogSourceResult.Success(it) } ?: CatalogSourceResult.Failure(CatalogSourceFailure("down", true)); override suspend fun details(sourceId: String) = error("unused"); override suspend fun filters() = error("unused") }
    private class FakeRepository : CatalogRepository { var homeCommits = 0; override fun observeHomes() = emptyFlow<List<app.openstory.catalog.model.CatalogHomeSnapshot>>(); override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>(); override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList()); override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> { homeCommits++; return Outcome.Success(Unit) }; override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId) }
}
