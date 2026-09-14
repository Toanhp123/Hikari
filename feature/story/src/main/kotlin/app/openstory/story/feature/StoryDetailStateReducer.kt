package app.openstory.story.feature

import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import app.openstory.story.feature.state.toStoryIssueUi
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun initialStoryDetailUiState(args: StoryRouteArgs): StoryDetailUiState {
    val initialSummary = args.preview?.title?.let { title ->
        StorySummaryUi(
            title = title,
            contentType = args.originMediaContext,
            ratingLabel = null,
            publicationStatus = null,
            latestUpdateLabel = null,
        )
    }
    return StoryDetailUiState(
        ref = args.ref,
        summary = initialSummary,
        detail = null,
        detailLoading = true,
        issue = null,
        artwork = StoryArtworkUi(
            assetKey = args.preview?.coverAssetKey,
            locator = args.preview?.coverLocator,
        ),
    )
}

internal fun StoryDetailSessionState.toStoryDetailUiState(
    previous: StoryDetailUiState,
): StoryDetailUiState {
    val currentProjection = projection
    val summary = currentProjection?.toSummaryUi() ?: previous.summary
    val detail = currentProjection?.detail?.let { richDetail ->
        StoryDetailUi(
            description = richDetail.description,
            authors = richDetail.authors,
            artists = richDetail.artists,
            genres = richDetail.genres,
            publicationStatus = richDetail.publicationStatus,
            language = richDetail.language,
        )
    } ?: previous.detail
    val issue = (acquisition as? CatalogAcquisitionStatus.Failed)?.failure?.toStoryIssueUi()
    val artwork = currentProjection?.summary?.let { currentSummary ->
        currentSummary.coverAssetKey?.let { assetKey ->
            StoryArtworkUi(assetKey = assetKey, locator = currentSummary.coverLocator)
        }
    } ?: previous.artwork

    return StoryDetailUiState(
        ref = currentProjection?.ref ?: previous.ref,
        summary = summary,
        detail = detail,
        detailLoading = currentProjection?.detail == null && issue == null,
        issue = issue,
        artwork = artwork,
    )
}

private fun StoryDetailProjection.toSummaryUi(): StorySummaryUi =
    StorySummaryUi(
        title = summary.title,
        contentType = summary.contentType,
        ratingLabel = summary.rating?.let { rating ->
            String.format(Locale.ROOT, "%.1f / %.0f", rating.value, rating.scale)
        },
        publicationStatus = summary.publicationStatusSummary,
        latestUpdateLabel = summary.latestUpdateEpochMs?.let(::formatLatestUpdate),
    )

private val STORY_UPDATE_DATE_FORMATTER = DateTimeFormatter
    .ofPattern("MMM d, uuuu", Locale.ENGLISH)
    .withZone(ZoneOffset.UTC)

private fun formatLatestUpdate(epochMs: Long): String =
    "Updated ${STORY_UPDATE_DATE_FORMATTER.format(Instant.ofEpochMilli(epochMs))}"
