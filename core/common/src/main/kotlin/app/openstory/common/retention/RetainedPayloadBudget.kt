package app.openstory.common.retention

import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycle

data class RetainedPayloadOwner(
    val entryId: RouteEntryId,
    val units: Int,
    val lifecycle: RouteLifecycle,
    val recency: Long,
) {
    init {
        require(units >= 0) { "Retained payload units must not be negative" }
    }
}

data class RetentionDecision(
    val compactEntryIds: List<RouteEntryId>,
)

interface RetainedPayloadBudget {
    fun update(owner: RetainedPayloadOwner): RetentionDecision

    fun release(entryId: RouteEntryId)
}

class ProcessRetainedPayloadBudget(
    private val maxUnits: Int = DEFAULT_MAX_UNITS,
) : RetainedPayloadBudget {
    private val lock = Any()
    private val owners = mutableMapOf<RouteEntryId, RetainedPayloadOwner>()

    init {
        require(maxUnits >= 0) { "Retained payload budget must not be negative" }
    }

    override fun update(owner: RetainedPayloadOwner): RetentionDecision = synchronized(lock) {
        if (owner.lifecycle == RouteLifecycle.RELEASED) {
            owners.remove(owner.entryId)
            return@synchronized RetentionDecision(emptyList())
        }
        owners[owner.entryId] = owner

        var excessUnits = owners.values.sumOf { it.units.toLong() } - maxUnits
        if (excessUnits <= 0) {
            return@synchronized RetentionDecision(emptyList())
        }

        val compacted = mutableListOf<RouteEntryId>()
        owners.values
            .asSequence()
            .filter { it.lifecycle == RouteLifecycle.RETAINED && it.units > 0 }
            .sortedWith(compareBy(RetainedPayloadOwner::recency, { it.entryId.value }))
            .forEach { candidate ->
                if (excessUnits <= 0) return@forEach
                compacted += candidate.entryId
                excessUnits -= candidate.units
                owners[candidate.entryId] = candidate.copy(units = 0)
            }
        RetentionDecision(compacted)
    }

    override fun release(entryId: RouteEntryId) {
        synchronized(lock) {
            owners.remove(entryId)
        }
    }

    companion object {
        const val DEFAULT_MAX_UNITS = 240
    }
}
