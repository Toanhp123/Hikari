package app.openstory.catalog.runtime.retention

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.runtime.concurrency.CatalogMutationGate
import app.openstory.common.id.StoryId

class ActiveStoryPins(
    private val writePort: CatalogWritePort,
    private val mutationGate: CatalogMutationGate,
    private val onActivePinsChanged: (Int) -> Unit = {},
    private val onReleaseMutationTouched: (Int) -> Unit = {},
) {
    private val active = linkedSetOf<StorySourceRef>()

    suspend fun register(ref: StorySourceRef) {
        val activeCount = mutationGate.withMutation {
            if (ref !in active && active.size == MAX_ACTIVE_STORY_PINS) {
                throw CatalogFailureException(CatalogFailure.InternalInvariant(ACTIVE_PIN_LIMIT_CODE))
            }
            active += ref
            active.size
        }
        onActivePinsChanged(activeCount)
    }

    suspend fun release(
        ref: StorySourceRef,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics {
        var activeCount: Int? = null
        val diagnostics = try {
            mutationGate.withMutation {
                active.remove(ref)
                activeCount = active.size
                writePort.releaseStoryDemand(
                    ref = ref,
                    retentionProtectedStoryIds = snapshotWithinMutation(),
                    releasedAtEpochMs = releasedAtEpochMs,
                )
            }
        } finally {
            activeCount?.let(onActivePinsChanged)
        }
        onReleaseMutationTouched(diagnostics.touchedStoryIds.size)
        return diagnostics
    }

    suspend fun snapshot(): Set<StoryId> = mutationGate.withMutation(::snapshotWithinMutation)

    internal suspend fun unregisterFailedActivation(ref: StorySourceRef) {
        val activeCount = mutationGate.withMutation {
            active.remove(ref)
            active.size
        }
        onActivePinsChanged(activeCount)
    }

    internal suspend fun <T> withMutationSnapshot(
        block: suspend (Set<StoryId>) -> T,
    ): T = mutationGate.withMutation { block(snapshotWithinMutation()) }

    private fun snapshotWithinMutation(): Set<StoryId> = active.mapTo(linkedSetOf()) { it.storyId }

    companion object {
        const val MAX_ACTIVE_STORY_PINS = 2
        private const val ACTIVE_PIN_LIMIT_CODE = "active_story_pin_limit"
    }
}
