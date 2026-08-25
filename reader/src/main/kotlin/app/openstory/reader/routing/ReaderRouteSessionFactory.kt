package app.openstory.reader.routing

import app.openstory.common.id.StoryId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope

class ReaderRouteSessionFactory(
    private val coordinator: ReaderRouteCoordinator,
    private val prefetchCoordinator: PrefetchCoordinator? = null,
) {
    fun create(storyId: StoryId): ReaderRouteSession = createSession(storyId, prefetchScope = null)

    fun create(storyId: StoryId, prefetchScope: CoroutineScope): ReaderRouteSession =
        createSession(storyId, prefetchScope)

    private fun createSession(
        storyId: StoryId,
        prefetchScope: CoroutineScope?,
    ): ReaderRouteSession = ReaderRouteSession(
        storyId = storyId,
        sessionId = ReaderSessionId(nextSessionId.getAndIncrement()),
        delegate = ReaderRouteExecutionDelegate(coordinator::execute),
        prefetchDelegate = prefetchCoordinator?.let { coordinator ->
            ReaderPrefetchExecutionDelegate(coordinator::prefetch)
        },
        prefetchScope = prefetchScope,
    )

    private companion object {
        val nextSessionId = AtomicLong(1L)
    }
}
