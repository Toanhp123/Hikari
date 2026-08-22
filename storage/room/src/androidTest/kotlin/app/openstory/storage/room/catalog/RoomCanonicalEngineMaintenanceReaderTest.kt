package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION
import app.openstory.catalog.orchestration.CanonicalMaintenancePolicyState
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCanonicalEngineMaintenanceReaderTest {
    @Test
    fun stalePolicyQueryIsBoundedAndDistinguishesFusionFromReconciliationVersions() = runTest {
        withDatabase { database ->
            seedStory(database, "story:fusion")
            seedStory(database, "story:case-left")
            seedStory(database, "story:case-right")
            val dao = database.canonicalCatalogDao()
            dao.upsertGeneration(generation("gen:old", "story:fusion", fusionVersion = FUSION_POLICY_VERSION - 1))
            dao.markGenerationValid("gen:old")
            dao.activateGeneration("story:fusion", "gen:old", "FRESH")
            seedCase(database, "case:old", "story:case-left", "story:case-right", RECONCILIATION_POLICY_VERSION - 1)

            val reader = RoomCanonicalEngineMaintenanceReader(database)
            val stale = reader.stalePolicyStoryIds(
                FUSION_POLICY_VERSION,
                PRIMARY_SELECTION_POLICY_VERSION,
                RECONCILIATION_POLICY_VERSION,
                limit = 2,
            )

            assertEquals(2, stale.size)
            val expectedStories = setOf(
                StoryId("story:fusion"),
                StoryId("story:case-left"),
                StoryId("story:case-right"),
            )
            assertTrue(stale.all { it in expectedStories })
            val fusionState = requireNotNull(reader.policyState(StoryId("story:fusion")))
            assertEquals(FUSION_POLICY_VERSION - 1, fusionState.fusionPolicyVersion)
            assertTrue(fusionState.reconciliationPolicyVersions.isEmpty())
        }
    }

    @Test
    fun canonicalStateWithoutActiveGenerationIsStaleFusionPolicyWork() = runTest {
        withDatabase { database ->
            seedStory(database, "story:bootstrap")

            val reader = RoomCanonicalEngineMaintenanceReader(database)
            val stale = reader.stalePolicyStoryIds(
                FUSION_POLICY_VERSION,
                PRIMARY_SELECTION_POLICY_VERSION,
                RECONCILIATION_POLICY_VERSION,
                limit = 8,
            )

            assertEquals(listOf(StoryId("story:bootstrap")), stale)
            assertEquals(
                CanonicalMaintenancePolicyState(
                    fusionPolicyVersion = null,
                    primarySelectionPolicyVersion = null,
                    reconciliationPolicyVersions = emptySet(),
                ),
                reader.policyState(StoryId("story:bootstrap")),
            )
        }
    }

    @Test
    fun redirectMaintenanceDetectsNonFlattenedRedirectChain() = runTest {
        withDatabase { database ->
            seedStory(database, "story:middle")
            seedStory(database, "story:canonical")
            val dao = database.canonicalCatalogDao()
            dao.upsertMergeEvent(
                StoryMergeEventEntity(
                    "merge:one", "story:middle", "story:retired", "TEST", null, "fingerprint:1",
                    1, 1, "REVERSIBLE", 1, "{}",
                ),
            )
            dao.upsertMergeEvent(
                StoryMergeEventEntity(
                    "merge:two", "story:canonical", "story:middle", "TEST", null, "fingerprint:2",
                    1, 2, "REVERSIBLE", 1, "{}",
                ),
            )
            dao.upsertRedirect(StoryRedirectEntity("story:retired", "story:middle", "merge:one", 1))
            dao.upsertRedirect(StoryRedirectEntity("story:middle", "story:canonical", "merge:two", 2))

            val issues = RoomCanonicalEngineMaintenanceReader(database).redirectInconsistencies(limit = 8)

            assertEquals(1, issues.size)
            assertEquals(StoryId("story:middle"), issues.single().storyId)
            assertTrue(issues.single().code.contains("story:retired->story:middle"))
        }
    }

    @Test
    fun pendingCaseReadReturnsOnlyCurrentPendingRevisionAndRespectsLimit() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a")
            seedStory(database, "story:b")
            seedStory(database, "story:c")
            seedCase(database, "case:1", "story:a", "story:b", RECONCILIATION_POLICY_VERSION)
            seedCase(database, "case:2", "story:a", "story:c", RECONCILIATION_POLICY_VERSION)

            val cases = RoomCanonicalEngineMaintenanceReader(database).pendingReconciliationCases(limit = 1)

            assertEquals(1, cases.size)
            assertEquals(RECONCILIATION_POLICY_VERSION, cases.single().policyVersion)
            assertTrue(cases.single().evidenceFingerprint.startsWith("fingerprint:"))
        }
    }

    private suspend fun seedStory(database: OpenStoryDatabase, storyId: String) {
        database.catalogDao().upsertStories(listOf(StoryEntity(storyId, "MANGA")))
        database.canonicalCatalogDao().upsertCanonicalState(
            StoryCanonicalStateEntity(storyId, null, "REEVALUATING", "AUTO", null, null, 0, 0, 1),
        )
    }

    private suspend fun seedCase(
        database: OpenStoryDatabase,
        caseId: String,
        left: String,
        right: String,
        policyVersion: Int,
    ) {
        val dao = database.canonicalCatalogDao()
        val revisionId = "revision:$caseId"
        dao.upsertReconciliationCase(
            ReconciliationCaseEntity(caseId, left, right, "PENDING", revisionId, null, 1, 1),
        )
        dao.insertReconciliationRevision(
            ReconciliationCaseRevisionEntity(
                revisionId = revisionId,
                caseId = caseId,
                leftStoryId = left,
                rightStoryId = right,
                decision = "REVIEW",
                identityFingerprint = "fingerprint:$caseId",
                policyVersion = policyVersion,
                score = 0.5,
                titleSimilarity = 0.5,
                authorSimilarity = null,
                reasonCodes = emptySet(),
                hardConflicts = emptySet(),
                resolutionOrigin = null,
                evaluatedAtEpochMillis = 1,
            ),
        )
    }

    private fun generation(id: String, storyId: String, fusionVersion: Int) = CanonicalGenerationEntity(
        generationId = id,
        storyId = storyId,
        fusionPolicyVersion = fusionVersion,
        primaryPolicyVersion = PRIMARY_SELECTION_POLICY_VERSION,
        fusionFingerprint = "fusion:$id",
        primaryPluginId = "catalog:test",
        primarySourceId = "source:1",
        title = "Story",
        description = null,
        coverUrl = null,
        sourceUrl = null,
        popularityRank = null,
        aliases = emptySet(),
        authors = emptySet(),
        genres = emptySet(),
        languageTags = emptySet(),
        publicationStatus = null,
        latestUpdateAtEpochMillis = null,
        latestUpdateReleaseLabel = null,
        scoreNormalizedValue = null,
        scoreContributorCount = null,
        health = "FRESH",
        createdAtEpochMillis = 1,
        valid = false,
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
