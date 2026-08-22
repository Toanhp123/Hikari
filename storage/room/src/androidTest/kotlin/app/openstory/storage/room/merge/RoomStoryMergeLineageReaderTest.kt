package app.openstory.storage.room.merge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.RoomReconciliationCaseRepository
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryMergeEventEntity
import app.openstory.storage.room.catalog.StoryEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryMergeLineageReaderTest {
    @Test
    fun recoversHistoricalSourceSidesFromSchemaNineAuditPayload() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", 10L)
            seedStory(database, "story:b", "source:b", 20L)
            val mergeCase = requireNotNull(
                RoomReconciliationCaseRepository(database).recordAssessment(
                    ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:b")),
                    mergeableAssessment("evidence:lineage"),
                    evaluatedAtEpochMillis = 30L,
                ),
            )
            assertIs<StoryMergeResult.Merged>(
                RoomStoryGraphMergeCoordinator(
                    database = database,
                    clock = FakeClock(100L),
                    mergeEventIdFactory = { "merge:lineage" },
                ).execute(
                    StoryMergeRequest(
                        requestId = "request:lineage",
                        leftStoryId = StoryId("story:a"),
                        rightStoryId = StoryId("story:b"),
                        origin = StoryMergeOrigin.AUTO_RECONCILIATION,
                        reconciliationCaseId = mergeCase.id,
                        evidenceFingerprint = mergeCase.evidenceFingerprint,
                        reconciliationPolicyVersion = 1,
                    ),
                ),
            )

            val lineage = RoomStoryMergeLineageReader(database)
                .lineagesFor(StoryId("story:a"))
                .single { it.mergeEventId == "merge:lineage" }

            assertEquals(StoryId("story:a"), lineage.survivorStoryId)
            assertEquals(StoryId("story:b"), lineage.retiredStoryId)
            assertEquals(mergeCase.id, lineage.reconciliationCaseId)
            assertEquals(
                setOf(SourceKey(PluginId("plugin:catalog"), "source:a")),
                lineage.survivorSourceKeysBefore,
            )
            assertEquals(
                setOf(SourceKey(PluginId("plugin:catalog"), "source:b")),
                lineage.retiredSourceKeysBefore,
            )
        }
    }

    @Test
    fun malformedSchemaNineLineagePayloadFailsClosed() = runTest {
        withDatabase { database ->
            database.canonicalCatalogDao().insertMergeEvent(
                StoryMergeEventEntity(
                    mergeEventId = "merge:malformed",
                    survivorStoryId = "story:a",
                    retiredStoryId = "story:b",
                    origin = StoryMergeOrigin.AUTO_RECONCILIATION.name,
                    reconciliationCaseId = null,
                    evidenceFingerprint = "evidence:malformed",
                    policyVersion = 1,
                    mergedAtEpochMillis = 100L,
                    reversibilityState = "REVERSIBLE",
                    reversalPayloadVersion = STORY_MERGE_REVERSAL_PAYLOAD_VERSION,
                    reversalPayload = """{"survivorBefore":{},"retiredBefore":{}}""",
                ),
            )

            assertFailsWith<IllegalArgumentException> {
                RoomStoryMergeLineageReader(database).lineagesFor(StoryId("story:a"))
            }
        }
    }

    private fun mergeableAssessment(fingerprint: String) = ReconciliationAssessment(
        policyVersion = 1,
        semanticDecision = ReconciliationSemanticDecision.SAME_WORK,
        mergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
        confidence = 1.0,
        titleSimilarity = 1.0,
        authorSimilarity = 1.0,
        winningLead = 1.0,
        matchedIdentifiers = emptySet(),
        conflictingIdentifiers = emptySet(),
        reasons = setOf(ReconciliationReasonCode.TITLE_EXACT),
        identityEvidenceFingerprint = fingerprint,
    )

    private suspend fun seedStory(
        database: OpenStoryDatabase,
        storyId: String,
        sourceId: String,
        createdAt: Long,
    ) {
        database.catalogDao().upsertStories(listOf(StoryEntity(storyId, "MANGA")))
        database.canonicalCatalogDao().upsertCanonicalState(
            StoryCanonicalStateEntity(
                storyId = storyId,
                activeGenerationId = null,
                health = "REEVALUATING",
                preferenceMode = "AUTO",
                pinnedPluginId = null,
                pinnedSourceId = null,
                preferenceRevision = 0L,
                identityRevision = 0L,
                createdAtEpochMillis = createdAt,
            ),
        )
        database.catalogDao().upsertEntries(listOf(catalogEntry(storyId, sourceId, createdAt)))
        database.canonicalCatalogDao().upsertWork(
            CanonicalEngineWorkEntity(storyId, "FUSION_REBUILD", "seed", 0, 0, null, 1),
        )
    }

    private fun catalogEntry(storyId: String, sourceId: String, time: Long) = CatalogEntryEntity(
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
        fetchedAtEpochMillis = time,
        fullPluginVersion = null,
        fullResolvedAtEpochMillis = null,
    )

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
