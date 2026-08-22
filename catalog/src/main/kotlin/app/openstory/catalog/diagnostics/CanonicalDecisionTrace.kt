package app.openstory.catalog.diagnostics

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.identity.SourceKey
import app.openstory.common.id.StoryId

enum class CanonicalTraceKind {
    RECONCILIATION,
    PRIMARY_SELECTION,
    FIELD_FUSION,
    MERGE_BLOCKED,
    MERGE_COMMITTED,
    GENERATION_FAILED,
    CASE_REOPENED,
    INVARIANT_VIOLATION,
}

data class CanonicalDecisionTrace(
    val kind: CanonicalTraceKind,
    val storyIds: Set<StoryId> = emptySet(),
    val sourceKeys: Set<SourceKey> = emptySet(),
    val policyVersions: Map<String, Int> = emptyMap(),
    val reasonCodes: List<String> = emptyList(),
    val evidenceFingerprints: List<String> = emptyList(),
    val field: CanonicalFieldKey? = null,
)

fun interface CanonicalDiagnosticsSink {
    fun record(trace: CanonicalDecisionTrace)
}
