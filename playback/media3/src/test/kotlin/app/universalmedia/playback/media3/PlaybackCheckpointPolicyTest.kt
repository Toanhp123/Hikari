package app.universalmedia.playback.media3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCheckpointPolicyTest {
    @Test fun boundaryDoesNotPostponeNextPeriodicCheckpoint() {
        val policy = PlaybackCheckpointPolicy()
        assertTrue(policy.shouldCheckpoint(5000, 5000, false, false))
        assertTrue(policy.shouldCheckpoint(9000, 9000, false, true))
        assertTrue(policy.shouldCheckpoint(10_000, 10_000, false, false))
    }

    @Test fun tenThousandSamplesAreBoundedAndUnchangedPositionIsNotDirty() {
        val policy = PlaybackCheckpointPolicy()
        val writes = (0L until 10_000L).count { policy.shouldCheckpoint(it, it, false, false) }
        assertEquals(2, writes)
        assertFalse(policy.shouldCheckpoint(20_000, 5001, false, false))
    }

    @Test fun pauseAndBackwardSeekCheckpointImmediatelyAndEndedIsExplicit() {
        val policy = PlaybackCheckpointPolicy()
        assertTrue(policy.shouldCheckpoint(10, 900, false, true))
        assertTrue(policy.shouldCheckpoint(11, 100, false, true))
        assertTrue(policy.shouldCheckpoint(12, 100, true, true))
        assertFalse(policy.shouldCheckpoint(13, 100, true, true))
    }

    @Test fun openingAtZeroDoesNotCreateProgressButCompletionAtZeroDoes() {
        val policy = PlaybackCheckpointPolicy()
        assertFalse(policy.shouldCheckpoint(10, 0, false, true))
        assertTrue(policy.shouldCheckpoint(11, 0, true, true))
    }
}
