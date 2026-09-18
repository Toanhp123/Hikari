package app.universalmedia.core.domain

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirstSliceContractTest {
    private val target = ConsumptionTargetRef.MediaTarget(MediaId.generate())
    private val context = VideoResumeContext(SourceBindingId.generate(), AssetId.generate(), AssetRevision(1))

    @Test
    fun newerCheckpointCanMoveBackwardAndRetainCompletion() {
        val old = checkpoint(42_000, CompletionState.COMPLETED)
        val rewind = old.copy(anchor = VideoResumeAnchor(10_000), expectedStateRevision = 8)
        assertEquals(10_000, rewind.anchor.positionMs)
        assertEquals(CompletionState.COMPLETED, rewind.completion)
        assertEquals(8, rewind.expectedStateRevision)
    }

    @Test
    fun reachingDurationDoesNotInferCompletion() {
        val checkpoint = checkpoint(100, CompletionState.IN_PROGRESS).copy(anchor = VideoResumeAnchor(100, 100))
        assertEquals(CompletionState.IN_PROGRESS, checkpoint.completion)
        val manual = VideoProgressState(target, null, CompletionState.COMPLETED, null, 1, 1000)
        assertEquals(null, manual.anchor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePositionIsRejected() {
        VideoResumeAnchor(-1)
    }

    @Test
    fun providerLoadingIsIncompleteRatherThanFailureOrComplete() {
        val result = TraversalResult.Incomplete(listOf(CoverageGap(null, IncompleteReason.PROVIDER_LOADING)))
        assertFalse((result as TraversalResult) is TraversalResult.Failed)
        assertEquals(IncompleteReason.PROVIDER_LOADING, result.gaps.single().reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteTraversalRequiresAnExplanation() {
        TraversalResult.Incomplete(emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun completeScanCannotClaimUnknownCoverage() {
        ScanFinalization(ScanOutcome.COMPLETE, ScanCoverage.Unknown, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failedScanCannotClaimCompleteCoverage() {
        ScanFinalization(ScanOutcome.FAILED, ScanCoverage.Complete, 1000)
    }

    private fun checkpoint(positionMs: Long, completion: CompletionState) = VideoProgressCheckpoint(
        target = target,
        anchor = VideoResumeAnchor(positionMs),
        completion = completion,
        context = context,
        observedAtEpochMs = 1000,
        expectedStateRevision = 7,
    )
}
