package app.openstory.storage.room.catalog

import androidx.room.withTransaction
import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.diagnostics.CanonicalTraceKind
import app.openstory.catalog.diagnostics.NoOpCanonicalDiagnosticsSink
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReconciliationCaseRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: CanonicalCatalogDao,
    private val diagnostics: CanonicalDiagnostics = CanonicalDiagnostics(NoOpCanonicalDiagnosticsSink),
) : ReconciliationCaseRepository {
    constructor(
        database: OpenStoryDatabase,
        diagnostics: CanonicalDiagnostics = CanonicalDiagnostics(NoOpCanonicalDiagnosticsSink),
    ) : this(database, database.canonicalCatalogDao(), diagnostics)
    override fun observePending(): Flow<List<ReconciliationCase>> = dao.observePendingReconciliationCases().map {
        entities -> database.withTransaction { entities.mapNotNull { entity -> entity.toDomain() } }
    }

    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> =
        dao.observeReconciliationCasesForStory(storyId.value).map { entities ->
            database.withTransaction { entities.mapNotNull { entity -> entity.toDomain() } }
        }

    override suspend fun find(caseId: String): ReconciliationCase? =
        database.withTransaction { dao.reconciliationCase(caseId)?.toDomain() }

    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = database.withTransaction {
        dao.reconciliationCase(key.left.value, key.right.value)
            ?.takeUnless { it.status == ReconciliationCaseStatus.SUPERSEDED.name }
            ?.toDomain()
    }

    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? {
        require(evaluatedAtEpochMillis >= 0L)
        if (assessment.semanticDecision == ReconciliationSemanticDecision.NO_MATCH) return findActive(key)
        var reopenedFrom: ReconciliationCaseStatus? = null
        var previousFingerprint: String? = null
        val recorded = database.withTransaction {
            val existing = dao.reconciliationCase(key.left.value, key.right.value)
            val current = existing?.toDomain()
            if (current != null &&
                current.evidenceFingerprint == assessment.identityEvidenceFingerprint &&
                current.policyVersion == assessment.policyVersion
            ) {
                return@withTransaction current
            }

            val caseId = existing?.caseId ?: caseId(key)
            val revisionNumber = (existing?.let { dao.reconciliationRevisions(caseId).size } ?: 0) + 1
            val status = when (assessment.semanticDecision) {
                ReconciliationSemanticDecision.DIFFERENT_WORK -> ReconciliationCaseStatus.RESOLVED_SEPARATE
                ReconciliationSemanticDecision.SAME_WORK,
                ReconciliationSemanticDecision.REVIEW,
                -> ReconciliationCaseStatus.PENDING
                ReconciliationSemanticDecision.NO_MATCH -> error("NO_MATCH does not create reconciliation revisions")
            }
            if (status == ReconciliationCaseStatus.PENDING && current != null && current.status in RESOLVED_STATUSES) {
                reopenedFrom = current.status
                previousFingerprint = current.evidenceFingerprint
            }
            val origin = if (status == ReconciliationCaseStatus.RESOLVED_SEPARATE) {
                ReconciliationResolutionOrigin.ENGINE
            } else {
                null
            }
            val revision = assessment.toEntity(
                revisionId = revisionId(caseId, revisionNumber),
                caseId = caseId,
                key = key,
                resolutionOrigin = origin,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            )
            val caseEntity = ReconciliationCaseEntity(
                caseId = caseId,
                leftStoryId = key.left.value,
                rightStoryId = key.right.value,
                status = status.name,
                currentRevisionId = revision.revisionId,
                contextualDeferredAtEpochMillis = null,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: evaluatedAtEpochMillis,
                updatedAtEpochMillis = evaluatedAtEpochMillis,
            )
            if (existing == null) {
                dao.upsertReconciliationCase(caseEntity.copy(currentRevisionId = null))
            }
            dao.insertReconciliationRevision(revision)
            dao.upsertReconciliationCase(caseEntity)
            dao.reconciliationCase(caseId)?.toDomain()
        }
        reopenedFrom?.let { previousStatus ->
            diagnostics.record(
                CanonicalDecisionTrace(
                    kind = CanonicalTraceKind.CASE_REOPENED,
                    storyIds = setOf(key.left, key.right),
                    policyVersions = mapOf("reconciliation" to assessment.policyVersion),
                    reasonCodes = listOf(
                        "case.reopened",
                        "case.previous.${previousStatus.name.lowercase()}",
                        "decision.${assessment.semanticDecision.name.lowercase()}",
                    ),
                    evidenceFingerprints = listOfNotNull(
                        previousFingerprint,
                        assessment.identityEvidenceFingerprint,
                    ),
                ),
            )
        }
        return recorded
    }

    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean = database.withTransaction {
        resolveSeparateInCurrentTransaction(caseId, expectedRevision, origin, resolvedAtEpochMillis)
    }

    internal suspend fun resolveSeparateInCurrentTransaction(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean {
        require(resolvedAtEpochMillis >= 0L)
        val currentEntity = dao.reconciliationCase(caseId)
        val current = currentEntity?.toDomain()
        val matches = current != null &&
            current.revision == expectedRevision &&
            current.status == ReconciliationCaseStatus.PENDING
        return if (!matches) {
            false
        } else {
            val resolved = requireNotNull(current)
            val entity = requireNotNull(currentEntity)
            val nextRevisionNumber = Math.addExact(expectedRevision, 1L)
            val revision = resolved.assessment.toEntity(
                revisionId = revisionId(caseId, nextRevisionNumber.toInt()),
                caseId = caseId,
                key = resolved.key,
                resolutionOrigin = origin,
                evaluatedAtEpochMillis = resolvedAtEpochMillis,
            )
            dao.insertReconciliationRevision(revision)
            dao.upsertReconciliationCase(
                entity.copy(
                    status = ReconciliationCaseStatus.RESOLVED_SEPARATE.name,
                    currentRevisionId = revision.revisionId,
                    contextualDeferredAtEpochMillis = null,
                    updatedAtEpochMillis = resolvedAtEpochMillis,
                ),
            )
            true
        }
    }

    override suspend fun defer(
        caseId: String,
        expectedRevision: Long,
        suppressUntilEpochMillis: Long,
    ): Boolean {
        require(suppressUntilEpochMillis >= 0L)
        return database.withTransaction {
            val currentEntity = dao.reconciliationCase(caseId) ?: return@withTransaction false
            val current = currentEntity.toDomain() ?: return@withTransaction false
            if (current.revision != expectedRevision || current.status != ReconciliationCaseStatus.PENDING) {
                return@withTransaction false
            }
            val effectiveSuppression =
                maxOf(currentEntity.contextualDeferredAtEpochMillis ?: 0L, suppressUntilEpochMillis)
            dao.upsertReconciliationCase(
                currentEntity.copy(contextualDeferredAtEpochMillis = effectiveSuppression),
            )
            true
        }
    }

    private suspend fun ReconciliationCaseEntity.toDomain(): ReconciliationCase? {
        val revisionId = currentRevisionId
        return revisionId?.let { id ->
            dao.reconciliationRevision(id)?.let { revision ->
                val allRevisions = dao.reconciliationRevisions(caseId)
                val key = ReconciliationCaseKey.of(StoryId(leftStoryId), StoryId(rightStoryId))
                ReconciliationCase(
                    id = caseId,
                    key = key,
                    status = ReconciliationCaseStatus.valueOf(status),
                    assessment = revision.toAssessment(),
                    evidenceFingerprint = revision.identityFingerprint,
                    policyVersion = revision.policyVersion,
                    resolutionOrigin = revision.resolutionOrigin?.let(ReconciliationResolutionOrigin::valueOf),
                    contextualPromptSuppressedUntilEpochMillis = contextualDeferredAtEpochMillis,
                    revision = allRevisions.size.toLong(),
                    createdAtEpochMillis = createdAtEpochMillis,
                    lastEvaluatedAtEpochMillis = revision.evaluatedAtEpochMillis,
                )
            }
        }
    }

    private fun ReconciliationAssessment.toEntity(
        revisionId: String,
        caseId: String,
        key: ReconciliationCaseKey,
        resolutionOrigin: ReconciliationResolutionOrigin?,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCaseRevisionEntity = ReconciliationCaseRevisionEntity(
        revisionId = revisionId,
        caseId = caseId,
        leftStoryId = key.left.value,
        rightStoryId = key.right.value,
        decision = semanticDecision.name,
        identityFingerprint = identityEvidenceFingerprint,
        policyVersion = policyVersion,
        score = confidence,
        titleSimilarity = titleSimilarity,
        authorSimilarity = authorSimilarity,
        reasonCodes = reasons.mapTo(linkedSetOf()) { it.name },
        hardConflicts = encodeTrace(this),
        resolutionOrigin = resolutionOrigin?.name,
        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
    )

    private fun ReconciliationCaseRevisionEntity.toAssessment(): ReconciliationAssessment {
        val trace = decodeTrace(hardConflicts)
        return ReconciliationAssessment(
            policyVersion = policyVersion,
            semanticDecision = ReconciliationSemanticDecision.valueOf(decision),
            mergeEligibility = trace.eligibility,
            confidence = score,
            titleSimilarity = titleSimilarity,
            authorSimilarity = authorSimilarity,
            winningLead = trace.winningLead,
            matchedIdentifiers = trace.matchedIdentifiers,
            conflictingIdentifiers = trace.conflictingIdentifiers,
            reasons = reasonCodes.mapNotNullTo(linkedSetOf()) { value ->
                runCatching { ReconciliationReasonCode.valueOf(value) }.getOrNull()
            },
            identityEvidenceFingerprint = identityFingerprint,
        )
    }

    private fun encodeTrace(assessment: ReconciliationAssessment): Set<String> = buildSet {
        add("eligibility:${assessment.mergeEligibility.name}")
        assessment.winningLead?.let { add("lead:$it") }
        assessment.matchedIdentifiers.sortedWith(identifierOrdering).forEach { identifier ->
            add("matched:${identifier.encode()}")
        }
        assessment.conflictingIdentifiers.sortedWith(identifierOrdering).forEach { identifier ->
            add("conflict:${identifier.encode()}")
        }
    }

    private fun decodeTrace(values: Set<String>): AssessmentTrace {
        var eligibility = ReconciliationMergeEligibility.MERGEABLE
        var winningLead: Double? = null
        val matched = linkedSetOf<ExternalIdentifier>()
        val conflicting = linkedSetOf<ExternalIdentifier>()
        values.sorted().forEach { value ->
            when {
                value.startsWith("eligibility:") -> runCatching {
                    ReconciliationMergeEligibility.valueOf(value.substringAfter(':'))
                }.getOrNull()?.let { eligibility = it }
                value.startsWith("lead:") -> value.substringAfter(':').toDoubleOrNull()?.let { winningLead = it }
                value.startsWith("matched:") -> decodeIdentifier(value.substringAfter(':'))?.let(matched::add)
                value.startsWith("conflict:") -> decodeIdentifier(value.substringAfter(':'))?.let(conflicting::add)
            }
        }
        return AssessmentTrace(eligibility, winningLead, matched, conflicting)
    }

    private fun ExternalIdentifier.encode(): String = listOf(
        scope.name,
        encodeText(namespace),
        encodeText(value),
    ).joinToString(":")

    private fun decodeIdentifier(value: String): ExternalIdentifier? {
        val parts = value.split(':', limit = IDENTIFIER_PART_COUNT)
        if (parts.size != IDENTIFIER_PART_COUNT) return null
        return runCatching {
            ExternalIdentifier(
                namespace = decodeText(parts[1]),
                value = decodeText(parts[2]),
                scope = ExternalIdentifierScope.valueOf(parts[0]),
            )
        }.getOrNull()
    }

    private fun caseId(key: ReconciliationCaseKey): String {
        val pairDigest = digest("${key.left.value}|${key.right.value}").take(CASE_DIGEST_HEX)
        return "reconcile:$pairDigest"
    }

    private fun revisionId(caseId: String, revision: Int): String = "$caseId:r:$revision"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun encodeText(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray())
    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value))

    private data class AssessmentTrace(
        val eligibility: ReconciliationMergeEligibility,
        val winningLead: Double?,
        val matchedIdentifiers: Set<ExternalIdentifier>,
        val conflictingIdentifiers: Set<ExternalIdentifier>,
    )

    private companion object {
        val RESOLVED_STATUSES = setOf(
            ReconciliationCaseStatus.RESOLVED_MERGED,
            ReconciliationCaseStatus.RESOLVED_SEPARATE,
        )
        const val CASE_DIGEST_HEX = 16
        const val IDENTIFIER_PART_COUNT = 3
        val identifierOrdering: Comparator<ExternalIdentifier> = compareBy<ExternalIdentifier> { it.namespace }
            .thenBy { it.scope.name }
            .thenBy { it.value }
    }

}
