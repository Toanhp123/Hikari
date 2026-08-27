package app.openstory.reader.routing

import kotlinx.coroutines.CancellationException

/**
 * Effect owner for bounded Reader prefetch. Semantic selection still belongs to the same
 * [ReaderRouteCoordinator]/HES engine path used by foreground routing.
 */
class PrefetchCoordinator(
    private val coordinator: ReaderRouteCoordinator,
) {
    internal suspend fun prefetch(
        session: ReaderRouteSession,
        context: ReaderRoutePlanningContext,
    ) {
        try {
            coordinator.executePrefetch(session, context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Prefetch is opportunistic. Foreground state and committed content stay authoritative.
        }
    }
}

internal fun interface ReaderPrefetchExecutionDelegate {
    suspend fun execute(
        session: ReaderRouteSession,
        context: ReaderRoutePlanningContext,
    )
}
