package app.openstory.reader.assets

enum class ReaderAssetDiagnosticKind {
    MEMORY_HIT,
    DISK_HIT,
    NETWORK_FETCH,
    SINGLE_FLIGHT_JOIN,
    PRIORITY_PROMOTION,
    PREFETCH,
    LOCATOR_REFRESH,
    CORRUPTION,
    COMMIT_FAILURE,
    CACHE_PRESSURE,
    EVICTION_BYTES,
}

sealed interface ReaderAssetDiagnosticEvent {
    data object MemoryHit : ReaderAssetDiagnosticEvent
    data object DiskHit : ReaderAssetDiagnosticEvent
    data object NetworkFetch : ReaderAssetDiagnosticEvent
    data object SingleFlightJoin : ReaderAssetDiagnosticEvent
    data class PriorityPromotion(
        val from: ContentFetchPriority,
        val to: ContentFetchPriority,
    ) : ReaderAssetDiagnosticEvent
    data class Prefetch(val count: Int = 1) : ReaderAssetDiagnosticEvent {
        init {
            require(count >= 0) { "Reader asset prefetch diagnostic count must not be negative." }
        }
    }
    data object LocatorRefresh : ReaderAssetDiagnosticEvent
    data object Corruption : ReaderAssetDiagnosticEvent
    data class CommitFailure(val failure: ReaderAssetFailure) : ReaderAssetDiagnosticEvent
    data class CachePressure(val pressure: ReaderAssetCachePressure) : ReaderAssetDiagnosticEvent
    data class EvictionBytes(val physicallyReclaimedBytes: Long) : ReaderAssetDiagnosticEvent {
        init {
            require(physicallyReclaimedBytes >= 0L) {
                "Reader asset physically reclaimed bytes must not be negative."
            }
        }
    }
}

fun interface ReaderAssetDiagnosticsSink {
    fun record(event: ReaderAssetDiagnosticEvent)

    companion object {
        val NO_OP = ReaderAssetDiagnosticsSink { }
    }
}

fun ReaderAssetDiagnosticsSink.recordSafely(event: ReaderAssetDiagnosticEvent) {
    try {
        record(event)
    } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
        // Diagnostics are observational only and must never alter Reader/cache behavior.
    }
}

data class ReaderAssetDiagnosticsSnapshot(
    val eventCounts: Map<ReaderAssetDiagnosticKind, Long>,
    val physicallyReclaimedEvictionBytes: Long,
)

class ReaderAssetAggregateDiagnostics : ReaderAssetDiagnosticsSink {
    private val lock = Any()
    private val eventCounts = ReaderAssetDiagnosticKind.entries.associateWith { 0L }.toMutableMap()
    private var physicallyReclaimedEvictionBytes = 0L

    override fun record(event: ReaderAssetDiagnosticEvent) {
        synchronized(lock) {
            val kind = event.kind()
            val count = when (event) {
                is ReaderAssetDiagnosticEvent.Prefetch -> event.count.toLong()
                else -> 1L
            }
            eventCounts[kind] = eventCounts.getValue(kind).saturatedAdd(count)
            if (event is ReaderAssetDiagnosticEvent.EvictionBytes) {
                physicallyReclaimedEvictionBytes = physicallyReclaimedEvictionBytes.saturatedAdd(
                    event.physicallyReclaimedBytes,
                )
            }
        }
    }

    fun snapshot(): ReaderAssetDiagnosticsSnapshot = synchronized(lock) {
        ReaderAssetDiagnosticsSnapshot(
            eventCounts = eventCounts.toMap(),
            physicallyReclaimedEvictionBytes = physicallyReclaimedEvictionBytes,
        )
    }
}

private fun ReaderAssetDiagnosticEvent.kind(): ReaderAssetDiagnosticKind = when (this) {
    ReaderAssetDiagnosticEvent.MemoryHit -> ReaderAssetDiagnosticKind.MEMORY_HIT
    ReaderAssetDiagnosticEvent.DiskHit -> ReaderAssetDiagnosticKind.DISK_HIT
    ReaderAssetDiagnosticEvent.NetworkFetch -> ReaderAssetDiagnosticKind.NETWORK_FETCH
    ReaderAssetDiagnosticEvent.SingleFlightJoin -> ReaderAssetDiagnosticKind.SINGLE_FLIGHT_JOIN
    is ReaderAssetDiagnosticEvent.PriorityPromotion -> ReaderAssetDiagnosticKind.PRIORITY_PROMOTION
    is ReaderAssetDiagnosticEvent.Prefetch -> ReaderAssetDiagnosticKind.PREFETCH
    ReaderAssetDiagnosticEvent.LocatorRefresh -> ReaderAssetDiagnosticKind.LOCATOR_REFRESH
    ReaderAssetDiagnosticEvent.Corruption -> ReaderAssetDiagnosticKind.CORRUPTION
    is ReaderAssetDiagnosticEvent.CommitFailure -> ReaderAssetDiagnosticKind.COMMIT_FAILURE
    is ReaderAssetDiagnosticEvent.CachePressure -> ReaderAssetDiagnosticKind.CACHE_PRESSURE
    is ReaderAssetDiagnosticEvent.EvictionBytes -> ReaderAssetDiagnosticKind.EVICTION_BYTES
}

private fun Long.saturatedAdd(other: Long): Long =
    if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other
