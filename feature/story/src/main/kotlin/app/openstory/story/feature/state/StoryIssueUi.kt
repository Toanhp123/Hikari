package app.openstory.story.feature.state

import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException

internal enum class StoryIssueKind {
    SOURCE_UNAVAILABLE,
    INVALID_SOURCE_DATA,
    ACQUISITION_FAILED,
    STORAGE_FAILED,
    ARTWORK_FAILED,
    INTERNAL_FAILURE,
}

internal data class StoryIssueUi(
    val kind: StoryIssueKind,
    val retryable: Boolean,
)

internal fun CatalogFailure.toStoryIssueUi(): StoryIssueUi = when (this) {
    CatalogFailure.SourceUnavailable -> StoryIssueUi(StoryIssueKind.SOURCE_UNAVAILABLE, false)
    is CatalogFailure.Validation,
    is CatalogFailure.IdentityCollision,
    -> StoryIssueUi(StoryIssueKind.INVALID_SOURCE_DATA, false)
    is CatalogFailure.Acquisition -> StoryIssueUi(StoryIssueKind.ACQUISITION_FAILED, true)
    is CatalogFailure.Storage -> StoryIssueUi(StoryIssueKind.STORAGE_FAILED, true)
    is CatalogFailure.Artwork -> StoryIssueUi(StoryIssueKind.ARTWORK_FAILED, reason.isRetryable)
    is CatalogFailure.InternalInvariant -> StoryIssueUi(StoryIssueKind.INTERNAL_FAILURE, false)
}

internal fun CatalogFailureException.toStoryIssueUi(): StoryIssueUi = failure.toStoryIssueUi()

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
