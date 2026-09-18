package app.universalmedia.core.domain

import app.universalmedia.core.model.RootId

/** Serialized adapter evidence, never canonical identity. Do not log descriptor values. */
data class LocalRootDescriptor(val providerAuthority: String, val treeLocator: String) {
    override fun toString(): String = "LocalRootDescriptor(<redacted>)"
}

data class LocalDocumentLocator(val rootId: RootId, val providerAuthority: String, val documentLocator: String) {
    override fun toString(): String = "LocalDocumentLocator(rootId=$rootId, <redacted>)"
}

enum class LocalAccessState { READABLE, ACCESS_LOST, UNAVAILABLE }

data class StorageRoot(
    val id: RootId,
    val descriptor: LocalRootDescriptor,
    val configGeneration: Long,
    val access: LocalAccessState,
)

data class RootRegistrationEvidence(
    val descriptor: LocalRootDescriptor,
    val persistedReadAccess: Boolean,
    val observedAtEpochMs: Long,
)

interface StorageRootStore {
    /** Descriptor equality is reauthorization evidence, not an ID generation algorithm. */
    suspend fun registerOrReauthorize(evidence: RootRegistrationEvidence): StorageRoot

    suspend fun get(rootId: RootId): StorageRoot?
}

data class LocalDocumentObservation(
    val locator: LocalDocumentLocator,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedEpochMs: Long?,
    val observedAtEpochMs: Long,
)

enum class LocalAccessFailure { ACCESS_LOST, UNAVAILABLE, NOT_FOUND, TRANSIENT_PROVIDER_FAILURE }

enum class IncompleteReason { PROVIDER_LOADING, INACCESSIBLE_BRANCH, INVALID_PROVIDER_DATA, OBSERVATION_BUDGET, SUPERSEDED }

data class CoverageGap(val locator: LocalDocumentLocator?, val reason: IncompleteReason)

sealed interface TraversalResult {
    data object Complete : TraversalResult

    data class Incomplete(val gaps: List<CoverageGap>) : TraversalResult {
        init {
            require(gaps.isNotEmpty())
        }
    }

    data class Failed(val failure: LocalAccessFailure) : TraversalResult

    data object Cancelled : TraversalResult
}

fun interface ScanCancellation {
    fun isCancelled(): Boolean
}

interface LocalTreeObservationSource {
    /** Await each bounded batch for backpressure. Cancellation never means complete coverage. */
    suspend fun observe(
        root: StorageRoot,
        scope: DeclaredScanScope,
        cancellation: ScanCancellation,
        onBatch: suspend (List<LocalDocumentObservation>) -> Unit,
    ): TraversalResult
}
