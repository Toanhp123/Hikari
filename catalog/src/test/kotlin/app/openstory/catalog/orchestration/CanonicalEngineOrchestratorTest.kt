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
    ): Fixture {
        return Fixture(
            CanonicalEngineOrchestrator(reconciliation, fusion, work, identity),
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
) : CanonicalEngineWorkRepository {
    val marks = mutableListOf<WorkMark>()

    override suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
    ) {
        if (failMarks) error("work unavailable")
        marks += WorkMark(storyId, type, reason, requiredPolicyVersion)
    }

    override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> = emptyList()
    override suspend fun complete(item: CanonicalEngineWorkItem) = Unit
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
