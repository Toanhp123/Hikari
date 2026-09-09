package app.openstory.catalog.domain.failure

import java.util.concurrent.CancellationException

enum class CatalogValidationReason { MALFORMED, OVER_LIMIT, AUTHORITY_MISMATCH, INVARIANT_VIOLATION }
enum class CatalogOperation { DISCOVER, STORY_DETAIL }
enum class CatalogStorageOperation {
    OPEN,
    READ_DISCOVER,
    READ_STORY,
    PUBLISH_DISCOVER,
    PUBLISH_STORY,
    RETENTION,
}

enum class CatalogArtworkFailureReason {
    INVALID_LOCATOR,
    POLICY_REJECTED,
    REDIRECT_REJECTED,
    TIMEOUT,
    MEDIA_TYPE_REJECTED,
    ENCODED_TOO_LARGE,
    DIMENSIONS_TOO_LARGE,
    DECODE_FAILED,
    IO_FAILED,
}

sealed interface CatalogFailure {
    data class Validation(
        val field: String,
        val reason: CatalogValidationReason,
    ) : CatalogFailure

    data object SourceUnavailable : CatalogFailure
    data class Acquisition(val operation: CatalogOperation) : CatalogFailure
    data class IdentityCollision(val storyId: String) : CatalogFailure
    data class Storage(val operation: CatalogStorageOperation) : CatalogFailure
    data class Artwork(val reason: CatalogArtworkFailureReason) : CatalogFailure
    data class InternalInvariant(val code: String) : CatalogFailure
}

class CatalogFailureException(
    val failure: CatalogFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.toString(), cause) {
    init {
        if (cause is CancellationException) throw cause
    }
}
