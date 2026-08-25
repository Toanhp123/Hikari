package app.openstory.reader.routing

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.ReaderPlanRevision

@JvmInline
value class ReaderSessionId(val value: Long) {
    init {
        require(value > 0L) { "ReaderSessionId must be positive: $value" }
    }
}

@JvmInline
value class ReaderGenerationId(val value: Long) {
    init {
        require(value > 0L) { "ReaderGenerationId must be positive: $value" }
    }
}

internal data class ReaderExecutionIdentity(
    val sessionId: ReaderSessionId,
    val generationId: ReaderGenerationId,
    val planRevision: ReaderPlanRevision,
    val targetChapterId: CanonicalChapterId,
)

internal data class ReaderAttemptIdentity(
    val sessionId: ReaderSessionId,
    val generationId: ReaderGenerationId,
    val planRevision: ReaderPlanRevision,
    val attemptId: String,
    val targetChapterId: CanonicalChapterId,
) {
    init {
        require(attemptId.isNotBlank()) { "Reader attempt ID must not be blank." }
    }
}

internal data class ReaderCommittedIdentity(
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val documentFingerprint: String,
) {
    init {
        require(documentFingerprint.isNotBlank()) { "Committed document fingerprint must not be blank." }
    }
}

internal sealed interface ReaderExecutionState {
    data object Idle : ReaderExecutionState

    data class Planning(val identity: ReaderExecutionIdentity) : ReaderExecutionState

    data class Executing(val attempt: ReaderAttemptIdentity) : ReaderExecutionState

    data class Competing(
        val primary: ReaderAttemptIdentity,
        val hedge: ReaderAttemptIdentity,
    ) : ReaderExecutionState

    data class Recovering(val attempt: ReaderAttemptIdentity) : ReaderExecutionState

    data class Validating(val attempt: ReaderAttemptIdentity) : ReaderExecutionState

    data class Committed(
        val identity: ReaderExecutionIdentity,
        val committed: ReaderCommittedIdentity,
    ) : ReaderExecutionState

    data class Exhausted(
        val identity: ReaderExecutionIdentity,
        val code: String,
        val retryable: Boolean,
    ) : ReaderExecutionState {
        init {
            require(code.isNotBlank()) { "Reader exhaustion code must not be blank." }
        }
    }

    data class Cancelled(val identity: ReaderExecutionIdentity) : ReaderExecutionState
}
