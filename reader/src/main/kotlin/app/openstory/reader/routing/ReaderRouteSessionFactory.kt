package app.openstory.reader.routing

import app.openstory.common.id.StoryId
import java.util.concurrent.atomic.AtomicLong

class ReaderRouteSessionFactory(
    private val coordinator: ReaderRouteCoordinator,
) {
    fun create(storyId: StoryId): ReaderRouteSession = ReaderRouteSession(
        storyId = storyId,
        sessionId = ReaderSessionId(nextSessionId.getAndIncrement()),
        delegate = ReaderRouteExecutionDelegate(coordinator::execute),
    )

    private companion object {
        val nextSessionId = AtomicLong(1L)
    }
}
