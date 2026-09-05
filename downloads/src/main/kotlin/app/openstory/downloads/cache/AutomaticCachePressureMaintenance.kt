package app.openstory.downloads.cache

import app.openstory.reader.assets.ReaderAssetDiagnosticEvent
import app.openstory.reader.assets.ReaderAssetDiagnosticsSink
import app.openstory.reader.assets.recordSafely
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class AutomaticCacheEmergencyReliefReport(
    val victimsProcessed: Int,
    val physicallyReclaimedBytes: Long,
    val reserveRestored: Boolean,
    val hasMoreVictims: Boolean,
    val madeProgress: Boolean,
)

data class AutomaticCacheBudgetSnapshot(
    val quotaBytes: Long,
    val committedBytes: Long,
    val pendingReservationBytes: Long,
    val activeProtectedOverflowBytes: Long,
) {
    val totalAccountedBytes: Long
        get() = committedBytes.saturatedAdd(pendingReservationBytes)
}

internal data class AutomaticCachePhysicalRelief(
    val madeProgress: Boolean,
    val physicallyReclaimedBytes: Long,
) {
    companion object {
        val NONE = AutomaticCachePhysicalRelief(madeProgress = false, physicallyReclaimedBytes = 0L)
    }
}

internal class AutomaticCachePressureMaintenance(
    private val scope: CoroutineScope,
    private val diagnostics: ReaderAssetDiagnosticsSink,
    private val maxPhysicalPressureVictims: Int,
    private val physicalCandidates: suspend () -> List<AutomaticCacheCandidate>,
    private val emergencyCandidates: suspend () -> List<AutomaticCacheCandidate>,
    private val relievePhysicalCandidate: suspend (AutomaticCacheCandidate) -> AutomaticCachePhysicalRelief,
    private val relieveEmergencyCandidate: suspend (AutomaticCacheCandidate) -> AutomaticCachePhysicalRelief,
    private val hasEmergencyVictims: suspend () -> Boolean,
) {
    private val emergencyScheduled = AtomicBoolean(false)

    suspend fun relievePhysicalPressure(requiredBytes: Long): Long {
        require(requiredBytes >= 0L) { "Required relief bytes must not be negative." }
        if (requiredBytes == 0L) return 0L
        var reclaimedBytes = 0L
        for (candidate in physicalCandidates().take(maxPhysicalPressureVictims)) {
            if (reclaimedBytes >= requiredBytes) break
            reclaimedBytes = reclaimedBytes.saturatedAdd(
                relievePhysicalCandidate(candidate).physicallyReclaimedBytes,
            )
        }
        diagnostics.recordSafely(ReaderAssetDiagnosticEvent.EvictionBytes(reclaimedBytes))
        return reclaimedBytes
    }

    suspend fun relieveEmergencyPressure(
        reserveRestored: () -> Boolean,
    ): AutomaticCacheEmergencyReliefReport {
        if (reserveRestored()) {
            return AutomaticCacheEmergencyReliefReport(
                victimsProcessed = 0,
                physicallyReclaimedBytes = 0L,
                reserveRestored = true,
                hasMoreVictims = false,
                madeProgress = false,
            )
        }
        val candidates = emergencyCandidates()
        var processed = 0
        var reclaimedBytes = 0L
        var restored = false
        var madeProgress = false
        for (candidate in candidates.take(MAX_EMERGENCY_RECONCILIATION_VICTIMS)) {
            if (reserveRestored()) {
                restored = true
                break
            }
            processed += 1
            val relief = relieveEmergencyCandidate(candidate)
            madeProgress = madeProgress || relief.madeProgress
            reclaimedBytes = reclaimedBytes.saturatedAdd(relief.physicallyReclaimedBytes)
            if (reserveRestored()) {
                restored = true
                break
            }
        }
        if (!restored) restored = reserveRestored()
        diagnostics.recordSafely(ReaderAssetDiagnosticEvent.EvictionBytes(reclaimedBytes))
        return AutomaticCacheEmergencyReliefReport(
            victimsProcessed = processed,
            physicallyReclaimedBytes = reclaimedBytes,
            reserveRestored = restored,
            hasMoreVictims = !restored && hasEmergencyVictims(),
            madeProgress = madeProgress,
        )
    }

    fun requestEmergencyReconciliation(reserveRestored: () -> Boolean) {
        if (!emergencyScheduled.compareAndSet(false, true)) return
        scope.launch {
            var reschedule = false
            try {
                val report = relieveEmergencyPressure(reserveRestored)
                reschedule = !report.reserveRestored &&
                    report.madeProgress &&
                    report.victimsProcessed >= MAX_EMERGENCY_RECONCILIATION_VICTIMS &&
                    report.hasMoreVictims
            } finally {
                emergencyScheduled.set(false)
                if (reschedule) requestEmergencyReconciliation(reserveRestored)
            }
        }
    }

    private companion object {
        const val MAX_EMERGENCY_RECONCILIATION_VICTIMS = 32
    }
}

internal fun Long.saturatedAdd(other: Long): Long =
    if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

internal fun Long.basisPoints(basisPoints: Int): Long =
    (this / BASIS_POINTS) * basisPoints + (this % BASIS_POINTS) * basisPoints / BASIS_POINTS

internal fun Long.incrementEpoch(): Long {
    check(this < Long.MAX_VALUE) { "Automatic cache epoch exhausted." }
    return this + 1L
}

private const val BASIS_POINTS = 10_000L
