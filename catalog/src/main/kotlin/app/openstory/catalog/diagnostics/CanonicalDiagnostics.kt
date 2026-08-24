package app.openstory.catalog.diagnostics

import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_TRACE_STORIES = 16
private const val MAX_TRACE_SOURCES = 16
private const val MAX_TRACE_POLICIES = 8
private const val MAX_TRACE_CODES = 16
private const val MAX_TRACE_FINGERPRINTS = 16
private const val MAX_TRACE_TOKEN_LENGTH = 128

object NoOpCanonicalDiagnosticsSink : CanonicalDiagnosticsSink {
    override fun record(trace: CanonicalDecisionTrace) = Unit
}

@Singleton
class CanonicalDiagnostics @Inject constructor(
    private val sink: CanonicalDiagnosticsSink,
) {
    fun record(trace: CanonicalDecisionTrace) {
        try {
            sink.record(trace.bounded())
        } catch (_: Exception) {
            // Diagnostics is deliberately fail-open. Canonical policy and mutation outcomes stay authoritative.
        }
    }
}

private fun CanonicalDecisionTrace.bounded(): CanonicalDecisionTrace = copy(
    storyIds = storyIds.asSequence()
        .filter { it.value.isSafeDiagnosticIdentifier() }
        .sortedBy { it.value }
        .take(MAX_TRACE_STORIES)
        .toCollection(linkedSetOf()),
    sourceKeys = sourceKeys.asSequence()
        .filter { key ->
            key.pluginId.value.isSafeDiagnosticIdentifier() && key.sourceId.isSafeDiagnosticIdentifier()
        }
        .sortedWith(compareBy({ it.pluginId.value }, { it.sourceId }))
        .take(MAX_TRACE_SOURCES)
        .toCollection(linkedSetOf()),
    policyVersions = policyVersions.entries.asSequence()
        .filter { (key, version) -> key.isSafeDiagnosticToken() && version > 0 }
        .sortedBy(Map.Entry<String, Int>::key)
        .take(MAX_TRACE_POLICIES)
        .associateTo(linkedMapOf()) { it.key to it.value },
    reasonCodes = reasonCodes.asSequence()
        .filter(String::isSafeDiagnosticToken)
        .distinct()
        .sorted()
        .take(MAX_TRACE_CODES)
        .toList(),
    evidenceFingerprints = evidenceFingerprints.asSequence()
        .filter(String::isSafeDiagnosticToken)
        .distinct()
        .sorted()
        .take(MAX_TRACE_FINGERPRINTS)
        .toList(),
)

private fun String.isSafeDiagnosticToken(): Boolean =
    isNotBlank() && length <= MAX_TRACE_TOKEN_LENGTH && all { character ->
        character.isLetterOrDigit() || character == '.' || character == '_' || character == ':' || character == '-'
    }

private fun String.isSafeDiagnosticIdentifier(): Boolean =
    isNotBlank() && length <= MAX_TRACE_TOKEN_LENGTH && none { it.isWhitespace() || it.isISOControl() }
