package app.openstory.catalog.feature.state

import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import java.util.concurrent.CancellationException

internal enum class CatalogIssueKind {
    SOURCE_UNAVAILABLE,
    INVALID_SOURCE_DATA,
    ACQUISITION_FAILED,
    STORAGE_FAILED,
    ARTWORK_FAILED,
    INTERNAL_FAILURE,
}

internal data class CatalogIssueUi(
    val kind: CatalogIssueKind,
    val retryable: Boolean,
)

internal fun CatalogFailure.toCatalogIssueUi(): CatalogIssueUi = when (this) {
    CatalogFailure.SourceUnavailable -> CatalogIssueUi(CatalogIssueKind.SOURCE_UNAVAILABLE, false)
    is CatalogFailure.Validation,
    is CatalogFailure.IdentityCollision,
    -> CatalogIssueUi(CatalogIssueKind.INVALID_SOURCE_DATA, false)
    is CatalogFailure.Acquisition -> CatalogIssueUi(CatalogIssueKind.ACQUISITION_FAILED, true)
    is CatalogFailure.Storage -> CatalogIssueUi(CatalogIssueKind.STORAGE_FAILED, true)
    is CatalogFailure.Artwork -> CatalogIssueUi(CatalogIssueKind.ARTWORK_FAILED, reason.isRetryable)
    is CatalogFailure.InternalInvariant -> CatalogIssueUi(CatalogIssueKind.INTERNAL_FAILURE, false)
}

internal fun Throwable.toCatalogIssueUi(): CatalogIssueUi = when (this) {
    is CancellationException -> throw this
    is CatalogFailureException -> failure.toCatalogIssueUi()
    else -> CatalogIssueUi(CatalogIssueKind.INTERNAL_FAILURE, false)
}

private val CatalogArtworkFailureReason.isRetryable: Boolean
    get() = when (this) {
        CatalogArtworkFailureReason.TIMEOUT,
        CatalogArtworkFailureReason.DECODE_FAILED,
        CatalogArtworkFailureReason.IO_FAILED,
        -> true
        CatalogArtworkFailureReason.INVALID_LOCATOR,
        CatalogArtworkFailureReason.POLICY_REJECTED,
        CatalogArtworkFailureReason.REDIRECT_REJECTED,
        CatalogArtworkFailureReason.MEDIA_TYPE_REJECTED,
        CatalogArtworkFailureReason.ENCODED_TOO_LARGE,
        CatalogArtworkFailureReason.DIMENSIONS_TOO_LARGE,
        -> false
    }
