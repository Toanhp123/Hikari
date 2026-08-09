package app.openstory.catalog.repository

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogRepositoryContractTest {
    @Test
    fun homeReplacementIsAtomicPerPluginAndHistoryRemainsAvailable() = runTest {
        val repository = FakeRepository()
        val firstA = mutation("plugin:a", "a-1")
        val firstB = mutation("plugin:b", "b-1")
        repository.commitHomeRefresh(firstA)
        repository.commitHomeRefresh(firstB)
        repository.commitHomeRefresh(mutation("plugin:a", "a-2"))

        assertEquals(listOf("plugin:a", "plugin:b"), repository.observeHomes().first().map { it.pluginId.value })
        assertEquals(listOf("a-2"), repository.observeHomes().first().single { it.pluginId.value == "plugin:a" }.sections.single().items.map { it.sourceId })
        assertEquals("a-1", repository.observeStory(StoryId("story:a-1")).first()!!.entries.single().sourceId)
    }

    private fun mutation(plugin: String, sourceId: String) = CatalogHomeMutation(
        pluginId = PluginId(plugin), pluginVersion = "1.0.0", refreshedAtEpochMillis = 1,
        stories = listOf(Story(StoryId("story:$sourceId"), ContentType.MANGA)),
        entries = listOf(CatalogEntry(StoryId("story:$sourceId"), PluginId(plugin), sourceId, sourceId, contentType = ContentType.MANGA, score = Score(1.0, 1.0))),
        sections = listOf(CatalogHomeSection("section", "Section", listOf(CatalogEntry(StoryId("story:$sourceId"), PluginId(plugin), sourceId, sourceId, contentType = ContentType.MANGA)))),
        orderedSourceItemIds = mapOf("section" to listOf(sourceId)),
    )

    private class FakeRepository : CatalogRepository {
        private val homes = MutableStateFlow<List<CatalogHomeSnapshot>>(emptyList())
        private val history = linkedMapOf<StoryId, app.openstory.catalog.model.StoryCatalogSnapshot>()
        override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = homes
        override fun observeStory(storyId: StoryId): Flow<app.openstory.catalog.model.StoryCatalogSnapshot?> = MutableStateFlow(history[storyId])
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, app.openstory.catalog.CatalogStoreFailure> {
            homes.value.filterNot { it.pluginId == mutation.pluginId }.let { retained ->
                homes.value = (retained + CatalogHomeSnapshot(mutation.pluginId, mutation.pluginVersion, mutation.refreshedAtEpochMillis, mutation.sections))
                    .sortedBy { it.pluginId.value }
            }
            mutation.entries.forEach { entry -> history[entry.storyId] = app.openstory.catalog.model.StoryCatalogSnapshot(mutation.stories.first { it.id == entry.storyId }, listOf(entry)) }
            return Outcome.Success(Unit)
        }
        override suspend fun commitDetails(mutation: CatalogDetailsMutation) = Outcome.Success(mutation.storyId)
    }
}
