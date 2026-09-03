package app.openstory.reader.assets

import app.openstory.reader.routing.ReaderNetworkState

data class ReaderAssetPlan(
    val interactive: List<ReaderPageAssetDescriptor> = emptyList(),
    val currentAhead: List<ReaderPageAssetDescriptor> = emptyList(),
    val transition: List<ReaderPageAssetDescriptor> = emptyList(),
) {
    companion object {
        val EMPTY = ReaderAssetPlan()
    }
}

class ReaderAssetPrefetchPlanner(
    private val workingSetPolicy: ReaderAssetWorkingSetPolicy = ReaderAssetWorkingSetPolicy(),
) {
    fun plan(
        manifest: ReaderAssetChapterManifest?,
        viewport: ReaderViewportSnapshot?,
        networkState: ReaderNetworkState,
        cachePressure: ReaderAssetCachePressure,
        prefetchedManifest: ReaderAssetChapterManifest?,
    ): ReaderAssetPlan {
        if (manifest == null || viewport == null || viewport.sessionId != manifest.sessionId) {
            return ReaderAssetPlan.EMPTY
        }
        val interactive = workingSetPolicy.visibleDescriptors(manifest, viewport).distinctBy { it.key }
        val currentAhead = currentAheadLimit(networkState, cachePressure)
            .takeIf { it > 0 }
            ?.let { limit -> rollingAhead(manifest, viewport, limit) }
            .orEmpty()
            .distinctBy { it.key }
        val transition = transitionLimit(viewport, networkState, cachePressure)
            .takeIf { it > 0 && prefetchedManifest?.sessionId == manifest.sessionId }
            ?.let { limit -> prefetchedManifest?.descriptors?.take(limit).orEmpty() }
            .orEmpty()
            .distinctBy { it.key }
        return ReaderAssetPlan(
            interactive = interactive,
            currentAhead = currentAhead,
            transition = transition,
        )
    }

    private fun currentAheadLimit(
        networkState: ReaderNetworkState,
        cachePressure: ReaderAssetCachePressure,
    ): Int = when {
        cachePressure == ReaderAssetCachePressure.EMERGENCY -> 0
        networkState == ReaderNetworkState.OFFLINE -> 0
        networkState == ReaderNetworkState.METERED -> ReaderAssetRuntimePolicy.METERED_NEAR_AHEAD_MAX
        else -> ReaderAssetRuntimePolicy.INTERACTIVE_CURRENT_AHEAD
    }

    private fun rollingAhead(
        manifest: ReaderAssetChapterManifest,
        viewport: ReaderViewportSnapshot,
        limit: Int,
    ): List<ReaderPageAssetDescriptor> {
        val anchor = when (viewport.direction) {
            ReaderViewportDirection.BACKWARD -> viewport.leadingVisibleImageOrdinal
            ReaderViewportDirection.FORWARD,
            ReaderViewportDirection.IDLE,
            -> viewport.trailingVisibleImageOrdinal
        } ?: return emptyList()
        val ordinals = when (viewport.direction) {
            ReaderViewportDirection.BACKWARD -> {
                (anchor - 1 downTo anchor - limit).toList()
            }
            ReaderViewportDirection.FORWARD,
            ReaderViewportDirection.IDLE,
            -> {
                (anchor + 1..anchor + limit).toList()
            }
        }
        return ordinals.mapNotNull(manifest.descriptors::getOrNull).take(limit)
    }

    private fun transitionLimit(
        viewport: ReaderViewportSnapshot,
        networkState: ReaderNetworkState,
        cachePressure: ReaderAssetCachePressure,
    ): Int = when {
        networkState != ReaderNetworkState.UNMETERED -> 0
        cachePressure != ReaderAssetCachePressure.NORMAL -> 0
        viewport.chapterProgressBasisPoints >= ReaderAssetRuntimePolicy.NEAR_END_BASIS_POINTS ->
            ReaderAssetRuntimePolicy.NEAR_END_TRANSITION_FRONTIER
        viewport.chapterProgressBasisPoints >= ReaderAssetRuntimePolicy.APPROACHING_END_BASIS_POINTS ->
            ReaderAssetRuntimePolicy.APPROACHING_END_TRANSITION_FRONTIER
        else -> 0
    }
}
