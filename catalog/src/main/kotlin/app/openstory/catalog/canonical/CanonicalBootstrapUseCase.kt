package app.openstory.catalog.canonical

import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.common.id.StoryId

class CanonicalBootstrapUseCase(
    private val canonical: CanonicalCatalogRepository,
    private val rebuilder: CanonicalGenerationRebuilder,
) {
    suspend fun ensureReady(storyId: StoryId): CanonicalStoryState {
        val current = requireNotNull(canonical.state(storyId)) {
            "Missing canonical Story state for ${storyId.value}"
        }
        if (current is CanonicalStoryState.Ready) return current

        rebuilder.rebuild(storyId, CanonicalFusionReason.BOOTSTRAP)
        return requireNotNull(canonical.state(storyId)) {
            "Canonical Story state disappeared during bootstrap for ${storyId.value}"
        }
    }

    suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult =
        rebuilder.rebuild(storyId, reason)

    suspend fun prewarm(storyIds: List<StoryId>, limit: Int) {
        require(limit >= 0)
        storyIds.distinct().take(limit).forEach { storyId ->
            ensureReady(storyId)
        }
    }
}
