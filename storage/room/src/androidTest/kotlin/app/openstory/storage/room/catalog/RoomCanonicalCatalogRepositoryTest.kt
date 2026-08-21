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
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCanonicalCatalogRepositoryTest {
    @Test
    fun invalidCandidateIsInvisibleUntilAtomicPromotion() = runTest {
        withDatabase { database ->
            val storyId = StoryId("story:1")
            seedStory(database, storyId)
            val dao = database.canonicalCatalogDao()
            dao.upsertCanonicalState(canonicalState(storyId))
            val repository = RoomCanonicalCatalogRepository(database)
            val candidate = generation(storyId, "gen:1", 10)
            dao.upsertGeneration(candidate.toEntity(valid = false))

            assertIs<CanonicalStoryState.Preparing>(repository.state(storyId))

            assertTrue(repository.persistCandidate(candidate, expectedActiveGenerationId = null))
            val ready = assertIs<CanonicalStoryState.Ready>(repository.state(storyId))
            assertEquals("gen:1", ready.generation.id)
            assertTrue(dao.generation("gen:1")!!.valid)
        }
    }

    @Test
    fun wrongExpectedGenerationDoesNotReplaceActiveGeneration() = runTest {
        withDatabase { database ->
            val storyId = StoryId("story:1")
            seedStory(database, storyId)
            database.canonicalCatalogDao().upsertCanonicalState(canonicalState(storyId))
            val repository = RoomCanonicalCatalogRepository(database)
            assertTrue(repository.persistCandidate(generation(storyId, "gen:1", 10), null))

            assertFalse(repository.persistCandidate(generation(storyId, "gen:2", 20), "wrong"))
            assertEquals("gen:1", repository.activeGeneration(storyId)?.id)
        }
    }

    @Test
    fun cleanupRetainsActiveAndImmediatelyPreviousSuccessfulGeneration() = runTest {
        withDatabase { database ->
            val storyId = StoryId("story:1")
            seedStory(database, storyId)
            database.canonicalCatalogDao().upsertCanonicalState(canonicalState(storyId))
            val repository = RoomCanonicalCatalogRepository(database)
            assertTrue(repository.persistCandidate(generation(storyId, "gen:1", 10), null))
            assertTrue(repository.persistCandidate(generation(storyId, "gen:2", 20), "gen:1"))
            assertTrue(repository.persistCandidate(generation(storyId, "gen:3", 30), "gen:2"))

            repository.cleanupObsoleteGenerations(storyId)

            assertEquals(listOf("gen:3", "gen:2"), database.canonicalCatalogDao().validGenerationIds(storyId.value))
        }
    }

    private suspend fun seedStory(database: OpenStoryDatabase, storyId: StoryId) {
        val pluginId = PluginId("plugin:one")
        val entry = CatalogEntry(storyId, pluginId, "source-1", "Title", contentType = ContentType.MANGA)
        RoomCatalogRepository(database).commitHomeRefresh(
            CatalogHomeMutation(
                pluginId = pluginId,
                pluginVersion = "1.0.0",
                refreshedAtEpochMillis = 1,
                stories = listOf(Story(storyId, ContentType.MANGA)),
                entries = listOf(entry),
                sections = listOf(CatalogHomeSection("section", "Section", listOf(entry))),
                orderedSourceItemIds = mapOf("section" to listOf("source-1")),
            ),
        )
    }

    private fun canonicalState(storyId: StoryId) = StoryCanonicalStateEntity(
        storyId = storyId.value,
        activeGenerationId = null,
        health = CanonicalHealth.REEVALUATING.name,
        preferenceMode = CanonicalSourcePreferenceMode.AUTO.name,
        pinnedPluginId = null,
        pinnedSourceId = null,
        preferenceRevision = 0,
        identityRevision = 0,
        createdAtEpochMillis = 1,
    )

    private fun generation(storyId: StoryId, id: String, createdAt: Long): CanonicalGeneration {
        val source = SourceKey(PluginId("plugin:one"), "source-1")
        return CanonicalGeneration(
            id = id,
            storyId = storyId,
            fusionPolicyVersion = 1,
            primarySelectionPolicyVersion = 1,
            fusionFingerprint = "fusion:$id",
            effectivePrimary = source,
            metadata = CanonicalMetadata(
                title = "Title",
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
                        CanonicalFieldContributor(source, "source-fusion", CatalogMetadataLevel.Summary),
                    ),
                    reasonCodes = listOf("primary"),
                    policyVersion = 1,
                ),
            ),
            createdAtEpochMillis = createdAt,
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
