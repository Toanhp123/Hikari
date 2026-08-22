package app.openstory.catalog.orchestration

import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalEngineLifecycleOrchestratorTest {
    private val story = StoryId("story:old")
    private val survivor = StoryId("story:survivor")
    private val source = SourceKey(PluginId("catalog:test"), "source:1")

    @Test
    fun sourceLinkedReconcilesSourceThenFusesResolvedOwner() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onSourceLinked(story, source)

        assertEquals(listOf(source), fixture.reconciliation.reconcileCalls)
        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED), fixture.fusion.calls)
        assertEquals(CanonicalEngineWorkReasons.SOURCE_LINKED, fixture.work.marks.single().reason)
    }

    @Test
    fun sourceUnlinkedInvalidatesIndexSchedulesReevaluationAndFusesWithoutForegroundPairScan() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onSourceUnlinked(story, source)

        assertEquals(1, fixture.reconciliation.invalidations)
        assertTrue(fixture.reconciliation.reconcileCalls.isEmpty())
        assertEquals(
            listOf(
                CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                CanonicalEngineWorkType.FUSION_REBUILD,
            ),
            fixture.work.marks.map(WorkMark::type),
        )
        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED), fixture.fusion.calls)
    }

    @Test
    fun durableWorkFailureDoesNotTurnCommittedEvidenceIntoAnException() = runTest {
        val work = RecordingWorkRepository(failMarks = true)
        val fixture = fixture(work = work)

        fixture.orchestrator.onEvidenceChanged(change(identity = false, fusion = true))

        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED), fixture.fusion.calls)
    }

    @Test
    fun sourcePreferenceChangeIsFusionOnlyAndReturnsFusionFailureToCaller() = runTest {
        val expected = CanonicalFusionResult.Failed(story, "fusion.failed", retryable = true)
        val fixture = fixture(fusion = RecordingRebuilder { _, _ -> expected })

        val result = fixture.orchestrator.onSourcePreferenceChanged(story)

        assertEquals(expected, result)
        assertTrue(fixture.reconciliation.reconcileCalls.isEmpty())
        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_PREFERENCE_CHANGED), fixture.fusion.calls)
        assertEquals(CanonicalEngineWorkReasons.SOURCE_PREFERENCE_CHANGED, fixture.work.marks.single().reason)
    }

    @Test
    fun storyMergedInvalidatesCandidateIndexAndDrainsFusionButDoesNotInventDerivedWork() = runTest {
        val fixture = fixture()

        fixture.orchestrator.onStoryMerged(survivor)

        assertEquals(1, fixture.reconciliation.invalidations)
        assertEquals(listOf(survivor to CanonicalFusionReason.POST_MERGE), fixture.fusion.calls)
        assertEquals(listOf(CanonicalEngineWorkType.FUSION_REBUILD), fixture.work.marks.map(WorkMark::type))
        assertTrue(fixture.work.marks.none { it.type == CanonicalEngineWorkType.POST_MERGE_DERIVED })
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
