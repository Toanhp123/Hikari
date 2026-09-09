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
) {
    private val active = linkedSetOf<StorySourceRef>()

    suspend fun register(ref: StorySourceRef) {
        mutationGate.withMutation {
            if (ref !in active && active.size == MAX_ACTIVE_STORY_PINS) {
                throw CatalogFailureException(CatalogFailure.InternalInvariant(ACTIVE_PIN_LIMIT_CODE))
            }
            active += ref
        }
    }

    suspend fun release(
        ref: StorySourceRef,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics = mutationGate.withMutation {
        active.remove(ref)
        writePort.releaseStoryDemand(
            ref = ref,
            retentionProtectedStoryIds = snapshotWithinMutation(),
            releasedAtEpochMs = releasedAtEpochMs,
        )
    }

    suspend fun snapshot(): Set<StoryId> = mutationGate.withMutation(::snapshotWithinMutation)

    internal suspend fun <T> withMutationSnapshot(
        block: suspend (Set<StoryId>) -> T,
    ): T = mutationGate.withMutation { block(snapshotWithinMutation()) }

    private fun snapshotWithinMutation(): Set<StoryId> = active.mapTo(linkedSetOf()) { it.storyId }

    companion object {
        const val MAX_ACTIVE_STORY_PINS = 2
        private const val ACTIVE_PIN_LIMIT_CODE = "active_story_pin_limit"
    }
}
