package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.withContext

internal data class DiscoverProjectionResult(
    val content: DiscoverSemanticContent,
    val pendingSlots: Int,
    val failures: Map<StoryId, CatalogUiFailure>,
    val expectedSlots: Int,
)

class DiscoverProjectionPipeline @Inject constructor(
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun project(
        homes: List<CatalogHomeSnapshot>,
        projections: List<CatalogStoryProjection>,
        selectedContentType: ContentType,
        settlements: Map<StoryId, DiscoverCanonicalSettlement>,
    ): DiscoverProjectionResult = withContext(dispatcher) {
        val slots = discoverFeedSlots(homes, selectedContentType)
        val liveByStory = projections
            .asSequence()
            .filter { it.contentType == selectedContentType }
            .associateBy(CatalogStoryProjection::storyId)
        val failures = slots.expectedStoryIds.mapNotNull { storyId ->
            (settlements[storyId] as? DiscoverCanonicalSettlement.Failed)
                ?.let { storyId to it.failure }
        }.toMap()
        val pendingSlots = sequenceOf(slots.popular, slots.latestUpdates, slots.topRated)
            .flatten()
            .count { it !in settlements }

        DiscoverProjectionResult(
            content = DiscoverSemanticContent(
                selectedContentType = selectedContentType,
                popular = stablePrefix(slots.popular, settlements, liveByStory),
                latestUpdates = stablePrefix(slots.latestUpdates, settlements, liveByStory),
                topRated = stablePrefix(slots.topRated, settlements, liveByStory),
                sourceEmpty = homes.isEmpty(),
            ),
            pendingSlots = pendingSlots,
            failures = failures,
            expectedSlots = slots.size,
        )
    }

    /**
     * Compatibility bridge for the pre-CSC DiscoverViewModel. Task 8 removes this overload when
     * screen state starts owning settlement explicitly.
     */
    internal suspend fun project(
        homes: List<CatalogHomeSnapshot>,
        projections: List<CatalogStoryProjection>,
        selectedContentType: ContentType,
    ): DiscoverSemanticContent = withContext(dispatcher) {
        projectSemanticDiscoverContent(
            homes = homes,
            projections = projections,
            selectedContentType = selectedContentType,
        )
    }
}

private fun stablePrefix(
    slots: List<StoryId>,
    settlements: Map<StoryId, DiscoverCanonicalSettlement>,
    liveByStory: Map<StoryId, CatalogStoryProjection>,
): List<DiscoverStoryItem> = buildList {
    for (storyId in slots) {
        when (val settlement = settlements[storyId]) {
            null -> break
            is DiscoverCanonicalSettlement.Projected -> {
                val projection = liveByStory[storyId] ?: settlement.projection
                add(projection.toDiscoverItem())
            }
            is DiscoverCanonicalSettlement.ResolvedExcluded -> Unit
            is DiscoverCanonicalSettlement.Failed -> Unit
        }
    }
}
