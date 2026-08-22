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
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
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
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
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
    fun sourcePreferenceRevisionIsHostOwnedAndDoesNotOwnEngineWork() = runTest {
        withDatabase { database ->
            val storyId = StoryId("story:1")
            seedStory(database, storyId)
            val repository = RoomCanonicalCatalogRepository(database)
            val source = SourceKey(PluginId("plugin:one"), "source-1")

            repository.setSourcePreference(
                CanonicalSourcePreference(
                    storyId,
                    CanonicalSourcePreferenceMode.PINNED,
                    source,
                    revision = 999,
                ),
            )
            repository.setSourcePreference(
                CanonicalSourcePreference(
                    storyId,
                    CanonicalSourcePreferenceMode.AUTO,
                    null,
                    revision = 999,
                ),
            )

            val state = requireNotNull(database.canonicalCatalogDao().canonicalState(storyId.value))
            assertEquals(2L, state.preferenceRevision)
            assertEquals(CanonicalSourcePreferenceMode.AUTO.name, state.preferenceMode)
            assertEquals(null, state.pinnedPluginId)
            assertEquals(null, state.pinnedSourceId)
            assertNull(database.canonicalCatalogDao().work(storyId.value, "FUSION_REBUILD"))
        }
    }

    @Test
    fun promotionHookFailureRollsBackCandidateAndKeepsOldGenerationActive() = runTest {
        withDatabase { database ->
            val storyId = StoryId("story:1")
            seedStory(database, storyId)
            val stable = RoomCanonicalCatalogRepository(database)
            assertTrue(stable.persistCandidate(generation(storyId, "gen:1", 10), null))
            val failing = RoomCanonicalCatalogRepository(
                database = database,
                canonicalDao = database.canonicalCatalogDao(),
                catalogDao = database.catalogDao(),
                identity = RoomStoryIdentityResolver(database),
                beforePromotion = { error("forced promotion failure") },
            )

            assertFailsWith<IllegalStateException> {
                failing.persistCandidate(generation(storyId, "gen:2", 20), "gen:1")
            }

            assertEquals("gen:1", stable.activeGeneration(storyId)?.id)
            assertEquals(null, database.canonicalCatalogDao().generation("gen:2"))
            assertEquals(emptyList(), database.canonicalCatalogDao().provenance("gen:2"))
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        }
    }

    @Test
    fun reconciliationReviewPersistsOneRevisionForSameFingerprintAndPolicy() = runTest {
        withDatabase { database ->
            val left = StoryId("story:a")
            val right = StoryId("story:b")
            seedStory(database, left)
            seedStory(database, right)
            val repository = RoomReconciliationCaseRepository(database)
            val key = ReconciliationCaseKey.of(left, right)
            val assessment = reconciliationAssessment("pair-fp", ReconciliationSemanticDecision.REVIEW)

            val first = requireNotNull(repository.recordAssessment(key, assessment, 10))
            val second = requireNotNull(repository.recordAssessment(key, assessment, 20))

            assertEquals(first.id, second.id)
            assertEquals(1L, second.revision)
            assertEquals(ReconciliationCaseStatus.PENDING, second.status)
            assertEquals(1, repository.observePending().first().size)
        }
    }

    @Test
    fun reconciliationCaseLookupExposesDurableTimestampsAndDeferNeverShortens() = runTest {
        withDatabase { database ->
            val left = StoryId("story:a")
            val right = StoryId("story:b")
            seedStory(database, left)
            seedStory(database, right)
            val repository = RoomReconciliationCaseRepository(database)
            val pending = requireNotNull(
                repository.recordAssessment(
                    ReconciliationCaseKey.of(left, right),
                    reconciliationAssessment("pair-fp", ReconciliationSemanticDecision.REVIEW),
                    evaluatedAtEpochMillis = 10,
                ),
            )

            assertEquals(10L, pending.createdAtEpochMillis)
            assertEquals(10L, pending.lastEvaluatedAtEpochMillis)
            assertTrue(repository.defer(pending.id, pending.revision, 1_000L))
            assertTrue(repository.defer(pending.id, pending.revision, 500L))

            val deferred = requireNotNull(repository.find(pending.id))
            assertEquals(ReconciliationCaseStatus.PENDING, deferred.status)
            assertEquals(10L, deferred.createdAtEpochMillis)
            assertEquals(10L, deferred.lastEvaluatedAtEpochMillis)
            assertEquals(1_000L, deferred.contextualPromptSuppressedUntilEpochMillis)
            assertEquals(
                10L,
                requireNotNull(database.canonicalCatalogDao().reconciliationCase(pending.id)).updatedAtEpochMillis,
            )
            assertEquals(listOf(pending.id), repository.observePending().first().map { it.id })

            assertTrue(
                repository.resolveSeparate(
                    pending.id,
                    pending.revision,
                    ReconciliationResolutionOrigin.USER,
                    resolvedAtEpochMillis = 20,
                ),
            )
            val completed = requireNotNull(repository.find(pending.id))
            assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, completed.status)
            assertEquals(10L, completed.createdAtEpochMillis)
            assertEquals(20L, completed.lastEvaluatedAtEpochMillis)
            assertEquals(null, completed.contextualPromptSuppressedUntilEpochMillis)
            assertFalse(
                repository.resolveSeparate(
                    pending.id,
                    completed.revision,
                    ReconciliationResolutionOrigin.USER,
                    resolvedAtEpochMillis = 30,
                ),
            )
            assertEquals(completed.revision, requireNotNull(repository.find(pending.id)).revision)
        }
    }

    @Test
    fun userKeepSeparateWithSameFingerprintDoesNotReopenOnRefresh() = runTest {
        withDatabase { database ->
            val left = StoryId("story:a")
            val right = StoryId("story:b")
            seedStory(database, left)
            seedStory(database, right)
            val repository = RoomReconciliationCaseRepository(database)
            val key = ReconciliationCaseKey.of(left, right)
            val assessment = reconciliationAssessment("pair-fp", ReconciliationSemanticDecision.REVIEW)
            val pending = requireNotNull(repository.recordAssessment(key, assessment, 10))
            assertTrue(repository.resolveSeparate(pending.id, pending.revision, ReconciliationResolutionOrigin.USER, 20))

            val refreshed = requireNotNull(repository.recordAssessment(key, assessment, 30))

            assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, refreshed.status)
            assertEquals(ReconciliationResolutionOrigin.USER, refreshed.resolutionOrigin)
            assertEquals(2L, refreshed.revision)
            assertEquals(emptyList(), repository.observePending().first())
        }
    }

    @Test
    fun changedIdentityFingerprintCreatesNewRevisionAndCanReopenReview() = runTest {
        withDatabase { database ->
            val left = StoryId("story:a")
            val right = StoryId("story:b")
            seedStory(database, left)
            seedStory(database, right)
            val repository = RoomReconciliationCaseRepository(database)
            val key = ReconciliationCaseKey.of(left, right)
            val initial = requireNotNull(
                repository.recordAssessment(key, reconciliationAssessment("fp:1", ReconciliationSemanticDecision.REVIEW), 10),
            )
            assertTrue(repository.resolveSeparate(initial.id, initial.revision, ReconciliationResolutionOrigin.USER, 20))

            val reopened = requireNotNull(
                repository.recordAssessment(key, reconciliationAssessment("fp:2", ReconciliationSemanticDecision.REVIEW), 30),
            )

            assertEquals(ReconciliationCaseStatus.PENDING, reopened.status)
            assertEquals(null, reopened.resolutionOrigin)
            assertEquals(3L, reopened.revision)
        }
    }

    @Test
    fun engineDifferentWorkPersistsResolvedSeparateRevision() = runTest {
        withDatabase { database ->
            val left = StoryId("story:a")
            val right = StoryId("story:b")
            seedStory(database, left)
            seedStory(database, right)
            val repository = RoomReconciliationCaseRepository(database)

            val stored = requireNotNull(
                repository.recordAssessment(
                    ReconciliationCaseKey.of(left, right),
                    reconciliationAssessment("fp:different", ReconciliationSemanticDecision.DIFFERENT_WORK),
                    10,
                ),
            )

            assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, stored.status)
            assertEquals(ReconciliationResolutionOrigin.ENGINE, stored.resolutionOrigin)
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

    private fun reconciliationAssessment(
        fingerprint: String,
        decision: ReconciliationSemanticDecision,
    ) = ReconciliationAssessment(
        policyVersion = 1,
        semanticDecision = decision,
        mergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
        confidence = if (decision == ReconciliationSemanticDecision.DIFFERENT_WORK) 1.0 else 0.9,
        titleSimilarity = 1.0,
        authorSimilarity = null,
        winningLead = null,
        matchedIdentifiers = emptySet(),
        conflictingIdentifiers = emptySet(),
        reasons = setOf(ReconciliationReasonCode.TITLE_EXACT),
        identityEvidenceFingerprint = fingerprint,
    )

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
