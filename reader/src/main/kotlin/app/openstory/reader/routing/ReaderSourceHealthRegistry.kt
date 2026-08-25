package app.openstory.reader.routing

import app.openstory.reader.engine.HealthPolicy
import app.openstory.reader.engine.SourceHealthOrigin
import app.openstory.reader.engine.SourceHealthReducer
import app.openstory.reader.engine.SourceHealthSnapshot
import app.openstory.reader.engine.SourceHealthState
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-lifetime, Reader-owned in-memory health. No persistence or Android dependency. */
class ReaderSourceHealthRegistry {
    private val reducer: SourceHealthReducer = SourceHealthReducer.v1()
    private val policy: HealthPolicy = HealthPolicy.v1()
    private data class Entry(
        val state: SourceHealthState,
        val observed: Boolean,
    )

    private val lock = Mutex()
    private val entries = mutableMapOf<SourceOperationKey, Entry>()

    internal suspend fun snapshot(
        key: SourceOperationKey,
        nowEpochMillis: Long,
    ): SourceHealthSnapshot = lock.withLock {
        val current = entries[key] ?: Entry(SourceHealthState(), observed = false)
        val advanced = reducer.advance(current.state, nowEpochMillis, policy)
        if (advanced != current.state || current.observed) {
            entries[key] = current.copy(state = advanced)
        }
        SourceHealthSnapshot(
            key = key,
            state = advanced,
            origin = if (current.observed) {
                SourceHealthOrigin.PROCESS_OBSERVED
            } else {
                SourceHealthOrigin.STARTUP_NEUTRAL
            },
        )
    }

    internal suspend fun record(
        key: SourceOperationKey,
        observation: SourceObservation,
        nowEpochMillis: Long,
    ) {
        lock.withLock {
            val previous = entries[key]?.state ?: SourceHealthState()
            entries[key] = Entry(
                state = reducer.reduce(previous, observation, nowEpochMillis, policy),
                observed = true,
            )
        }
    }
}
