package app.openstory.common.execution

import kotlinx.coroutines.CompletableDeferred

interface ProcessWorkAdmission {
    suspend fun <T> run(
        resource: WorkResource,
        priority: WorkPriority,
        block: suspend () -> T,
    ): WorkAdmissionResult<T>
}

data class WorkAdmissionLimits(
    val maxActive: Int,
    val maxPending: Int,
    val reservedForegroundActive: Int,
) {
    init {
        require(maxActive > 0) { "Active work limit must be positive" }
        require(maxPending >= 0) { "Pending work limit must not be negative" }
        require(reservedForegroundActive in 0 until maxActive) {
            "Foreground reservation must leave at least one noncritical slot"
        }
    }
}

class BoundedProcessWorkAdmission(
    limitOverrides: Map<WorkResource, WorkAdmissionLimits> = emptyMap(),
) : ProcessWorkAdmission {
    private val configuredLimits = DEFAULT_LIMITS + limitOverrides
    private val states = WorkResource.entries.associateWith { resource ->
        ResourceState(
            limits = configuredLimits.getValue(resource),
        )
    }

    override suspend fun <T> run(
        resource: WorkResource,
        priority: WorkPriority,
        block: suspend () -> T,
    ): WorkAdmissionResult<T> {
        val state = states.getValue(resource)
        val request = PendingRequest(priority)
        val startsImmediately = synchronized(state.lock) {
            when {
                state.canStart(priority) -> {
                    state.admit(request)
                    true
                }

                state.pending.size < state.limits.maxPending -> {
                    state.pending += request
                    false
                }

                else -> null
            }
        }
        if (startsImmediately == null) {
            return WorkAdmissionResult.Rejected(WorkRejectionReason.SATURATED)
        }
        if (startsImmediately) {
            request.admitted.complete(Unit)
        }

        try {
            request.admitted.await()
            return WorkAdmissionResult.Completed(block())
        } finally {
            val newlyAdmitted = synchronized(state.lock) {
                if (request.isActive) {
                    state.release(request)
                    state.admitPending()
                } else {
                    state.pending.remove(request)
                    emptyList()
                }
            }
            newlyAdmitted.forEach { it.admitted.complete(Unit) }
        }
    }

    private class ResourceState(
        val limits: WorkAdmissionLimits,
    ) {
        val lock = Any()
        val pending = mutableListOf<PendingRequest>()
        var activeCount = 0
        var noncriticalActiveCount = 0

        fun canStart(priority: WorkPriority): Boolean =
            activeCount < limits.maxActive &&
                (
                    priority != WorkPriority.NONCRITICAL ||
                        noncriticalActiveCount < limits.maxActive - limits.reservedForegroundActive
                )

        fun admit(request: PendingRequest) {
            check(!request.isActive)
            request.isActive = true
            activeCount += 1
            if (request.priority == WorkPriority.NONCRITICAL) {
                noncriticalActiveCount += 1
            }
        }

        fun release(request: PendingRequest) {
            check(request.isActive)
            request.isActive = false
            activeCount -= 1
            if (request.priority == WorkPriority.NONCRITICAL) {
                noncriticalActiveCount -= 1
            }
        }

        fun admitPending(): List<PendingRequest> {
            val admitted = mutableListOf<PendingRequest>()
            while (true) {
                val index = nextAdmissibleIndex()
                if (index < 0) return admitted
                pending.removeAt(index).also { request ->
                    admit(request)
                    admitted += request
                }
            }
        }

        private fun nextAdmissibleIndex(): Int {
            WorkPriority.entries.forEach { priority ->
                val index = pending.indexOfFirst { request ->
                    request.priority == priority && canStart(priority)
                }
                if (index >= 0) return index
            }
            return -1
        }
    }

    private class PendingRequest(
        val priority: WorkPriority,
    ) {
        val admitted = CompletableDeferred<Unit>()
        var isActive = false
    }

    companion object {
        val DEFAULT_LIMITS: Map<WorkResource, WorkAdmissionLimits> = mapOf(
            WorkResource.NETWORK to WorkAdmissionLimits(
                maxActive = DEFAULT_NETWORK_ACTIVE,
                maxPending = DEFAULT_NETWORK_PENDING,
                reservedForegroundActive = DEFAULT_NETWORK_FOREGROUND_RESERVE,
            ),
            WorkResource.DECODE to WorkAdmissionLimits(
                maxActive = DEFAULT_DECODE_ACTIVE,
                maxPending = DEFAULT_DECODE_PENDING,
                reservedForegroundActive = DEFAULT_DECODE_FOREGROUND_RESERVE,
            ),
        )

        private const val DEFAULT_NETWORK_ACTIVE = 6
        private const val DEFAULT_NETWORK_PENDING = 12
        private const val DEFAULT_NETWORK_FOREGROUND_RESERVE = 2
        private const val DEFAULT_DECODE_ACTIVE = 4
        private const val DEFAULT_DECODE_PENDING = 8
        private const val DEFAULT_DECODE_FOREGROUND_RESERVE = 1
    }
}
