package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCanonicalProjectionQueryTest {
    @Test
    fun allStoryProjectionDoesNotIssuePerStoryPointQueries() = runTest {
        val queries = mutableListOf<String>()
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).setQueryCallback(
            { sql, _ -> synchronized(queries) { queries += sql.lowercase() } },
            Executor { command -> command.run() },
        ).build()
        try {
            val catalog = RoomCatalogRepository(database)
            catalog.commitHomeRefresh(homeMutation(listOf("a-1", "a-2")))
            val canonical = RoomCanonicalCatalogRepository(database)
            listOf("a-1", "a-2").forEach { sourceId ->
                val storyId = StoryId("story:$sourceId")
                assertTrue(canonical.persistCandidate(generation(storyId, sourceId), null))
            }
            synchronized(queries) { queries.clear() }

            assertEquals(2, RoomCatalogStoryProjectionRepository(database).observe().first().size)

            val observed = synchronized(queries) { queries.toList() }
            assertTrue(observed.none { "from stories where story_id = ?" in it })
            assertTrue(observed.none { "from catalog_entries where story_id = ?" in it })
        } finally {
            database.close()
        }
    }

    private fun homeMutation(sourceIds: List<String>): CatalogHomeMutation {
        val pluginId = PluginId("a")
        val entries = sourceIds.map { sourceId ->
            CatalogEntry(
                storyId = StoryId("story:$sourceId"),
                pluginId = pluginId,
                sourceId = sourceId,
                title = sourceId,
                contentType = ContentType.MANGA,
            )
        }
        return CatalogHomeMutation(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 1L,
            stories = entries.map { Story(it.storyId, it.contentType) },
            entries = entries,
            sections = listOf(CatalogHomeSection("section", "Section", entries)),
            orderedSourceItemIds = mapOf("section" to sourceIds),
        )
    }

    private fun generation(storyId: StoryId, sourceId: String): CanonicalGeneration {
        val source = SourceKey(PluginId("a"), sourceId)
        return CanonicalGeneration(
            id = "gen:$sourceId",
            storyId = storyId,
            fusionPolicyVersion = 1,
            primarySelectionPolicyVersion = 1,
            fusionFingerprint = "fusion:$sourceId",
            effectivePrimary = source,
            metadata = CanonicalMetadata(
                title = sourceId,
                description = null,
                coverUrl = null,
                sourceUrl = null,
                popularityRank = null,
                aliases = emptyList(),
                authors = emptyList(),
                genres = emptyList(),
                languageTags = emptyList(),
                publicationStatus = null,
                latestUpdate = null,
                score = null,
            ),
            health = CanonicalHealth.FRESH,
            provenance = mapOf(
                CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                    field = CanonicalFieldKey.TITLE,
                    strategy = CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                    contributors = listOf(
                        CanonicalFieldContributor(
                            sourceKey = source,
                            fusionFingerprint = "source-fusion:$sourceId",
                            metadataLevel = CatalogMetadataLevel.Summary,
                        ),
                    ),
                    reasonCodes = listOf("primary"),
                    policyVersion = 1,
                ),
            ),
            createdAtEpochMillis = 10L,
        )
    }
}
