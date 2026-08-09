package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.model.*
import app.openstory.catalog.repository.*
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogHomeQueryTest {
    @Test fun queryRanksOnlyCachedHomes() = runTest {
        val entry = CatalogEntry(StoryId("story"), PluginId("p"), "s", "Title", contentType = ContentType.MANGA, score = Score(8.0, 10.0))
        val repository = FakeRepository(listOf(CatalogHomeSnapshot(PluginId("p"), "1", 1, listOf(CatalogHomeSection("section", "Section", listOf(entry))))))
        assertEquals(StoryId("story"), CatalogHomeQuery(repository).rankedStories.first().single().storyId)
        assertEquals(0, repository.sourceCalls)
    }
    private class FakeRepository(homes: List<CatalogHomeSnapshot>) : CatalogRepository {
        var sourceCalls = 0
        private val state = MutableStateFlow(homes)
        override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = state
        override fun observeStory(storyId: StoryId) = MutableStateFlow<StoryCatalogSnapshot?>(null)
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)
        override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
    }
}
