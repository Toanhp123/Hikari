package app.openstory.catalog.orchestration

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.diagnostics.CanonicalTraceKind
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationMaintenance
import app.openstory.catalog.reconciliation.ReconciliationMaintenanceCase
import app.openstory.catalog.reconciliation.ReconciliationRunResult
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class CanonicalEngineMaintenanceDiagnosticsTest {
    @Test
    fun safetyPassEmitsTypedInvariantContextAndPreservesAccounting() = runTest {
        val canonical = StoryId("story:canonical")
        val retired = StoryId("story:retired")
        val source = SourceKey(PluginId("catalog:test"), "source:retired")
        val reader = DiagnosticMaintenanceReader(
            listOf(
                CanonicalMaintenanceInvariantIssue(
                    storyId = canonical,
                    code = "canonical.invariant.provenance_source_outside_story",
                    relatedStoryIds = setOf(retired),
                    sourceKeys = setOf(source),
                    field = CanonicalFieldKey.TITLE,
                ),
            ),
        )
        val traces = mutableListOf<CanonicalDecisionTrace>()
        val health = RecordingHealthMarker()
        val service = service(reader, health, CanonicalDiagnostics(traces::add))

        val report = service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, report.failedInvariant)
        assertEquals(listOf(canonical), health.degraded)
        assertEquals(
            CanonicalDecisionTrace(
                kind = CanonicalTraceKind.INVARIANT_VIOLATION,
                storyIds = linkedSetOf(canonical, retired),
                sourceKeys = setOf(source),
                reasonCodes = listOf("canonical.invariant.provenance_source_outside_story"),
                field = CanonicalFieldKey.TITLE,
            ),
            traces.single(),
        )
    }

    @Test
    fun diagnosticsFailureDoesNotChangeSafetyPassResult() = runTest {
        val canonical = StoryId("story:canonical")
        val reader = DiagnosticMaintenanceReader(
            listOf(CanonicalMaintenanceInvariantIssue(canonical, "canonical.invariant.redirect_target_invalid")),
        )
        val health = RecordingHealthMarker()
        val service = service(
            reader,
            health,
            CanonicalDiagnostics { error("diagnostics unavailable") },
        )

        val report = service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, report.failedInvariant)
        assertEquals(listOf(canonical), health.degraded)
    }

    private fun service(
        reader: CanonicalEngineMaintenanceReader,
        health: CanonicalMaintenanceHealthMarker,
        diagnostics: CanonicalDiagnostics,
    ) = CanonicalEngineMaintenanceService(
        work = EmptyWorkRepository,
        reconciliation = NoOpReconciliationMaintenance,
        fusion = CanonicalGenerationRebuilder { storyId, _ -> CanonicalFusionResult.Preparing(storyId) },
        identity = PassThroughIdentity,
        reader = reader,
        derived = PostMergeDerivedWorkDispatcher { _, _ -> PostMergeDerivedWorkResult.Dispatched },
        observability = CanonicalMaintenanceObservability(health, diagnostics),
        clock = FakeClock(1_000L),
    )
}

private class DiagnosticMaintenanceReader(
    private val issues: List<CanonicalMaintenanceInvariantIssue>,
) : CanonicalEngineMaintenanceReader {
    override suspend fun stalePolicyStoryIds(
        fusionPolicyVersion: Int,
        primarySelectionPolicyVersion: Int,
        reconciliationPolicyVersion: Int,
        limit: Int,
    ): List<StoryId> = emptyList()

    override suspend fun policyState(storyId: StoryId): CanonicalMaintenancePolicyState? = null

    override suspend fun pendingReconciliationCases(limit: Int): List<ReconciliationMaintenanceCase> = emptyList()

    override suspend fun redirectInconsistencies(limit: Int): List<CanonicalMaintenanceInvariantIssue> =
        issues.take(limit)

    override suspend fun invariantIssues(limit: Int): List<CanonicalMaintenanceInvariantIssue> = issues.take(limit)
}

private object EmptyWorkRepository : CanonicalEngineWorkRepository {
    override suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
    ) = CanonicalEngineWorkItem(storyId, type, reason, requiredPolicyVersion, 0, 0L, null)

    override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> = emptyList()
    override suspend fun complete(item: CanonicalEngineWorkItem): Boolean = false
    override suspend fun retry(
        item: CanonicalEngineWorkItem,
        failureCode: String,
        nextAttemptAtEpochMillis: Long,
    ) = Unit
    override suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType) = Unit
}

private object NoOpReconciliationMaintenance : CatalogReconciliationMaintenance {
    override suspend fun reevaluateStory(storyId: StoryId): List<ReconciliationRunResult> = emptyList()
    override suspend fun isEvidenceFingerprintCurrent(case: ReconciliationMaintenanceCase): Boolean = true
}

private object PassThroughIdentity : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(storyId)
    override suspend fun resolve(storyId: StoryId): StoryId = storyId
    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
}

private class RecordingHealthMarker : CanonicalMaintenanceHealthMarker {
    val degraded = mutableListOf<StoryId>()
    override suspend fun markDegraded(storyId: StoryId) {
        degraded += storyId
    }
}
