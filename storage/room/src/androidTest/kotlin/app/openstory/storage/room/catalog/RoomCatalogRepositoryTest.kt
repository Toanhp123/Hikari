package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {
    @Test
    fun semanticHomeCommitReplacesOnlyOnePluginAndKeepsRemovedEntry() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2))
            repository.commitHomeRefresh(mutation("a", listOf("a-2"), 3))

            val homes = repository.observeHomes().first()
            assertEquals(listOf("a", "b"), homes.map { it.pluginId.value })
            assertEquals(listOf("a-2"), homes.first().sections.single().items.map { it.sourceId })
            assertEquals("a-1", repository.observeStory(StoryId("story:a-1")).first()!!.entries.single().sourceId)
        }
    }

    @Test
    fun detailEnrichmentDoesNotAlterHomeMembership() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val before = repository.observeHomes().first()
            val entry = before.single().sections.single().items.single()

            val result = repository.commitDetails(
                CatalogDetailsMutation(
                    entry.storyId,
                    entry.copy(description = "rich details"),
                    "2.0.0",
                    2,
                ),
            )

            assertIs<Outcome.Success<StoryId>>(result)
            assertEquals(before, repository.observeHomes().first())
            assertEquals("rich details", repository.observeStory(entry.storyId).first()!!.entries.single().description)
        }
    }

    @Test
    fun failedHomeMutationLeavesPreviousSnapshotAndFreshness() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val before = repository.observeHomes().first()
            val duplicateEntry = mutation("a", listOf("a-2"), 2).entries.single()

            val failed = repository.commitHomeRefresh(
                mutation("a", listOf("a-2"), 2).copy(
                    sections = listOf(
                        CatalogHomeSection(
                            "section",
                            "Section",
                            listOf(duplicateEntry, duplicateEntry),
                        ),
                    ),
                    orderedSourceItemIds = mapOf("section" to listOf("a-2", "a-2")),
                ),
            )

            assertIs<Outcome.Failure<*>>(failed)
            assertEquals(before, repository.observeHomes().first())
        }
    }

    private fun mutation(plugin: String, sourceIds: List<String>, timestamp: Long): CatalogHomeMutation {
        val pluginId = PluginId(plugin)
        val entries = sourceIds.map { sourceId ->
            CatalogEntry(
                StoryId("story:$sourceId"),
                pluginId,
                sourceId,
                sourceId,
                contentType = ContentType.MANGA,
            )
        }
        return CatalogHomeMutation(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = timestamp,
            stories = entries.map { Story(it.storyId, it.contentType) },
            entries = entries,
            sections = listOf(CatalogHomeSection("section", "Section", entries)),
            orderedSourceItemIds = mapOf("section" to sourceIds),
        )
    }

    private suspend fun withDatabase(block: suspend (OpenStoryDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
