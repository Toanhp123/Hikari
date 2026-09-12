package app.openstory.catalog.feature.fixture

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkCatalogDiagnosticsTest {
    @Test
    fun resetAndLifecycleCallbacksExposeTerminalOwnershipCounts() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkLifecycleCounters.recordActivationStarted()
        BenchmarkLifecycleCounters.recordStorageReady()
        BenchmarkLifecycleCounters.recordAcquisitionStarted()
        BenchmarkLifecycleCounters.recordImageSessionInitialized()
        BenchmarkWorkCounters.recordCoverDemandStarted()
        BenchmarkWorkCounters.recordCoverJobCount(1)
        BenchmarkWorkCounters.recordRuntimeWorkCount(1)
        BenchmarkWorkCounters.recordStoryPinCount(1)
        BenchmarkLifecycleCounters.recordStoryCollectorStarted()
        BenchmarkWorkCounters.recordCoverDemandStopped()
        BenchmarkWorkCounters.recordCoverJobCount(0)
        BenchmarkWorkCounters.recordRuntimeWorkCount(0)
        BenchmarkWorkCounters.recordStoryPinCount(0)
        BenchmarkLifecycleCounters.recordStoryCollectorStopped()
        BenchmarkLifecycleCounters.recordImageSessionClosed()
        BenchmarkLifecycleCounters.recordRuntimeSessionClosed()

        assertEquals(1, BenchmarkCatalogDiagnostics.snapshot().activationStarts)
        assertEquals(1, BenchmarkCatalogDiagnostics.snapshot().storageReadyEvents)
        assertEquals(1, BenchmarkCatalogDiagnostics.snapshot().acquisitionStarts)
        assertEquals(0, BenchmarkCatalogDiagnostics.snapshot().activeCoverDemands)
        assertEquals(0, BenchmarkCatalogDiagnostics.snapshot().activeCoverJobs)
        assertEquals(1, BenchmarkCatalogDiagnostics.snapshot().peakActiveCoverJobs)
        assertEquals(0, BenchmarkCatalogDiagnostics.snapshot().activeRuntimeWork)
        assertEquals(0, BenchmarkCatalogDiagnostics.snapshot().activeStoryPins)
        assertEquals(0, BenchmarkCatalogDiagnostics.snapshot().activeStoryCollectors)
        assertEquals(0, BenchmarkCatalogDiagnostics.snapshot().activeImageSessions)
        assertEquals(1, BenchmarkCatalogDiagnostics.snapshot().runtimeSessionCloses)

        BenchmarkCatalogDiagnostics.reset()
        assertEquals(BenchmarkCatalogDiagnosticSnapshot(), BenchmarkCatalogDiagnostics.snapshot())
    }

    @Test
    fun sqlDiagnosticsCountOnlyDiscoverAndStoryObservationStatements() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkQueryCounters.recordSqlQuery(
            "SELECT state.source_key FROM catalog_source_state AS state " +
                "LEFT JOIN discover_card AS card ON card.source_key = state.source_key",
        )
        BenchmarkQueryCounters.recordSqlQuery(
            "SELECT identity.story_id FROM story_source_identity AS identity " +
                "LEFT JOIN story_detail AS detail ON detail.story_id = identity.story_id",
        )
        BenchmarkQueryCounters.recordSqlQuery("SELECT * FROM story_author WHERE story_id = ? ORDER BY position")
        BenchmarkQueryCounters.recordSqlQuery("SELECT * FROM story_artist WHERE story_id = ? ORDER BY position")
        BenchmarkQueryCounters.recordSqlQuery("SELECT * FROM story_genre WHERE story_id = ? ORDER BY position")
        BenchmarkQueryCounters.recordSqlQuery("PRAGMA foreign_keys")

        assertEquals(1, BenchmarkCatalogDiagnostics.snapshot().discoverObservationQueries)
        assertEquals(4, BenchmarkCatalogDiagnostics.snapshot().storyObservationQueries)
    }

    @Test
    fun imageOwnershipSamplesTrackCurrentAndPeakBytes() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkImageCounters.recordImageOwnership(10, 20)
        BenchmarkImageCounters.recordImageOwnership(7, 25)

        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        assertEquals(7L, snapshot.decodedMemoryBytes)
        assertEquals(10L, snapshot.peakDecodedMemoryBytes)
        assertEquals(25L, snapshot.encodedDiskBytes)
        assertEquals(25L, snapshot.peakEncodedDiskBytes)
    }

    @Test
    fun successfulDecodeSamplesExposeBoundedTargetAndThreadEvidence() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkImageCounters.recordSuccessfulDecode(
            targetWidth = 360,
            targetHeight = 540,
            originalSize = false,
            mainThread = false,
        )
        BenchmarkImageCounters.recordSuccessfulDecode(
            targetWidth = 720,
            targetHeight = 1080,
            originalSize = false,
            mainThread = false,
        )

        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        assertEquals(2, snapshot.successfulDecodes)
        assertEquals(720, snapshot.maxDecodeTargetWidth)
        assertEquals(1080, snapshot.maxDecodeTargetHeight)
        assertEquals(0, snapshot.originalSizeDecodes)
        assertEquals(0, snapshot.mainThreadDecodes)
    }

    @Test
    fun mutationSamplesTrackMaximumBoundedWork() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkWorkCounters.recordDiscoverMutationTouched(41)
        BenchmarkWorkCounters.recordDiscoverMutationTouched(19)
        BenchmarkWorkCounters.recordStoryReleaseMutationTouched(2)
        BenchmarkWorkCounters.recordStoryReleaseMutationTouched(1)

        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        assertEquals(41, snapshot.maxDiscoverTouchedStoryIds)
        assertEquals(2, snapshot.maxReleaseTouchedStoryIds)
    }
}
