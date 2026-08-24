package app.openstory.catalog.orchestration

import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationRunner
import app.openstory.catalog.reconciliation.ReconciliationRunResult
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalEngineOrchestratorTest {
    private val story = StoryId("story:old")
    private val survivor = StoryId("story:survivor")
    private val source = SourceKey(PluginId("catalog:test"), "source:1")

    @Test
    fun identityOnlyChangeRunsReconciliationWithoutFusion() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onEvidenceChanged(change(identity = true, fusion = false))

        assertEquals(listOf(source), fixture.reconciliation.reconcileCalls)
        assertTrue(fixture.fusion.calls.isEmpty())
        assertTrue(fixture.work.marks.isEmpty())
    }

    @Test
    fun successfulForegroundFusionCompletesItsDurableSnapshotWithoutBackgroundReplay() = runTest {
        val scheduler = RecordingCanonicalWorkScheduler()
        val fixture = fixture(scheduler = scheduler)

        fixture.orchestrator.onEvidenceChanged(change(identity = false, fusion = true))

        assertEquals(1, fixture.work.completed.size)
        assertEquals(CanonicalEngineWorkType.FUSION_REBUILD, fixture.work.completed.single().type)
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun staleForegroundCompletionKicksBackgroundDrainForNewerDirtyWork() = runTest {
        val scheduler = RecordingCanonicalWorkScheduler()
        val work = RecordingWorkRepository(completeResult = false)
        val fixture = fixture(work = work, scheduler = scheduler)

        fixture.orchestrator.onEvidenceChanged(change(identity = false, fusion = true))

        assertEquals(1, fixture.work.completed.size)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun retryableForegroundFusionFailureKicksBackgroundDrain() = runTest {
        val scheduler = RecordingCanonicalWorkScheduler()
        val fusion = RecordingRebuilder { id, _ ->
            CanonicalFusionResult.Failed(id, "canonical.promotion.race", retryable = true)
        }
        val fixture = fixture(scheduler = scheduler, fusion = fusion)

        fixture.orchestrator.onEvidenceChanged(change(identity = false, fusion = true))

        assertTrue(fixture.work.completed.isEmpty())
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun fusionOnlyChangeMarksDirtyThenRebuildsOnce() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onEvidenceChanged(change(identity = false, fusion = true))

        assertEquals(
            listOf(story to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED),
            fixture.fusion.calls,
        )
        assertEquals(
            listOf(
                WorkMark(
                    story,
                    CanonicalEngineWorkType.FUSION_REBUILD,
                    CanonicalEngineWorkReasons.SOURCE_SUMMARY_CHANGED,
                    requiredPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
                ),
            ),
            fixture.work.marks,
        )
    }

    @Test
    fun identityAndFusionChangeResolvesOwnerAfterReconciliationThenFusesSurvivorOnce() = runTest {
        val order = mutableListOf<String>()
        val identity = RecordingIdentityRepository().apply { redirects[story] = story }
        val reconciliation = RecordingReconciliationRunner { key ->
            order += "reconcile:${key.sourceId}"
            identity.redirects[story] = survivor
            ReconciliationRunResult.NoIdentityChange
        }
        val fusion = RecordingRebuilder { id, _ ->
            order += "fusion:${id.value}"
            CanonicalFusionResult.Preparing(id)
        }
        val fixture = fixture(identity = identity, reconciliation = reconciliation, fusion = fusion)

        fixture.orchestrator.onEvidenceChanged(change(identity = true, fusion = true))

        assertEquals(listOf("reconcile:source:1", "fusion:story:survivor"), order)
        assertEquals(listOf(survivor), fixture.work.marks.map(WorkMark::storyId))
        assertEquals(1, fixture.fusion.calls.size)
    }

    @Test
    fun autoMergeForIdentityOnlyChangeTriggersPostMergeFusionForResolvedSurvivor() = runTest {
        val identity = RecordingIdentityRepository().apply { redirects[story] = story }
        val reconciliation = RecordingReconciliationRunner {
            identity.redirects[story] = survivor
            ReconciliationRunResult.AutoMergeApplied(survivor)
        }
        val fixture = fixture(identity = identity, reconciliation = reconciliation)

        fixture.orchestrator.onEvidenceChanged(change(identity = true, fusion = false))

        assertEquals(listOf(survivor to CanonicalFusionReason.POST_MERGE), fixture.fusion.calls)
        assertEquals(CanonicalEngineWorkReasons.STORY_MERGED, fixture.work.marks.single().reason)
    }

    @Test
    fun successfulPostMergeFusionStillKicksDrainForTransactionOwnedDerivedWork() = runTest {
        val scheduler = RecordingCanonicalWorkScheduler()
        val fixture = fixture(scheduler = scheduler)

        fixture.orchestrator.onStoryMerged(story)

        assertEquals(1, fixture.work.completed.size)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun unchangedEvidenceDoesNothing() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onEvidenceChanged(change(identity = false, fusion = false))

        assertTrue(fixture.reconciliation.reconcileCalls.isEmpty())
        assertTrue(fixture.fusion.calls.isEmpty())
        assertTrue(fixture.work.marks.isEmpty())
    }

    @Test
    fun availabilityOnlyChangeDoesNotRunIdentityAndUsesAvailabilityFusionReason() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onEvidenceChanged(
            change(identity = false, fusion = false, availability = true),
        )

        assertTrue(fixture.reconciliation.reconcileCalls.isEmpty())
        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_AVAILABILITY_CHANGED), fixture.fusion.calls)
        assertEquals(CanonicalEngineWorkReasons.SOURCE_AVAILABILITY_CHANGED, fixture.work.marks.single().reason)
    }

    @Test
    fun oneThousandBackgroundChangesAreCoalescedIntoOneDurableBatchWithoutForegroundEngines() = runTest {
        val scheduler = RecordingCanonicalWorkScheduler()
        val fixture = fixture(scheduler = scheduler)
        val changes = (0 until 1_000).map { index ->
            CatalogEvidenceChange(
                storyId = StoryId("story:$index"),
                sourceKey = SourceKey(PluginId("catalog:test"), "source:$index"),
                identityFingerprintChanged = true,
                fusionFingerprintChanged = true,
                availabilityChanged = false,
                level = CatalogEvidenceLevel.SUMMARY,
            )
        }

        fixture.orchestrator.onEvidenceChanges(changes, immediateStoryIds = emptySet())

        assertTrue(fixture.reconciliation.reconcileCalls.isEmpty())
        assertTrue(fixture.fusion.calls.isEmpty())
        assertEquals(1, fixture.work.batchCalls)
        assertEquals(2_000, fixture.work.marks.size)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun deferredRowsAreDurableBeforeForegroundReconciliationCanMergeStories() = runTest {
        val order = mutableListOf<String>()
        val work = RecordingWorkRepository(onBatchMark = { order += "batch" })
        val reconciliation = RecordingReconciliationRunner {
            order += "reconcile"
            ReconciliationRunResult.NoIdentityChange
        }
        val fixture = fixture(work = work, reconciliation = reconciliation)
        val backgroundStory = StoryId("story:background")
        val changes = listOf(
            change(identity = true, fusion = true),
            CatalogEvidenceChange(
                storyId = backgroundStory,
                sourceKey = SourceKey(PluginId("catalog:test"), "source:background"),
                identityFingerprintChanged = true,
                fusionFingerprintChanged = true,
                availabilityChanged = false,
                level = CatalogEvidenceLevel.SUMMARY,
            ),
        )

        fixture.orchestrator.onEvidenceChanges(changes, immediateStoryIds = setOf(story))

        assertEquals(listOf("batch", "reconcile"), order)
    }

    @Test
    fun reconciliationFailureSchedulesResolvedOwnerRetryAndStillAllowsFusion() = runTest {
        val identity = RecordingIdentityRepository().apply { redirects[story] = survivor }
        val reconciliation = RecordingReconciliationRunner { error("temporary reconciliation failure") }
        val fixture = fixture(identity = identity, reconciliation = reconciliation)

        fixture.orchestrator.onEvidenceChanged(change(identity = true, fusion = true, level = CatalogEvidenceLevel.FULL))

        assertEquals(2, fixture.work.marks.size)
        assertEquals(
            WorkMark(
                survivor,
                CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                CanonicalEngineWorkReasons.SOURCE_FULL_CHANGED,
                app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION,
            ),
            fixture.work.marks[0],
        )
        assertEquals(CanonicalEngineWorkType.FUSION_REBUILD, fixture.work.marks[1].type)
        assertEquals(listOf(survivor to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED), fixture.fusion.calls)
    }

    private fun change(
        identity: Boolean,
        fusion: Boolean,
        availability: Boolean = false,
        level: CatalogEvidenceLevel = CatalogEvidenceLevel.SUMMARY,
    ) = CatalogEvidenceChange(
        storyId = story,
        sourceKey = source,
        identityFingerprintChanged = identity,
        fusionFingerprintChanged = fusion,
        availabilityChanged = availability,
        level = level,
    )

    private fun fixture(
        identity: RecordingIdentityRepository = RecordingIdentityRepository().apply { redirects[story] = story },
        reconciliation: RecordingReconciliationRunner = RecordingReconciliationRunner(),
        fusion: RecordingRebuilder = RecordingRebuilder(),
        work: RecordingWorkRepository = RecordingWorkRepository(),
        scheduler: CanonicalEngineWorkScheduler = NoOpCanonicalEngineWorkScheduler,
    ): Fixture {
        return Fixture(
            CanonicalEngineOrchestrator(reconciliation, fusion, work, identity, scheduler),
            reconciliation,
            fusion,
            work,
        )
    }

    private data class Fixture(
        val orchestrator: CanonicalEngineOrchestrator,
        val reconciliation: RecordingReconciliationRunner,
        val fusion: RecordingRebuilder,
        val work: RecordingWorkRepository,
    )
}

internal class RecordingReconciliationRunner(
    private val result: suspend (SourceKey) -> ReconciliationRunResult = { ReconciliationRunResult.NoIdentityChange },
) : CatalogReconciliationRunner {
    val reconcileCalls = mutableListOf<SourceKey>()
    var invalidations = 0

    override suspend fun reconcile(sourceKey: SourceKey): ReconciliationRunResult {
        reconcileCalls += sourceKey
        return result(sourceKey)
    }

    override suspend fun invalidateCandidateIndex() {
        invalidations++
    }
}

internal class RecordingRebuilder(
    private val result: suspend (StoryId, CanonicalFusionReason) -> CanonicalFusionResult = { storyId, _ ->
        CanonicalFusionResult.Preparing(storyId)
    },
) : CanonicalGenerationRebuilder {
    val calls = mutableListOf<Pair<StoryId, CanonicalFusionReason>>()

    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult {
        calls += storyId to reason
        return result(storyId, reason)
    }
}

internal class RecordingWorkRepository(
    private val failMarks: Boolean = false,
    private val completeResult: Boolean = true,
    private val onBatchMark: () -> Unit = {},
) : CanonicalEngineWorkRepository {
    val marks = mutableListOf<WorkMark>()
    val completed = mutableListOf<CanonicalEngineWorkItem>()
    var batchCalls = 0

    override suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
    ): CanonicalEngineWorkItem {
        if (failMarks) error("work unavailable")
        marks += WorkMark(storyId, type, reason, requiredPolicyVersion)
        return CanonicalEngineWorkItem(
            storyId = storyId,
            type = type,
            reason = reason,
            requiredPolicyVersion = requiredPolicyVersion,
            attemptCount = 0,
            nextAttemptAtEpochMillis = 0L,
            lastFailureCode = null,
        )
    }

    override suspend fun markDirty(requests: List<CanonicalEngineWorkRequest>): List<CanonicalEngineWorkItem> {
        batchCalls += 1
        onBatchMark()
        return requests.map { request ->
            markDirty(
                storyId = request.storyId,
                type = request.type,
                reason = request.reason,
                requiredPolicyVersion = request.requiredPolicyVersion,
            )
        }
    }

    override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> = emptyList()
    override suspend fun complete(item: CanonicalEngineWorkItem): Boolean {
        completed += item
        return completeResult
    }
    override suspend fun retry(item: CanonicalEngineWorkItem, failureCode: String, nextAttemptAtEpochMillis: Long) = Unit
    override suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType) = Unit
}

internal data class WorkMark(
    val storyId: StoryId,
    val type: CanonicalEngineWorkType,
    val reason: String,
    val requiredPolicyVersion: Int?,
)

internal class RecordingIdentityRepository : StoryIdentityRepository {
    val redirects = mutableMapOf<StoryId, StoryId>()

    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(redirects[storyId] ?: storyId)

    override suspend fun resolve(storyId: StoryId): StoryId = redirects[storyId] ?: storyId

    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
}


private class RecordingCanonicalWorkScheduler : CanonicalEngineWorkScheduler {
    var calls = 0

    override fun scheduleDrain() {
        calls += 1
    }
}
