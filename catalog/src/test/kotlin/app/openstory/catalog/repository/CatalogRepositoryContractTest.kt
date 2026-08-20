package app.openstory.catalog.repository

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CatalogRepositoryContractTest {
    @Test
    fun homeMutationRejectsContradictoryMembership() {
        val valid = mutation("plugin:a", "a-1")

        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                orderedSourceItemIds = mapOf("section" to listOf("different")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(stories = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(sections = emptyList(), orderedSourceItemIds = emptyMap())
        }
    }

    @Test
    fun metadataSnapshotReturnsContentWithIndependentLifecycleStamps() = runTest {
        val repository = FakeRepository()
        repository.commitHomeRefresh(mutation("catalog.test", "source-1"))
        val key = CatalogMetadataKey(PluginId("catalog.test"), "source-1")

        val snapshot = repository.metadataSnapshot(key)

        assertEquals(key.pluginId, snapshot?.entry?.pluginId)
        assertEquals(key.sourceId, snapshot?.entry?.sourceId)
        assertEquals("1.0.0", snapshot?.summary?.pluginVersion)
        assertNull(snapshot?.artwork)
        assertNull(snapshot?.full)
    }

    @Test
    fun homeReplacementIsAtomicPerPluginAndHistoryRemainsAvailable() = runTest {
        val repository = FakeRepository()
        repository.commitHomeRefresh(mutation("plugin:a", "a-1"))
        repository.commitHomeRefresh(mutation("plugin:b", "b-1"))
        repository.commitHomeRefresh(mutation("plugin:a", "a-2"))

        assertEquals(
            listOf("plugin:a", "plugin:b"),
            repository.observeHomes().first().map { it.pluginId.value },
        )
        assertEquals(
            listOf("a-2"),
            repository.observeHomes().first()
                .single { it.pluginId.value == "plugin:a" }
                .sections.single()
                .items.map { it.sourceId },
        )
        assertEquals(
            "a-1",
            repository.observeStory(StoryId("story:a-1"))
                .first()!!
                .entries.single()
                .sourceId,
        )
    }

    private fun mutation(plugin: String, sourceId: String): CatalogHomeMutation {
        val storyId = StoryId("story:$sourceId")
        val entry = CatalogEntry(
            storyId,
            PluginId(plugin),
            sourceId,
            sourceId,
            contentType = ContentType.MANGA,
            score = Score(1.0, 1.0),
        )
        return CatalogHomeMutation(
            pluginId = PluginId(plugin),
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 1,
            stories = listOf(Story(storyId, ContentType.MANGA)),
            entries = listOf(entry),
            sections = listOf(CatalogHomeSection("section", "Section", listOf(entry))),
            orderedSourceItemIds = mapOf("section" to listOf(sourceId)),
        )
    }

    private class FakeRepository : CatalogRepository {
        private val homes = MutableStateFlow<List<CatalogHomeSnapshot>>(emptyList())
        private val history = linkedMapOf<StoryId, StoryCatalogSnapshot>()
        private val metadata = linkedMapOf<CatalogMetadataKey, CatalogMetadataSnapshot>()

        override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = homes
        override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> =
            MutableStateFlow(history[storyId])
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = metadata[key]

        override suspend fun commitHomeRefresh(
            mutation: CatalogHomeMutation,
        ): Outcome<Unit, CatalogStoreFailure> {
            val retained = homes.value.filterNot { it.pluginId == mutation.pluginId }
            homes.value = (
                retained + CatalogHomeSnapshot(
                    mutation.pluginId,
                    mutation.pluginVersion,
                    mutation.refreshedAtEpochMillis,
                    mutation.sections,
                )
            ).sortedBy { it.pluginId.value }
            mutation.entries.forEach { entry ->
                history[entry.storyId] = StoryCatalogSnapshot(
                    mutation.stories.first { it.id == entry.storyId },
                    listOf(entry),
                )
                metadata[CatalogMetadataKey(entry.pluginId, entry.sourceId)] = CatalogMetadataSnapshot(
                    entry = entry,
                    summary = CatalogMetadataStamp(
                        pluginVersion = mutation.pluginVersion,
                        resolvedAtEpochMillis = mutation.refreshedAtEpochMillis,
                    ),
                    artwork = null,
                    full = null,
                )
            }
            return Outcome.Success(Unit)
        }

        override suspend fun commitDetails(
            mutation: CatalogDetailsMutation,
        ): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
    }
}
