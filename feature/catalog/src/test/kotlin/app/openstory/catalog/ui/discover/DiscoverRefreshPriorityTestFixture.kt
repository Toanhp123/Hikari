package app.openstory.catalog.ui.discover

import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceFeedKind
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceLatestUpdate
import app.openstory.catalog.source.SourceSection
import app.openstory.common.id.StoryId

internal class RecordingDiscoverEngine :
    CanonicalEngineEventSink by app.openstory.catalog.FeatureNoOpCanonicalEngineEventSink {
    val immediateStoryIdBatches = mutableListOf<Set<StoryId>>()

    override suspend fun onEvidenceChanges(
        changes: List<CatalogEvidenceChange>,
        immediateStoryIds: Set<StoryId>,
    ) {
        immediateStoryIdBatches += immediateStoryIds
    }
}

internal fun discoverSections(itemsPerSection: Int): List<SourceSection> = listOf(
    SourceSection(
        sourceId = "popular",
        title = "Popular",
        kind = SourceFeedKind.POPULAR,
        items = (0 until itemsPerSection).map { index ->
            discoverSourceItem("popular-$index", popularityRank = index.toLong() + 1)
        },
    ),
    SourceSection(
        sourceId = "latest",
        title = "Latest",
        kind = SourceFeedKind.LATEST_UPDATES,
        items = (0 until itemsPerSection).map { index ->
            discoverSourceItem(
                id = "latest-$index",
                latestUpdate = SourceLatestUpdate(10_000L - index, "$index"),
            )
        },
    ),
    SourceSection(
        sourceId = "top",
        title = "Top",
        kind = SourceFeedKind.TOP_RATED,
        items = (0 until itemsPerSection).map { index ->
            discoverSourceItem(id = "top-$index", scoreValue = 10.0 - index / 10.0)
        },
    ),
)

private fun discoverSourceItem(
    id: String,
    popularityRank: Long? = null,
    latestUpdate: SourceLatestUpdate? = null,
    scoreValue: Double? = null,
) = SourceItem(
    sourceId = id,
    title = id,
    contentType = SourceContentType.MANGA,
    authors = emptySet(),
    coverUrl = null,
    scoreValue = scoreValue,
    scoreScale = scoreValue?.let { 10.0 },
    popularityRank = popularityRank,
    latestUpdate = latestUpdate,
)
