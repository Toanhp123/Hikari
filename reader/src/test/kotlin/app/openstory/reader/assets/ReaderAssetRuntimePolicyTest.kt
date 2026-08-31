package app.openstory.reader.assets

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderAssetRuntimePolicyTest {
    @Test
    fun readerRuntimePolicyKeepsFrozenV1Bounds() {
        assertEquals(16 * 1024 * 1024, ReaderAssetRuntimePolicy.MAX_READER_ASSET_BYTES)
        assertEquals(1, ReaderAssetRuntimePolicy.MAX_TRANSIENT_ASSET_RETRIES)
        assertEquals(250L, ReaderAssetRuntimePolicy.TRANSIENT_ASSET_RETRY_DELAY_MILLIS)
        assertEquals(3, ReaderAssetRuntimePolicy.MAX_TOTAL_CONTENT_FETCHES)
        assertEquals(1, ReaderAssetRuntimePolicy.RESERVED_CRITICAL_INTERACTIVE_SLOTS)
        assertEquals(1, ReaderAssetRuntimePolicy.MAX_NEXT_CHAPTER_SPECULATIVE_FETCHES)
        assertEquals(2, ReaderAssetRuntimePolicy.COIL_PREWARM_BEHIND)
        assertEquals(4, ReaderAssetRuntimePolicy.INTERACTIVE_CURRENT_AHEAD)
        assertEquals(2, ReaderAssetRuntimePolicy.METERED_NEAR_AHEAD_MAX)
        assertEquals(4, ReaderAssetRuntimePolicy.NEXT_CHAPTER_OPENING_BURST)
        assertEquals(8_000, ReaderAssetRuntimePolicy.APPROACHING_END_BASIS_POINTS)
        assertEquals(9_000, ReaderAssetRuntimePolicy.NEAR_END_BASIS_POINTS)
        assertEquals(1, ReaderAssetRuntimePolicy.APPROACHING_END_TRANSITION_FRONTIER)
        assertEquals(4, ReaderAssetRuntimePolicy.NEAR_END_TRANSITION_FRONTIER)
        assertEquals(2, ReaderAssetRuntimePolicy.RECENT_COMMITTED_HISTORY_DEPTH)
    }
}
