package app.openstory.catalog.ui.discover

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class DiscoverCanonicalBootstrapPipeline @Inject constructor(
    private val bootstrap: CanonicalBootstrapUseCase,
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun prewarm(storyIds: List<StoryId>) = withContext(dispatcher) {
        storyIds.distinct().forEach { storyId ->
            try {
                bootstrap.ensureReady(storyId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Best-effort prewarm: one invalid local Story must not block the remaining visible set.
            }
        }
    }
}
