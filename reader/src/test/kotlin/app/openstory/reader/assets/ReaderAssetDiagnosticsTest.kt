package app.openstory.reader.assets

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderAssetDiagnosticsTest {
    @Test
    fun `diagnostic sink failure is fail open`() {
        val diagnostics = ReaderAssetDiagnosticsSink { error("diagnostics unavailable") }

        diagnostics.recordSafely(ReaderAssetDiagnosticEvent.DiskHit)
    }

    @Test
    fun `aggregate sink counts every frozen diagnostic category and physical eviction bytes`() {
        val diagnostics = ReaderAssetAggregateDiagnostics()

        diagnostics.record(ReaderAssetDiagnosticEvent.MemoryHit)
        diagnostics.record(ReaderAssetDiagnosticEvent.DiskHit)
        diagnostics.record(ReaderAssetDiagnosticEvent.NetworkFetch)
        diagnostics.record(ReaderAssetDiagnosticEvent.SingleFlightJoin)
        diagnostics.record(
            ReaderAssetDiagnosticEvent.PriorityPromotion(
                from = ContentFetchPriority.SPECULATIVE,
                to = ContentFetchPriority.CRITICAL,
            ),
        )
        diagnostics.record(ReaderAssetDiagnosticEvent.Prefetch(count = 3))
        diagnostics.record(ReaderAssetDiagnosticEvent.LocatorRefresh)
        diagnostics.record(ReaderAssetDiagnosticEvent.Corruption)
        diagnostics.record(ReaderAssetDiagnosticEvent.CommitFailure(ReaderAssetFailure.CacheStorageUnavailable))
        diagnostics.record(ReaderAssetDiagnosticEvent.CachePressure(ReaderAssetCachePressure.EMERGENCY))
        diagnostics.record(ReaderAssetDiagnosticEvent.EvictionBytes(128))
        diagnostics.record(ReaderAssetDiagnosticEvent.EvictionBytes(0))

        val snapshot = diagnostics.snapshot()
        ReaderAssetDiagnosticKind.entries.forEach { kind ->
            val expected = when (kind) {
                ReaderAssetDiagnosticKind.PREFETCH -> 3L
                ReaderAssetDiagnosticKind.EVICTION_BYTES -> 2L
                else -> 1L
            }
            assertEquals(expected, snapshot.eventCounts.getValue(kind), kind.name)
        }
        assertEquals(128L, snapshot.physicallyReclaimedEvictionBytes)
    }
}

internal class RecordingReaderAssetDiagnostics : ReaderAssetDiagnosticsSink {
    val events = mutableListOf<ReaderAssetDiagnosticEvent>()
    override fun record(event: ReaderAssetDiagnosticEvent) {
        events += event
    }
}
