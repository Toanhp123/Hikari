package app.openstory.catalog.details

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.*
import app.openstory.catalog.source.*
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogDetailsServiceTest {
    @Test fun sourceIdMismatchFailsWithoutPersistence() = runTest {
        val repository = FakeRepository()
        val source = Source("a", SourceDetails("different", null, "Title", emptySet(), emptySet(), null, emptySet(), SourceContentType.MANGA, emptySet(), null, null, null, null))
        val result = CatalogDetailsService(Registry(source), repository, StoryMatcher(), Clock { 1 }).load(PluginId("a"), "requested")
        assertEquals(CatalogDetailsFailure.SourceIdMismatch("requested", "different"), (result as CatalogDetailsResult.Failure).failure)
        assertEquals(0, repository.detailCommits)
    }
    @Test fun enrichmentCommitsDetailsOnly() = runTest {
        val repository = FakeRepository()
        val source = Source("a", SourceDetails("source", "url", "Title", emptySet(), setOf("Author"), "Description", emptySet(), SourceContentType.MANGA, setOf("en"), null, null, null, 4))
        val result = CatalogDetailsService(Registry(source), repository, StoryMatcher(), Clock { 1 }).load(PluginId("a"), "source")
        assertEquals(1, repository.detailCommits)
        assertEquals("source", (result as CatalogDetailsResult.Success).entry.sourceId)
    }
    private class Registry(private val source: CatalogSource) : CatalogSourceRegistry { override suspend fun enabled() = listOf(source); override suspend fun source(pluginId: PluginId) = source.takeIf { it.pluginId == pluginId } }
    private class Source(id: String, private val detailsValue: SourceDetails) : CatalogSource { override val pluginId = PluginId(id); override val version = "1"; override suspend fun home(request: SourceHomeRequest) = error("unused"); override suspend fun search(request: SourceSearchRequest) = error("unused"); override suspend fun details(sourceId: String) = CatalogSourceResult.Success(detailsValue); override suspend fun filters() = error("unused") }
    private class FakeRepository : CatalogRepository { var detailCommits = 0; override fun observeHomes() = emptyFlow<List<app.openstory.catalog.model.CatalogHomeSnapshot>>(); override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>(); override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList()); override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation) = Outcome.Success(Unit); override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> { detailCommits++; return Outcome.Success(mutation.storyId) } }
}
