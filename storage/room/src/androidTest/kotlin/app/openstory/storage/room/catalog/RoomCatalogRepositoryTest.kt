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
    fun storyProjectionObservationScopesRowsToRequestedStories() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2))
            val projections = RoomCatalogStoryProjectionRepository(database)

            val observed = projections.observeForStories(setOf(StoryId("story:a-1"))).first()

            assertEquals(listOf("story:a-1"), observed.map { it.storyId.value })
            assertEquals(emptyList(), projections.observeForStories(emptySet()).first())
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
            assertEquals(
                before.single().sections.flatMap { it.items }.map { it.pluginId to it.sourceId },
                repository.observeHomes().first().single().sections.flatMap { it.items }
                    .map { it.pluginId to it.sourceId },
            )
            assertEquals("rich details", repository.observeStory(entry.storyId).first()!!.entries.single().description)
        }
    }

    @Test
    fun detailEnrichmentDoesNotOverwriteCanonicalStoryContentType() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val stored = repository.observeStory(StoryId("story:a-1")).first()!!

            repository.commitDetails(
                CatalogDetailsMutation(
                    stored.story.id,
                    stored.entries.single().copy(contentType = ContentType.WEB_NOVEL),
                    "2.0.0",
                    2,
                ),
            )

            val enriched = repository.observeStory(stored.story.id).first()!!
            assertEquals(ContentType.MANGA, enriched.story.contentType)
            assertEquals(ContentType.WEB_NOVEL, enriched.entries.single().contentType)
        }
    }

    @Test
    fun failedHomeMutationLeavesPreviousSnapshotAndFreshness() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val before = repository.observeHomes().first()
            database.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER reject_snapshot_update
                   BEFORE INSERT ON catalog_home_snapshots
                   WHEN NEW.refreshed_at_epoch_millis = 2
                   BEGIN SELECT RAISE(ABORT, 'forced failure'); END""",
            )

            val failed = repository.commitHomeRefresh(
                mutation("a", listOf("a-2"), 2),
            )

            assertIs<Outcome.Failure<*>>(failed)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_snapshot_update")
            assertEquals(before, repository.observeHomes().first())
        }
    }

    @Test
    fun matchSnapshotCollapsesSourceEntriesIntoOneCanonicalCandidate() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1, StoryId("story:shared")))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2, StoryId("story:shared")))

            val candidate = repository.matchSnapshot().candidates.single()

            assertEquals(StoryId("story:shared"), candidate.story.id)
            assertEquals(setOf("a", "b"), candidate.sourceKeys.map { it.pluginId.value }.toSet())
            assertEquals(setOf("a-1", "b-1"), candidate.sourceKeys.map { it.sourceId }.toSet())
            assertEquals(2, candidate.evidence.size)
        }
    }

    private fun mutation(
        plugin: String,
        sourceIds: List<String>,
        timestamp: Long,
        canonicalStoryId: StoryId? = null,
    ): CatalogHomeMutation {
        val pluginId = PluginId(plugin)
        val entries = sourceIds.map { sourceId ->
            CatalogEntry(
                canonicalStoryId ?: StoryId("story:$sourceId"),
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
