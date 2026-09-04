package app.openstory.reader.routing

import app.openstory.common.id.StoryId
import app.openstory.reader.assets.ReaderAssetSessionPort
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class ReaderRouteSessionFactory(
    private val coordinator: ReaderRouteCoordinator,
    private val prefetchCoordinator: PrefetchCoordinator? = null,
    private val assetSessionPort: ReaderAssetSessionPort = ReaderAssetSessionPort.NO_OP,
) {
    fun create(storyId: StoryId): ReaderRouteSession = createSession(storyId, prefetchScope = null)

    fun create(storyId: StoryId, prefetchScope: CoroutineScope): ReaderRouteSession =
        createSession(storyId, prefetchScope)

    private fun createSession(
        storyId: StoryId,
        prefetchScope: CoroutineScope?,
    ): ReaderRouteSession {
        val session = ReaderRouteSession(
            storyId = storyId,
            sessionId = ReaderSessionId(nextSessionId.getAndIncrement()),
            delegate = ReaderRouteExecutionDelegate(coordinator::execute),
            refreshDelegate = ReaderSelectedReleaseRefreshDelegate(coordinator::refreshSelectedRelease),
            prefetchDelegate = prefetchCoordinator?.let { coordinator ->
                ReaderPrefetchExecutionDelegate(coordinator::prefetch)
            },
            prefetchScope = prefetchScope,
            assetSessionPort = assetSessionPort,
        )
        assetSessionPort.registerSelectedReleaseRefreshPort(session.sessionId, session)
        prefetchScope?.coroutineContext?.get(Job)?.invokeOnCompletion { session.close() }
        return session
    }

    private companion object {
        val nextSessionId = AtomicLong(1L)
    }
}
