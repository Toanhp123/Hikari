package app.openstory.storage.room.merge

import androidx.room.withTransaction
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.StoryMergeEventEntity
import app.openstory.storage.room.catalog.StoryRedirectEntity

internal class RoomStoryMergeWriter(
    private val database: OpenStoryDatabase,
    private val identity: StoryIdentityRepository,
    private val readers: StoryMergeSnapshotReader,
    private val clock: Clock,
    private val mergeEventIdFactory: () -> String,
    private val beforeAudit: suspend () -> Unit = {},
) {
    private val applier = RoomStoryMergeApplier(database)

    suspend fun commit(plan: PreparedStoryGraphMerge): StoryMergeResult = database.withTransaction {
        val currentLeft = identity.resolve(plan.request.leftStoryId)
        val currentRight = identity.resolve(plan.request.rightStoryId)
        if (currentLeft == currentRight) {
            return@withTransaction StoryMergeResult.AlreadyMerged(currentLeft)
        }
        val currentIds = setOf(currentLeft, currentRight)
        if (currentIds != setOf(plan.survivorStoryId, plan.retiredStoryId)) {
            return@withTransaction StoryMergeResult.StalePlan(currentIds)
        }

        val survivorBefore = readers.read(plan.survivorStoryId)
            ?: return@withTransaction StoryMergeResult.StalePlan(currentIds)
        val retiredBefore = readers.read(plan.retiredStoryId)
            ?: return@withTransaction StoryMergeResult.StalePlan(currentIds)
        if (!matchesExpectedVersion(plan, survivorBefore, retiredBefore)) {
            return@withTransaction StoryMergeResult.StalePlan(currentIds)
        }
        if (!mergeAuthorizingCaseIsCurrent(plan)) {
            return@withTransaction StoryMergeResult.StalePlan(currentIds)
        }

        applier.applyPreparedDomainState(plan)
        applier.validatePostMoveState(plan)

        val now = clock.nowEpochMillis()
        check(now >= 0L) { "Merge timestamp must be non-negative" }
        val survivorState = requireNotNull(database.canonicalCatalogDao().canonicalState(plan.survivorStoryId.value))
        database.canonicalCatalogDao().upsertCanonicalState(
            survivorState.copy(
                health = "REEVALUATING",
                preferenceMode = plan.sourcePreference.mode.name,
                pinnedPluginId = plan.sourcePreference.pinnedSource?.pluginId?.value,
                pinnedSourceId = plan.sourcePreference.pinnedSource?.sourceId,
                preferenceRevision = plan.sourcePreference.revision,
                identityRevision = survivorState.identityRevision + 1L,
            ),
        )
        val survivorAfter = requireNotNull(readers.read(plan.survivorStoryId)) {
            "Merged survivor graph disappeared before audit: ${plan.survivorStoryId.value}"
        }
        val postMergeFingerprint = survivorAfter.authoritativeFingerprint()

        beforeAudit()
        val mergeEventId = mergeEventIdFactory().also { require(it.isNotBlank()) }
        database.canonicalCatalogDao().insertMergeEvent(
            StoryMergeEventEntity(
                mergeEventId = mergeEventId,
                survivorStoryId = plan.survivorStoryId.value,
                retiredStoryId = plan.retiredStoryId.value,
                origin = plan.request.origin.name,
                reconciliationCaseId = plan.request.reconciliationCaseId,
                evidenceFingerprint = plan.request.evidenceFingerprint,
                policyVersion = plan.request.reconciliationPolicyVersion,
                mergedAtEpochMillis = now,
                reversibilityState = "REVERSIBLE",
                reversalPayloadVersion = STORY_MERGE_REVERSAL_PAYLOAD_VERSION,
                reversalPayload = storyMergeReversalPayload(
                    plan,
                    survivorBefore,
                    retiredBefore,
                    postMergeFingerprint,
                ),
            ),
        )

        database.canonicalCatalogDao().flattenRedirectTargets(
            retiredStoryId = plan.retiredStoryId.value,
            survivorStoryId = plan.survivorStoryId.value,
        )
        database.canonicalCatalogDao().upsertRedirect(
            StoryRedirectEntity(
                retiredStoryId = plan.retiredStoryId.value,
                canonicalStoryId = plan.survivorStoryId.value,
                mergeEventId = mergeEventId,
                createdAtEpochMillis = now,
            ),
        )

        plan.request.reconciliationCaseId?.let { caseId ->
            check(
                database.canonicalCatalogDao().markReconciliationCaseStatus(
                    caseId,
                    ReconciliationCaseStatus.RESOLVED_MERGED.name,
                    now,
                ) == 1,
            ) { "Merge-authorizing reconciliation case disappeared: $caseId" }
        }
        database.canonicalCatalogDao().rekeyRetiredStoryState(
            retiredStoryId = plan.retiredStoryId.value,
            survivorStoryId = plan.survivorStoryId.value,
        )
        applier.markPostMergeWork(plan)

        check(database.catalogDao().deleteStory(plan.retiredStoryId.value) == 1) {
            "Retired Story disappeared before retirement: ${plan.retiredStoryId.value}"
        }
        check(database.catalogDao().findStory(plan.retiredStoryId.value) == null)

        StoryMergeResult.Merged(plan.survivorStoryId, mergeEventId)
    }

    private fun matchesExpectedVersion(
        plan: PreparedStoryGraphMerge,
        survivor: StoryMergeSnapshot,
        retired: StoryMergeSnapshot,
    ): Boolean = survivor.identityRevision == plan.expectedVersion.survivorIdentityRevision &&
        retired.identityRevision == plan.expectedVersion.retiredIdentityRevision &&
        survivor.authoritativeFingerprint() == plan.expectedVersion.survivorAuthoritativeFingerprint &&
        retired.authoritativeFingerprint() == plan.expectedVersion.retiredAuthoritativeFingerprint

    private suspend fun mergeAuthorizingCaseIsCurrent(plan: PreparedStoryGraphMerge): Boolean {
        val caseId = plan.request.reconciliationCaseId
        return if (caseId == null) {
            true
        } else {
            val dao = database.canonicalCatalogDao()
            val authorizingCase = dao.reconciliationCase(caseId)
            val revisionId = authorizingCase?.currentRevisionId
            val revision = if (revisionId == null) null else dao.reconciliationRevision(revisionId)
            authorizingCase != null &&
                authorizingCase.status == ReconciliationCaseStatus.PENDING.name &&
                casePairMatchesPlan(authorizingCase.leftStoryId, authorizingCase.rightStoryId, plan) &&
                revision != null &&
                revision.identityFingerprint == plan.request.evidenceFingerprint &&
                revision.policyVersion == plan.request.reconciliationPolicyVersion &&
                "eligibility:${ReconciliationMergeEligibility.MERGEABLE.name}" in revision.hardConflicts &&
                originAllowsDecision(plan.request.origin, revision.decision)
        }
    }

    private fun casePairMatchesPlan(
        leftStoryId: String,
        rightStoryId: String,
        plan: PreparedStoryGraphMerge,
    ): Boolean = setOf(leftStoryId, rightStoryId) == setOf(
        plan.survivorStoryId.value,
        plan.retiredStoryId.value,
    )

    private fun originAllowsDecision(origin: StoryMergeOrigin, decision: String): Boolean = when (origin) {
        StoryMergeOrigin.AUTO_RECONCILIATION -> decision == ReconciliationSemanticDecision.SAME_WORK.name
        StoryMergeOrigin.USER_REVIEW_APPROVAL,
        StoryMergeOrigin.MANUAL_MAINTENANCE,
        -> decision == ReconciliationSemanticDecision.SAME_WORK.name ||
            decision == ReconciliationSemanticDecision.REVIEW.name
    }
}
