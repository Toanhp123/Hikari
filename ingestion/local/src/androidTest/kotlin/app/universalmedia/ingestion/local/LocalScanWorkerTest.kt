package app.universalmedia.ingestion.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalAccessState
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
import app.universalmedia.core.domain.ScanRun
import app.universalmedia.core.domain.SeenLocalAsset
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.domain.TraversalResult
import app.universalmedia.core.model.RootId
import app.universalmedia.core.model.ScanRunId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScanWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun inputHasOnlyRootIdAndKeepCoalescesActiveWork() {
        val fixture = Fixture()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(
                LocalScanWorkerFactory(fixture.runner),
            ).build(),
        )
        val manager = WorkManager.getInstance(context)
        try {
            val scheduler = LocalScanScheduler(manager)
            val constraints = Constraints.Builder().setRequiredNetworkType(
                NetworkType.CONNECTED,
            ).build()
            val request = LocalScanScheduler.request(
                fixture.root.id,
            ).setConstraints(constraints).build()
            assertEquals(setOf("root_id"), request.workSpec.input.keyValueMap.keys)
            assertEquals(
                fixture.root.id.value.toString(),
                request.workSpec.input.getString("root_id"),
            )
            // Hold the first request enqueued to exercise the production KEEP policy.
            manager.enqueueUniqueWork(
                "scan-root:${fixture.root.id.value}",
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
                .result.get(10, TimeUnit.SECONDS)
            scheduler.enqueue(fixture.root.id).result.get(10, TimeUnit.SECONDS)
            val work = manager.getWorkInfosForUniqueWork(
                "scan-root:${fixture.root.id.value}",
            ).get(10, TimeUnit.SECONDS)
            assertEquals(1, work.size)
            assertEquals(request.id, work.single().id)
            assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        } finally {
            manager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
            WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }

    @Test
    fun recreatedWorkerUsesFreshDomainRunAndReloadsRoot() = runBlocking {
        val fixture = Fixture()
        val request = LocalScanScheduler.request(fixture.root.id).build()
        val first = fixture.worker(request.workSpec.input, request.id)
        assertEquals(ListenableWorker.Result.success(), first.doWork())
        fixture.root = fixture.root.copy(configGeneration = 2)
        val second = fixture.worker(request.workSpec.input, request.id)
        assertEquals(ListenableWorker.Result.success(), second.doWork())
        assertEquals(listOf(1L, 2L), fixture.runs.map { it.scope.configGeneration })
        assertNotEquals(fixture.runs[0].id, fixture.runs[1].id)
        assertTrue(fixture.runs.none { it.id.value == request.id })
    }

    @Test
    fun onlyExplicitTransientFailureRetriesAndInvalidInputFails() = runBlocking {
        val fixture = Fixture()
        val input = LocalScanScheduler.request(fixture.root.id).build().workSpec.input
        fixture.ending = TraversalResult.Failed(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
        assertEquals(ListenableWorker.Result.retry(), fixture.worker(input).doWork())
        fixture.ending = TraversalResult.Failed(LocalAccessFailure.ACCESS_LOST)
        assertEquals(ListenableWorker.Result.failure(), fixture.worker(input).doWork())
        assertEquals(ListenableWorker.Result.failure(), fixture.worker(Data.EMPTY).doWork())
        val malformed = Data.Builder().putString("root_id", "content://private/tree").build()
        assertEquals(ListenableWorker.Result.failure(), fixture.worker(malformed).doWork())
        assertEquals(2, fixture.runs.size)
        assertTrue(fixture.finished.all { it.coverage != ScanCoverage.Complete })
    }

    @Test
    fun schedulerSuccessDoesNotPromotePartialDomainCoverage() = runBlocking {
        val fixture = Fixture()
        fixture.ending =
            TraversalResult.Incomplete(listOf(CoverageGap(null, IncompleteReason.PROVIDER_LOADING)))
        val input = LocalScanScheduler.request(fixture.root.id).build().workSpec.input
        assertEquals(ListenableWorker.Result.success(), fixture.worker(input).doWork())
        assertEquals(
            app.universalmedia.core.domain.ScanOutcome.PARTIAL,
            fixture.finished.single().outcome,
        )
        assertTrue(fixture.finished.single().coverage is ScanCoverage.Incomplete)
    }

    private inner class Fixture :
        StorageRootStore,
        ScanJournal,
        LocalTreeObservationSource,
        LocalMediaMaterializer {
        var root =
            StorageRoot(
                RootId.generate(),
                LocalRootDescriptor("fixture", "content://fixture/tree"),
                1,
                LocalAccessState.READABLE,
            )
        val runs = mutableListOf<ScanRun>()
        val finished = mutableListOf<ScanFinalization>()
        var ending: TraversalResult = TraversalResult.Complete
        val runner = LocalRootScanRunner(this, this, this, this)
        fun worker(input: Data, id: java.util.UUID = java.util.UUID.randomUUID()): LocalScanWorker =
            TestListenableWorkerBuilder<LocalScanWorker>(context)
                .setId(
                    id,
                ).setInputData(input).setWorkerFactory(LocalScanWorkerFactory(runner)).build()
        override suspend fun get(rootId: RootId): StorageRoot? = root.takeIf { it.id == rootId }
        override suspend fun registerOrReauthorize(
            evidence: RootRegistrationEvidence,
        ): StorageRoot = error("Not used")
        override suspend fun beginRun(scope: DeclaredScanScope, startedAtEpochMs: Long): ScanRun =
            ScanRun(ScanRunId.generate(), scope, startedAtEpochMs).also(runs::add)
        override suspend fun recordPositiveBatch(
            runId: ScanRunId,
            observations: List<SeenLocalAsset>,
        ): Unit = error("Not used")
        override suspend fun finalizeRun(runId: ScanRunId, finalization: ScanFinalization) {
            finished.add(finalization)
        }
        override suspend fun commitRecognizedLocalVideo(
            runId: ScanRunId,
            observation: LocalDocumentObservation,
        ): MaterializedLocalVideo = error("Empty fixture")
        override suspend fun observe(
            root: StorageRoot,
            scope: DeclaredScanScope,
            cancellation: ScanCancellation,
            onBatch: suspend (List<LocalDocumentObservation>) -> Unit,
        ): TraversalResult = ending
    }
}
