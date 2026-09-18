package app.universalmedia.ingestion.local

import app.universalmedia.core.domain.AssetRevision
import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalMediaMaterializer
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.LocalTreeObservationSource
import app.universalmedia.core.domain.MaterializedLocalVideo
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.ScanCancellation
import app.universalmedia.core.domain.ScanCoverage
import app.universalmedia.core.domain.ScanFinalization
import app.universalmedia.core.domain.ScanJournal
import app.universalmedia.core.domain.ScanOutcome
import app.universalmedia.core.domain.ScanRun
import app.universalmedia.core.domain.SeenLocalAsset
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.domain.TraversalResult
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.RootId
import app.universalmedia.core.model.ScanRunId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.core.model.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalRootScanRunnerTest {
    @Test
    fun onlyProviderMp4IsMaterializedAndEveryAttemptLoadsFreshScope() = runBlocking {
        val fixture = Fixture()
        fixture.batch =
            listOf(
                fixture.document("video/mp4"),
                fixture.document("video/MP4"),
                fixture.document(null),
            )
        assertEquals(LocalScanResult.COMPLETE, fixture.runner.run(fixture.root.id))
        fixture.root = fixture.root.copy(configGeneration = 2)
        assertEquals(LocalScanResult.COMPLETE, fixture.runner.run(fixture.root.id))
        assertEquals(2, fixture.committed.size)
        assertEquals(listOf(1L, 2L), fixture.runs.map { it.scope.configGeneration })
        assertNotEquals(fixture.runs[0].id, fixture.runs[1].id)
        assertTrue(fixture.finished.all { it.outcome == ScanOutcome.COMPLETE })
    }

    @Test
    fun incompleteAndFailedTraversalKeepPositivesWithoutCompleteCoverage() = runBlocking {
        val endings = listOf(
            TraversalResult.Incomplete(
                listOf(CoverageGap(null, IncompleteReason.PROVIDER_LOADING)),
            ),
            TraversalResult.Failed(LocalAccessFailure.ACCESS_LOST),
            TraversalResult.Failed(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE),
        )
        val expected =
            listOf(LocalScanResult.INCOMPLETE, LocalScanResult.FAILURE, LocalScanResult.RETRY)
        endings.forEachIndexed { index, ending ->
            val fixture = Fixture()
            fixture.ending = ending
            assertEquals(expected[index], fixture.runner.run(fixture.root.id))
            assertEquals(1, fixture.committed.size)
            assertNotEquals(ScanCoverage.Complete, fixture.finished.single().coverage)
        }
    }

    @Test
    fun cancellationAfterPositiveCommitCannotFinalizeComplete() = runBlocking {
        val fixture = Fixture()
        fixture.cancelAfterBatch = true
        try {
            fixture.runner.run(fixture.root.id, ScanCancellation { fixture.cancelled })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, fixture.committed.size)
            assertEquals(ScanOutcome.CANCELLED, fixture.finished.single().outcome)
        }
    }

    @Test
    fun revokedOrMissingRootNeverStartsTraversalOrRetries() = runBlocking {
        val fixture = Fixture()
        fixture.root = fixture.root.copy(access = LocalAccessState.ACCESS_LOST)
        assertEquals(LocalScanResult.FAILURE, fixture.runner.run(fixture.root.id))
        assertEquals(LocalScanResult.ROOT_NOT_FOUND, fixture.runner.run(RootId.generate()))
        assertTrue(fixture.runs.isEmpty())
        assertEquals(0, fixture.traversals)
    }

    @Test
    fun largeInputIsConsumedSequentiallyWithoutDroppingObservations() = runBlocking {
        val fixture = Fixture()
        fixture.batch = List(4097) { fixture.document("video/mp4") }
        assertEquals(LocalScanResult.COMPLETE, fixture.runner.run(fixture.root.id))
        assertEquals(4097, fixture.committed.size)
        assertEquals(1, fixture.maxInFlight)
    }

    @Test
    fun persistenceFailureSurfacesWithoutSchedulerRetry() = runBlocking {
        val fixture = Fixture()
        fixture.failCommit = true
        try {
            fixture.runner.run(fixture.root.id)
            fail("Persistence defects must surface")
        } catch (_: IllegalStateException) {
            assertEquals(ScanOutcome.FAILED, fixture.finished.single().outcome)
        }
    }

    @Test
    fun coroutineStopFinalizesCancelledAfterCommittedPositive() = runBlocking {
        val fixture = Fixture()
        fixture.pauseAfterBatch = true
        val execution = launch { fixture.runner.run(fixture.root.id) }
        fixture.batchCommitted.await()
        execution.cancelAndJoin()
        assertEquals(1, fixture.committed.size)
        assertEquals(ScanOutcome.CANCELLED, fixture.finished.single().outcome)
        assertEquals(ScanCoverage.Unknown, fixture.finished.single().coverage)
    }

    @Test
    fun supersededRunRejectionCannotReportSuccessfulScan() = runBlocking {
        val fixture = Fixture()
        fixture.rejectFinalization = true
        try {
            fixture.runner.run(fixture.root.id)
            fail("A stale finalizer must not report success")
        } catch (_: IllegalStateException) {
            assertTrue(fixture.finished.isEmpty())
            assertEquals(1, fixture.committed.size)
        }
    }

    private class Fixture :
        StorageRootStore,
        ScanJournal,
        LocalMediaMaterializer,
        LocalTreeObservationSource {
        var root =
            StorageRoot(
                RootId.generate(),
                LocalRootDescriptor("test", "content://test/tree/root"),
                1,
                LocalAccessState.READABLE,
            )
        val runs = mutableListOf<ScanRun>()
        val finished = mutableListOf<ScanFinalization>()
        val committed = mutableListOf<LocalDocumentObservation>()
        var batch = listOf(document("video/mp4"))
        var ending: TraversalResult = TraversalResult.Complete
        var traversals = 0
        var cancelAfterBatch = false
        var cancelled = false
        var failCommit = false
        var pauseAfterBatch = false
        var rejectFinalization = false
        val batchCommitted = CompletableDeferred<Unit>()
        var inFlight = 0
        var maxInFlight = 0
        val runner = LocalRootScanRunner(this, this, this, this) { 100L }

        fun document(mime: String?) = LocalDocumentObservation(
            LocalDocumentLocator(
                root.id,
                "test",
                "content://test/document/${java.util.UUID.randomUUID()}",
            ),
            "misleading.mp4",
            mime,
            1,
            1,
            1,
        )

        override suspend fun get(rootId: RootId): StorageRoot? = root.takeIf { it.id == rootId }
        override suspend fun registerOrReauthorize(
            evidence: RootRegistrationEvidence,
        ): StorageRoot = error("Not used")
        override suspend fun beginRun(scope: DeclaredScanScope, startedAtEpochMs: Long): ScanRun =
            ScanRun(ScanRunId.generate(), scope, startedAtEpochMs).also(runs::add)
        override suspend fun recordPositiveBatch(
            runId: ScanRunId,
            observations: List<SeenLocalAsset>,
        ): Unit = error("Materializer already records evidence atomically")
        override suspend fun finalizeRun(runId: ScanRunId, finalization: ScanFinalization) {
            check(!rejectFinalization) { "Scan is no longer active" }
            assertEquals(runs.last().id, runId)
            finished.add(finalization)
        }
        override suspend fun commitRecognizedLocalVideo(
            runId: ScanRunId,
            observation: LocalDocumentObservation,
        ): MaterializedLocalVideo {
            check(!failCommit)
            assertEquals(runs.last().id, runId)
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            kotlinx.coroutines.yield()
            committed.add(observation)
            inFlight--
            return MaterializedLocalVideo(
                MediaId.generate(),
                SourceId.generate(),
                SourceBindingId.generate(),
                AssetId.generate(),
                AssetRevision(1),
            )
        }
        override suspend fun observe(
            root: StorageRoot,
            scope: DeclaredScanScope,
            cancellation: ScanCancellation,
            onBatch: suspend (List<LocalDocumentObservation>) -> Unit,
        ): TraversalResult {
            traversals++
            assertEquals(root.configGeneration, scope.configGeneration)
            onBatch(batch)
            batchCommitted.complete(Unit)
            if (pauseAfterBatch) kotlinx.coroutines.awaitCancellation()
            cancelled = cancelAfterBatch
            return ending
        }
    }
}
