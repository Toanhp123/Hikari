package app.openstory.storage.room.merge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResolution
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.LibraryEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryGraphMergePlannerTest {
    @Test
    fun prepareIsReadOnlyAndCapturesRetiredSourceMembership() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 20)
            seedStory(database, "story:b", "source:b", createdAt = 10)
            val before = tableCounts(database)
            val planner = RoomStoryGraphMergePlanner(
                RoomStoryIdentityResolver(database),
                RoomStoryMergeReaders(database),
            )

            val prepared = assertIs<StoryGraphMergePreparation.Ready>(
                planner.prepare(request("story:a", "story:b")),
            ).plan

            assertEquals(StoryId("story:b"), prepared.survivorStoryId)
            assertEquals(setOf("source:a"), prepared.sourceKeysToMove.mapTo(hashSetOf()) { it.sourceId })
            assertEquals(before, tableCounts(database))
        }
    }

    @Test
    fun protectedConflictBlocksButExplicitValidResolutionPreparesWithoutWriting() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val mappingDao = database.libraryDao()
            mappingDao.insertMapping(
                ContentMappingEntity("story:a", "plugin:content", "mapped:x", "USER_APPROVED", 1, 1),
            )
            mappingDao.insertMapping(
                ContentMappingEntity("story:b", "plugin:content", "mapped:y", "USER_URL", 1, 2),
            )
            val planner = RoomStoryGraphMergePlanner(
                RoomStoryIdentityResolver(database),
                RoomStoryMergeReaders(database),
            )
            val before = tableCounts(database)

            assertIs<StoryGraphMergePreparation.ReviewRequired>(planner.prepare(request("story:a", "story:b")))
            val ready = assertIs<StoryGraphMergePreparation.Ready>(
                planner.prepare(
                    request("story:a", "story:b").copy(
                        resolutions = listOf(
                            StoryMergeResolution.ContentMappingTarget(PluginId("plugin:content"), "mapped:x"),
                        ),
                    ),
                ),
            )
            assertEquals("mapped:x", ready.plan.mappingPlan.mappings.single().sourceStoryId)
            assertEquals(before, tableCounts(database))
        }
    }

    @Test
    fun fingerprintDetectsAuthoritativeLibraryChangeWithoutIdentityRevision() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val planner = RoomStoryGraphMergePlanner(
                RoomStoryIdentityResolver(database),
                RoomStoryMergeReaders(database),
            )
            val first = assertIs<StoryGraphMergePreparation.Ready>(
                planner.prepare(request("story:a", "story:b")),
            ).plan
            database.libraryDao().insert(LibraryEntity("story:a", "READING", 10, 10))
            val second = assertIs<StoryGraphMergePreparation.Ready>(
                planner.prepare(request("story:a", "story:b")),
            ).plan

            assertEquals(
                first.expectedVersion.retiredIdentityRevision,
                second.expectedVersion.survivorIdentityRevision.takeIf { second.survivorStoryId == StoryId("story:a") }
                    ?: second.expectedVersion.retiredIdentityRevision,
            )
            val firstByStory = fingerprintFor(first, StoryId("story:a"))
            val secondByStory = fingerprintFor(second, StoryId("story:a"))
            assertNotEquals(firstByStory, secondByStory)
        }
    }

    private fun fingerprintFor(plan: PreparedStoryGraphMerge, storyId: StoryId): String =
        if (plan.survivorStoryId == storyId) {
            plan.expectedVersion.survivorAuthoritativeFingerprint
        } else {
            plan.expectedVersion.retiredAuthoritativeFingerprint
        }

    private fun request(left: String, right: String) = StoryMergeRequest(
        requestId = "request:$left:$right",
        leftStoryId = StoryId(left),
        rightStoryId = StoryId(right),
        origin = StoryMergeOrigin.AUTO_RECONCILIATION,
        reconciliationCaseId = null,
        evidenceFingerprint = "evidence",
        reconciliationPolicyVersion = 1,
    )

    private suspend fun seedStory(
        database: OpenStoryDatabase,
        storyId: String,
        sourceId: String,
        createdAt: Long,
    ) {
        database.catalogDao().upsertStories(listOf(StoryEntity(storyId, "MANGA")))
        database.canonicalCatalogDao().upsertCanonicalState(
            StoryCanonicalStateEntity(storyId, null, "REEVALUATING", "AUTO", null, null, 0, 0, createdAt),
        )
        database.catalogDao().upsertEntries(
            listOf(
                CatalogEntryEntity(
                    pluginId = "plugin:catalog",
                    sourceId = sourceId,
                    storyId = storyId,
                    title = storyId,
                    aliases = emptySet(),
                    authors = emptySet(),
                    description = null,
                    genres = emptySet(),
                    contentType = "MANGA",
                    languageTags = emptySet(),
                    coverUrl = null,
                    sourceUrl = null,
                    scoreValue = null,
                    scoreScale = null,
                    popularityRank = null,
                    publicationStatus = null,
                    latestUpdateAtEpochMillis = null,
                    latestUpdateReleaseLabel = null,
                    pluginVersion = "1",
                    fetchedAtEpochMillis = createdAt,
                    fullPluginVersion = null,
                    fullResolvedAtEpochMillis = null,
                ),
            ),
        )
    }

    private fun tableCounts(database: OpenStoryDatabase): Map<String, Long> = listOf(
        "stories",
        "catalog_entries",
        "story_canonical_state",
        "library_entries",
        "content_mappings",
        "content_mapping_rejections",
        "canonical_chapters",
        "chapter_releases",
        "chapter_aggregation_overrides",
        "chapter_sync_states",
        "reading_progress",
    ).associateWith { table ->
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
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
