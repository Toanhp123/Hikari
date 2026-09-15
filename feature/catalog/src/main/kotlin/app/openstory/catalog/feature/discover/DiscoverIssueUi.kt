package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import java.util.concurrent.CancellationException

internal enum class DiscoverIssueKind {
    SOURCE_UNAVAILABLE,
    INVALID_SOURCE_DATA,
    ACQUISITION_FAILED,
    STORAGE_FAILED,
    ARTWORK_FAILED,
    INTERNAL_FAILURE,
}

internal data class DiscoverIssueUi(
    val kind: DiscoverIssueKind,
    val retryable: Boolean,
)

internal fun CatalogFailure.toDiscoverIssueUi(): DiscoverIssueUi = when (this) {
    CatalogFailure.SourceUnavailable -> DiscoverIssueUi(DiscoverIssueKind.SOURCE_UNAVAILABLE, false)
    is CatalogFailure.Validation,
    is CatalogFailure.IdentityCollision,
    -> DiscoverIssueUi(DiscoverIssueKind.INVALID_SOURCE_DATA, false)
    is CatalogFailure.Acquisition -> DiscoverIssueUi(DiscoverIssueKind.ACQUISITION_FAILED, true)
    is CatalogFailure.Storage -> DiscoverIssueUi(DiscoverIssueKind.STORAGE_FAILED, true)
    is CatalogFailure.Artwork -> DiscoverIssueUi(DiscoverIssueKind.ARTWORK_FAILED, reason.isRetryable)
    is CatalogFailure.InternalInvariant -> DiscoverIssueUi(DiscoverIssueKind.INTERNAL_FAILURE, false)
}

internal fun Throwable.toDiscoverIssueUi(): DiscoverIssueUi = when (this) {
    is CancellationException -> throw this
    is CatalogFailureException -> failure.toDiscoverIssueUi()
    else -> DiscoverIssueUi(DiscoverIssueKind.INTERNAL_FAILURE, false)
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
