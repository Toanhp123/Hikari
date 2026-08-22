package app.openstory.storage.room.merge

import app.openstory.catalog.canonical.CanonicalSourcePreferenceMergePolicy
import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.identity.StoryMergeReverseRequest
import app.openstory.catalog.identity.StoryMergeReversalAssessment
import app.openstory.catalog.identity.StoryMergeReversibility
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.chapters.merge.ChapterStoryMergePolicy
import app.openstory.common.merge.DomainMergeDecision
import app.openstory.library.merge.ContentMappingStoryMergePolicy
import app.openstory.library.merge.LibraryStoryMergePolicy
import app.openstory.reader.progress.ReadingProgressMergePolicy
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryMergeEventEntity

internal class RoomStoryMergeReversalPlanner(
    private val database: OpenStoryDatabase,
    private val reader: StoryMergeSnapshotReader = RoomStoryMergeReaders(database),
    private val sourcePreferencePolicy: CanonicalSourcePreferenceMergePolicy = CanonicalSourcePreferenceMergePolicy(),
    private val libraryPolicy: LibraryStoryMergePolicy = LibraryStoryMergePolicy(),
    private val mappingPolicy: ContentMappingStoryMergePolicy = ContentMappingStoryMergePolicy(),
    private val chapterPolicy: ChapterStoryMergePolicy = ChapterStoryMergePolicy(),
    private val progressPolicy: ReadingProgressMergePolicy = ReadingProgressMergePolicy(),
) {
    suspend fun prepare(request: StoryMergeReverseRequest): StoryMergeReversalPreparation =
        database.canonicalCatalogDao().mergeReversalEvent(request.mergeEventId)
            ?.let(StoryMergeReversalPreparation::AlreadyReversed)
            ?: prepareHistoricalMerge(request)

    private suspend fun prepareHistoricalMerge(request: StoryMergeReverseRequest): StoryMergeReversalPreparation =
        database.canonicalCatalogDao().mergeEvent(request.mergeEventId)
            ?.let { event -> prepareRecordedMerge(request, event) }
            ?: StoryMergeReversalPreparation.NotFound

    private suspend fun prepareRecordedMerge(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
    ): StoryMergeReversalPreparation {
        val audit = runCatching { StoryMergeReversalAuditParser.parse(event) }.getOrNull()
        val recordedStateResult = recordedStateResult(event, audit)
        return recordedStateResult ?: prepareKnownAudit(request, event, requireNotNull(audit))
    }

    private fun recordedStateResult(
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit?,
    ): StoryMergeReversalPreparation? = when {
        event.reversibilityState == StoryMergeReversibility.NOT_AUTOMATICALLY_REVERSIBLE.name ->
            StoryMergeReversalPreparation.NotAutomaticallyReversible
        audit == null -> StoryMergeReversalPreparation.NotAutomaticallyReversible
        event.reversibilityState == StoryMergeReversibility.REQUIRES_REVIEW_TO_REVERSE.name ->
            review(audit, setOf(STORY_MERGE_REVERSAL_AUDIT_REQUIRES_REVIEW))
        event.reversibilityState != StoryMergeReversibility.REVERSIBLE.name ->
            StoryMergeReversalPreparation.NotAutomaticallyReversible
        event.policyVersion > RECONCILIATION_POLICY_VERSION ->
            review(audit, setOf(STORY_MERGE_REVERSAL_UNSUPPORTED_POLICY))
        else -> null
    }

    private suspend fun prepareKnownAudit(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit,
    ): StoryMergeReversalPreparation = database.canonicalCatalogDao()
        .canonicalState(audit.survivorStoryId.value)
        ?.let { currentState -> prepareCurrentState(request, event, audit, currentState) }
        ?: StoryMergeReversalPreparation.StalePlan

    private suspend fun prepareCurrentState(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit,
        currentState: StoryCanonicalStateEntity,
    ): StoryMergeReversalPreparation {
        val currentWork = database.canonicalCatalogDao().workForStory(audit.survivorStoryId.value)
        return if (currentState.identityRevision != request.expectedSurvivorIdentityRevision) {
            StoryMergeReversalPreparation.StalePlan
        } else {
            currentStateBlocker(currentState, currentWork)?.let { reason -> review(audit, setOf(reason)) }
                ?: prepareGraphPresence(request, event, audit)
        }
    }

    private fun currentStateBlocker(
        currentState: StoryCanonicalStateEntity,
        currentWork: List<CanonicalEngineWorkEntity>,
    ): String? = when {
        currentState.health == "DEGRADED" -> STORY_MERGE_REVERSAL_CANONICAL_DEGRADED
        currentWork.any { it.nextAttemptAtEpochMillis == Long.MAX_VALUE } -> STORY_MERGE_REVERSAL_PARKED_INVARIANT
        currentWork.any(::requiresUnsupportedPolicy) -> STORY_MERGE_REVERSAL_UNSUPPORTED_POLICY
        else -> null
    }

    private suspend fun prepareGraphPresence(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit,
    ): StoryMergeReversalPreparation {
        val catalogDao = database.catalogDao()
        val canonicalDao = database.canonicalCatalogDao()
        val survivorRedirect = canonicalDao.redirect(audit.survivorStoryId.value)
        val retiredExists = catalogDao.findStory(audit.retiredStoryId.value) != null
        return if (retiredExists || survivorRedirect != null) {
            StoryMergeReversalPreparation.StalePlan
        } else {
            prepareForwardRedirect(request, event, audit)
        }
    }

    private suspend fun prepareForwardRedirect(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit,
    ): StoryMergeReversalPreparation = database.canonicalCatalogDao().redirect(audit.retiredStoryId.value)
        ?.let { redirect ->
            val redirectMatchesMerge =
                redirect.canonicalStoryId == audit.survivorStoryId.value &&
                    redirect.mergeEventId == event.mergeEventId
            if (!redirectMatchesMerge) {
                review(audit, setOf(STORY_MERGE_REVERSAL_REDIRECT_CHANGED))
            } else {
                prepareCorrectionCase(request, event, audit)
            }
        }
        ?: StoryMergeReversalPreparation.StalePlan

    private suspend fun prepareCorrectionCase(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit,
    ): StoryMergeReversalPreparation = if (caseMatches(request, event.survivorStoryId, event.retiredStoryId)) {
        prepareLineage(audit, request, event)
    } else {
        StoryMergeReversalPreparation.StalePlan
    }

    private suspend fun prepareLineage(
        audit: StoryMergeReversalAudit,
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
    ): StoryMergeReversalPreparation {
        val nestedRedirect = database.canonicalCatalogDao().redirects().any { row ->
            row.retiredStoryId != audit.retiredStoryId.value && row.canonicalStoryId == audit.survivorStoryId.value
        }
        return if (nestedRedirect) {
            review(audit, setOf(STORY_MERGE_REVERSAL_NESTED_REDIRECT_LINEAGE))
        } else {
            prepareCurrentSnapshot(audit, request, event)
        }
    }

    private suspend fun prepareCurrentSnapshot(
        audit: StoryMergeReversalAudit,
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
    ): StoryMergeReversalPreparation = reader.read(audit.survivorStoryId)
        ?.let { current -> prepareSnapshot(current, audit, request, event) }
        ?: StoryMergeReversalPreparation.StalePlan

    private fun prepareSnapshot(
        current: StoryMergeSnapshot,
        audit: StoryMergeReversalAudit,
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
    ): StoryMergeReversalPreparation {
        val blockers = reversalBlockers(current, audit)
        return if (blockers.isNotEmpty()) {
            review(audit, blockers)
        } else {
            ready(request, event, audit)
        }
    }

    private fun ready(
        request: StoryMergeReverseRequest,
        event: StoryMergeEventEntity,
        audit: StoryMergeReversalAudit,
    ): StoryMergeReversalPreparation.Ready = StoryMergeReversalPreparation.Ready(
        plan = PreparedStoryMergeReversal(request, event, audit),
        assessment = StoryMergeReversalAssessment(
            mergeEventId = event.mergeEventId,
            survivingStoryId = audit.survivorStoryId,
            restoredStoryId = audit.retiredStoryId,
            reversibility = StoryMergeReversibility.REVERSIBLE,
            reasonCodes = emptySet(),
        ),
    )

    private suspend fun caseMatches(
        request: StoryMergeReverseRequest,
        survivorStoryId: String,
        retiredStoryId: String,
    ): Boolean = request.expectedReconciliationCaseId?.let { expectedCaseId ->
        val expectedRevision = requireNotNull(request.expectedReconciliationCaseRevision)
        database.canonicalCatalogDao().reconciliationCase(expectedCaseId)?.let { case ->
            case.status == "PENDING" &&
                setOf(case.leftStoryId, case.rightStoryId) == setOf(survivorStoryId, retiredStoryId) &&
                database.canonicalCatalogDao().reconciliationRevisions(expectedCaseId).size.toLong() == expectedRevision
        } ?: false
    } ?: true

    private fun reversalBlockers(
        current: StoryMergeSnapshot,
        audit: StoryMergeReversalAudit,
    ): Set<String> = buildSet {
        val survivorBefore = audit.survivorBefore
        val retiredBefore = audit.retiredBefore
        val historicalSources = survivorBefore.sourceKeys + retiredBefore.sourceKeys
        if (current.sourceKeys != historicalSources) add(STORY_MERGE_REVERSAL_SOURCE_OWNERSHIP_CHANGED)
        if (!canonicalStateIsReversible(current, audit)) add(STORY_MERGE_REVERSAL_CANONICAL_STATE_CHANGED)
        addAll(
            libraryPolicy.reversalBlockers(
                audit.survivorStoryId,
                current.libraryEntry,
                survivorBefore.libraryEntry,
                retiredBefore.libraryEntry,
            ),
        )
        addAll(
            mappingPolicy.reversalBlockers(
                audit.survivorStoryId,
                current.mappings,
                current.rejections,
                survivorBefore.mappings,
                retiredBefore.mappings,
                survivorBefore.rejections,
                retiredBefore.rejections,
            ),
        )
        addAll(
            chapterPolicy.reversalBlockers(
                survivorStoryId = audit.survivorStoryId,
                currentGraph = current.chapterGraph,
                currentSyncStates = current.syncStates,
                survivorChapterIds = survivorBefore.chapterIds,
                retiredChapterIds = retiredBefore.chapterIds,
                survivorReleaseIds = survivorBefore.releaseIds,
                retiredReleaseIds = retiredBefore.releaseIds,
                historicalOverrides = survivorBefore.manualOverrides + retiredBefore.manualOverrides,
                historicalSyncStates = survivorBefore.syncStates + retiredBefore.syncStates,
            ),
        )
        addAll(
            progressPolicy.reversalBlockers(
                audit.survivorStoryId,
                current.readingProgress,
                survivorBefore.readingProgress,
                retiredBefore.readingProgress,
            ),
        )
    }

    private fun canonicalStateIsReversible(
        current: StoryMergeSnapshot,
        audit: StoryMergeReversalAudit,
    ): Boolean {
        val expectedPreference = when (
            val decision = sourcePreferencePolicy.plan(
                audit.survivorStoryId,
                audit.survivorBefore.sourcePreference,
                audit.retiredBefore.sourcePreference,
            )
        ) {
            is DomainMergeDecision.Ready -> decision.value
            is DomainMergeDecision.RequiresReview -> null
        }
        return current.contentType.name == audit.survivorBefore.contentType &&
            current.createdAtEpochMillis == audit.survivorBefore.createdAtEpochMillis &&
            expectedPreference != null &&
            current.sourcePreference == expectedPreference
    }

    private fun requiresUnsupportedPolicy(work: CanonicalEngineWorkEntity): Boolean =
        work.requiredPolicyVersion?.let { required ->
            runCatching { CanonicalEngineWorkType.valueOf(work.workType) }.getOrNull()?.let { type ->
                supportedPolicyVersion(type)?.let { supported -> required > supported } ?: true
            } ?: true
        } ?: false

    private fun supportedPolicyVersion(type: CanonicalEngineWorkType): Int? = when (type) {
        CanonicalEngineWorkType.FUSION_REBUILD -> FUSION_POLICY_VERSION
        CanonicalEngineWorkType.RECONCILIATION_REEVALUATION -> RECONCILIATION_POLICY_VERSION
        CanonicalEngineWorkType.POST_MERGE_DERIVED,
        CanonicalEngineWorkType.POLICY_REEVALUATION,
        -> null
    }

    private fun review(
        audit: StoryMergeReversalAudit,
        reasons: Set<String>,
    ): StoryMergeReversalPreparation.ReviewRequired = StoryMergeReversalPreparation.ReviewRequired(
        StoryMergeReversalAssessment(
            mergeEventId = audit.mergeEventId,
            survivingStoryId = audit.survivorStoryId,
            restoredStoryId = audit.retiredStoryId,
            reversibility = StoryMergeReversibility.REQUIRES_REVIEW_TO_REVERSE,
            reasonCodes = reasons,
        ),
    )
}
