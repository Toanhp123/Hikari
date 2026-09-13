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
        assertEquals(EXPECTED_STORY_OBSERVATION_QUERIES, BenchmarkCatalogDiagnostics.snapshot().storyObservationQueries)
    }

    @Test
    fun imageOwnershipSamplesTrackCurrentAndPeakBytes() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkImageCounters.recordImageOwnership(PEAK_DECODED_BYTES, INITIAL_ENCODED_BYTES)
        BenchmarkImageCounters.recordImageOwnership(CURRENT_DECODED_BYTES, FINAL_ENCODED_BYTES)

        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        assertEquals(CURRENT_DECODED_BYTES, snapshot.decodedMemoryBytes)
        assertEquals(PEAK_DECODED_BYTES, snapshot.peakDecodedMemoryBytes)
        assertEquals(FINAL_ENCODED_BYTES, snapshot.encodedDiskBytes)
        assertEquals(FINAL_ENCODED_BYTES, snapshot.peakEncodedDiskBytes)
    }

    @Test
    fun successfulDecodeSamplesExposeBoundedTargetAndThreadEvidence() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkImageCounters.recordSuccessfulDecode(
            targetWidth = FIRST_TARGET_WIDTH,
            targetHeight = FIRST_TARGET_HEIGHT,
            originalSize = false,
            mainThread = false,
        )
        BenchmarkImageCounters.recordSuccessfulDecode(
            targetWidth = MAX_TARGET_WIDTH,
            targetHeight = MAX_TARGET_HEIGHT,
            originalSize = false,
            mainThread = false,
        )

        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        assertEquals(2, snapshot.successfulDecodes)
        assertEquals(MAX_TARGET_WIDTH, snapshot.maxDecodeTargetWidth)
        assertEquals(MAX_TARGET_HEIGHT, snapshot.maxDecodeTargetHeight)
        assertEquals(0, snapshot.originalSizeDecodes)
        assertEquals(0, snapshot.mainThreadDecodes)
    }

    @Test
    fun mutationSamplesTrackMaximumBoundedWork() {
        BenchmarkCatalogDiagnostics.reset()

        BenchmarkWorkCounters.recordDiscoverMutationTouched(MAX_DISCOVER_MUTATION_TOUCHED)
        BenchmarkWorkCounters.recordDiscoverMutationTouched(SMALLER_DISCOVER_MUTATION_TOUCHED)
        BenchmarkWorkCounters.recordStoryReleaseMutationTouched(2)
        BenchmarkWorkCounters.recordStoryReleaseMutationTouched(1)

        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        assertEquals(MAX_DISCOVER_MUTATION_TOUCHED, snapshot.maxDiscoverTouchedStoryIds)
        assertEquals(2, snapshot.maxReleaseTouchedStoryIds)
    }

    private companion object {
        const val EXPECTED_STORY_OBSERVATION_QUERIES = 4
        const val PEAK_DECODED_BYTES = 10L
        const val INITIAL_ENCODED_BYTES = 20L
        const val CURRENT_DECODED_BYTES = 7L
        const val FINAL_ENCODED_BYTES = 25L
        const val FIRST_TARGET_WIDTH = 360
        const val FIRST_TARGET_HEIGHT = 540
        const val MAX_TARGET_WIDTH = 720
        const val MAX_TARGET_HEIGHT = 1_080
        const val MAX_DISCOVER_MUTATION_TOUCHED = 41
        const val SMALLER_DISCOVER_MUTATION_TOUCHED = 19
    }
}
