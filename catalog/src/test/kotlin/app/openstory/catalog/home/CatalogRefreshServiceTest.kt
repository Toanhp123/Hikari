package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.*
import app.openstory.catalog.source.*
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogRefreshServiceTest {
    @Test fun sourceFailureDoesNotBlockSuccessfulCommit() = runTest {
        val repository = RecordingRepository()
        val registry = Registry(listOf(Source("a", CatalogSourceResult.Failure(CatalogSourceFailure("down", true))), Source("b", CatalogSourceResult.Success(listOf(section("b-1"))))))
        val results = CatalogRefreshService(registry, repository, StoryMatcher(), Clock { 42 }).refresh()
        assertEquals(listOf("b"), repository.mutations.map { it.pluginId.value })
        assertEquals(2, results.size)
    }
    @Test fun oneMutationCapturesOneTimestamp() = runTest {
        val repository = RecordingRepository()
        val registry = Registry(listOf(Source("a", CatalogSourceResult.Success(listOf(section("a-1"))))))
        CatalogRefreshService(registry, repository, StoryMatcher(), Clock { 99 }).refresh()
        assertEquals(99, repository.mutations.single().refreshedAtEpochMillis)
    }
    @Test fun incomingOrderDoesNotChangeResolvedStories() = runTest {
        suspend fun resolve(items: List<SourceItem>): List<StoryId> {
            val repository = RecordingRepository()
            val registry = Registry(listOf(Source("a", CatalogSourceResult.Success(listOf(SourceSection("s", "S", items))))))
            CatalogRefreshService(registry, repository, StoryMatcher(), Clock { 1 }).refresh()
            return repository.mutations.single().entries.sortedBy { it.sourceId }.map { it.storyId }
        }
        val items = listOf(item("one", "One"), item("two", "Two"))
        assertEquals(resolve(items), resolve(items.reversed()))
    }
    private fun section(id: String) = SourceSection("section", "Section", listOf(item(id, id)))
    private fun item(id: String, title: String) = SourceItem(id, title, SourceContentType.MANGA, emptySet(), null, null, null)
    private class Registry(private val values: List<CatalogSource>) : CatalogSourceRegistry {
        override suspend fun enabled() = values
        override suspend fun source(pluginId: PluginId) = values.firstOrNull { it.pluginId == pluginId }
    }
    private class Source(id: String, private val result: CatalogSourceResult<List<SourceSection>>) : CatalogSource {
        override val pluginId = PluginId(id); override val version = "1"
        override suspend fun home(request: SourceHomeRequest) = result
        override suspend fun search(request: SourceSearchRequest) = error("unused")
        override suspend fun details(sourceId: String) = error("unused")
        override suspend fun filters() = error("unused")
    }
    private class RecordingRepository : CatalogRepository {
        val mutations = mutableListOf<CatalogHomeMutation>()
        override fun observeHomes() = emptyFlow<List<app.openstory.catalog.model.CatalogHomeSnapshot>>()
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> { mutations += mutation; return Outcome.Success(Unit) }
        override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
    }
}
