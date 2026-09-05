package app.openstory.reader.routing

import app.openstory.common.FakeMonotonicClock
import app.openstory.common.id.PluginId
import app.openstory.reader.assets.ContentFetchArbiter
import app.openstory.reader.assets.ContentFetchPriority
import app.openstory.reader.engine.SourceOperationKey

internal enum class ReaderTestRemotePriority {
    FOREGROUND,
    PREFETCH,
}

internal class ReaderExecutionTestOwners {
    val sourceLane = ContentSourceExecutionLane()
    val fetchArbiter = ContentFetchArbiter(monotonicClock = FakeMonotonicClock(0L))
    val halfOpenProbeRegistry = ReaderHalfOpenProbeRegistry()

    suspend fun <T> withRemotePermit(
        sourceId: PluginId,
        priority: ReaderTestRemotePriority,
        block: suspend () -> T,
    ): T = sourceLane.withSource(
        sourceId,
        when (priority) {
            ReaderTestRemotePriority.FOREGROUND -> ContentSourceWorkPriority.FOREGROUND
            ReaderTestRemotePriority.PREFETCH -> ContentSourceWorkPriority.PREFETCH
        },
    ) {
        fetchArbiter.withAdmission(
            when (priority) {
                ReaderTestRemotePriority.FOREGROUND -> ContentFetchPriority.CRITICAL
                ReaderTestRemotePriority.PREFETCH -> ContentFetchPriority.PREFETCH
            },
            block,
        )
    }

    fun tryAcquireHalfOpenProbe(key: SourceOperationKey): ReaderHalfOpenProbeLease? =
        halfOpenProbeRegistry.tryAcquire(key)
}
