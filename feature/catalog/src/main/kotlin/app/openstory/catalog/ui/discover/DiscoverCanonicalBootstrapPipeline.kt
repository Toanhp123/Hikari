package app.openstory.catalog.ui.discover

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class DiscoverCanonicalBootstrapPipeline @Inject constructor(
    private val bootstrap: CanonicalBootstrapUseCase,
    private val projections: CatalogStoryProjectionRepository,
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal fun settle(
        storyIds: List<StoryId>,
        selectedContentType: ContentType,
    ): Flow<Map<StoryId, DiscoverCanonicalSettlement>> = flow {
        val expectedIds = storyIds.distinct()
        if (expectedIds.isEmpty()) {
            emit(emptyMap())
            return@flow
        }

        val settlements = linkedMapOf<StoryId, DiscoverCanonicalSettlement>()
        val seed = try {
            withContext(dispatcher) {
                projections.observeForStories(expectedIds.toSet()).first()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }

        val seedByStory = seed.associateBy { it.storyId }
        expectedIds.forEach { storyId ->
            val projection = seedByStory[storyId] ?: return@forEach
            settlements[storyId] = if (projection.contentType == selectedContentType) {
                DiscoverCanonicalSettlement.Projected(storyId, projection)
            } else {
                DiscoverCanonicalSettlement.ResolvedExcluded(
                    storyId,
                    DiscoverExclusionReason.CONTENT_TYPE_MISMATCH,
                )
            }
        }
        emit(settlements.toMap())

        expectedIds.forEach { storyId ->
            if (storyId in settlements) return@forEach
            settlements[storyId] = settleOne(storyId, selectedContentType)
            emit(settlements.toMap())
        }
    }

    private suspend fun settleOne(
        storyId: StoryId,
        selectedContentType: ContentType,
    ): DiscoverCanonicalSettlement = withContext(dispatcher) {
        val state = try {
            bootstrap.ensureReady(storyId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withContext DiscoverCanonicalSettlement.Failed(
                storyId,
                CatalogUiFailure(CANONICAL_BOOTSTRAP_FAILED, retryable = true),
            )
        }

        val ready = when (state) {
            is CanonicalStoryState.Preparing -> {
                return@withContext DiscoverCanonicalSettlement.Failed(
                    storyId,
                    CatalogUiFailure(CANONICAL_STILL_PREPARING, retryable = false),
                )
            }
            is CanonicalStoryState.Ready -> state
        }

        if (ready.story.contentType != selectedContentType) {
            return@withContext DiscoverCanonicalSettlement.ResolvedExcluded(
                storyId,
                DiscoverExclusionReason.CONTENT_TYPE_MISMATCH,
            )
        }

        val projection = try {
            projections.find(storyId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withContext DiscoverCanonicalSettlement.Failed(
                storyId,
                CatalogUiFailure(PROJECTION_LOOKUP_FAILED, retryable = true),
            )
        }
        projection?.let { DiscoverCanonicalSettlement.Projected(storyId, it) }
            ?: DiscoverCanonicalSettlement.Failed(
                storyId,
                CatalogUiFailure(PROJECTION_MISSING, retryable = true),
            )
    }

    private companion object {
        const val CANONICAL_STILL_PREPARING = "catalog.discover.canonical_still_preparing"
        const val CANONICAL_BOOTSTRAP_FAILED = "catalog.discover.canonical_bootstrap_failed"
        const val PROJECTION_LOOKUP_FAILED = "catalog.discover.projection_lookup_failed"
        const val PROJECTION_MISSING = "catalog.discover.projection_missing"
    }
}
