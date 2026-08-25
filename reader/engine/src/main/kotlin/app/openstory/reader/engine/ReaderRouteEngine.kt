package app.openstory.reader.engine

import app.openstory.reader.engine.internal.DefaultReaderRouteEngine

interface ReaderRouteEngine {
    fun plan(
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): ReaderRouteDecision

    companion object {
        fun v1(): ReaderRouteEngine = DefaultReaderRouteEngine()
    }
}
