package app.universalmedia

import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.domain.CompletionState
import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalDocumentAccess
import app.universalmedia.core.domain.LocalDocumentAccessResult
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.LocalSourceLookup
import app.universalmedia.core.domain.LocalTreeObservationSource
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.ScanCancellation
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.TraversalResult
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoResumeAnchor
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.ConsumptionTargetRef.MediaTarget
import app.universalmedia.data.RoomMediaStore
import app.universalmedia.data.UniversalMediaDatabase
import app.universalmedia.ingestion.local.LocalRootScanRunner
import app.universalmedia.ingestion.local.LocalScanResult
import app.universalmedia.source.api.ResolutionFailure
import app.universalmedia.source.api.SourceResolution
import app.universalmedia.source.local.LocalSourceResolver
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real scan orchestration, Room and resolver; traversal endings are injected at the domain port. */
class ScanSafetyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "scan-safety-${UUID.randomUUID()}.db"
    private lateinit var database: UniversalMediaDatabase
    private lateinit var store: RoomMediaStore
    private lateinit var root: StorageRoot

    @Before
    fun setUp() = runBlocking {
        database = UniversalMediaDatabase.open(context, name)
        store = RoomMediaStore(database)
        root = store.registerOrReauthorize(
            RootRegistrationEvidence(LocalRootDescriptor("fixture", "tree"), true, 0),
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun rescanRefreshesObservationWithoutNewIdentityOrMembershipEpoch() = runBlocking {
        val item = seed()
        val before = store.progress.load(MediaTarget(item.mediaId))
        val traversal = Traversal(listOf(document("original").copy(observedAtEpochMs = 100)))
        assertEquals(LocalScanResult.COMPLETE, runner(traversal).run(root.id))
        val lookup = store.sources.load(MediaTarget(item.mediaId)) as LocalSourceLookup.Found
        assertEquals(item.bindingId, lookup.context.bindingId)
        assertEquals(item.assetId, lookup.context.assetId)
        assertEquals(item.assetRevision, lookup.context.assetRevision)
        listOf("media", "source", "source_binding", "asset", "library_entry").forEach {
            assertEquals(1L, scalar("SELECT COUNT(*) FROM $it"))
        }
        assertEquals(1L, scalar("SELECT added_at FROM library_entry"))
        assertEquals(100L, scalar("SELECT observed_at FROM asset"))
        assertEquals(before, store.progress.load(MediaTarget(item.mediaId)))
    }

    @Test
    fun loadingAndProviderFailureRetainUnseenRowsAndCommittedPositives() = runBlocking {
        val item = seed()
        val before = store.progress.load(MediaTarget(item.mediaId))
        val endings = listOf(
            TraversalResult.Incomplete(
                listOf(CoverageGap(null, IncompleteReason.PROVIDER_LOADING)),
            ),
            TraversalResult.Failed(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE),
        )
        endings.forEachIndexed { index, ending ->
            val traversal = Traversal(listOf(document("positive-$index")), ending)
            val expected = if (index == 0) LocalScanResult.INCOMPLETE else LocalScanResult.RETRY
            assertEquals(expected, runner(traversal).run(root.id))
            assertEquals(
                (index + 2).toLong(),
                scalar("SELECT COUNT(*) FROM asset WHERE presence = 'PRESENT'"),
            )
            assertEquals(
                (index + 2).toLong(),
                scalar("SELECT COUNT(*) FROM library_entry WHERE membership = 'ACTIVE'"),
            )
            assertEquals(before, store.progress.load(MediaTarget(item.mediaId)))
        }
        assertEquals(0L, scalar("SELECT COUNT(*) FROM scan_scope WHERE coverage = 'COMPLETE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM scan_run WHERE outcome = 'PARTIAL'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM scan_run WHERE outcome = 'FAILED'"))
    }

    @Test
    fun cancellationAndAbandonedRunRecoveryPreserveCommittedState() = runBlocking {
        val item = seed()
        val before = store.progress.load(MediaTarget(item.mediaId))
        val traversal = Traversal(listOf(document("second")), pause = true)
        val execution = launch { runner(traversal).run(root.id) }
        try {
            withTimeout(5000) { traversal.committed.await() }
        } finally {
            execution.cancelAndJoin()
        }
        assertEquals(1L, scalar("SELECT COUNT(*) FROM scan_run WHERE outcome = 'CANCELLED'"))
        val abandoned = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 20)
        store.commitRecognizedLocalVideo(abandoned.id, document("third"))
        // Reopen models lost in-memory ownership, not actual OS process death (Task 11).
        database.close()
        database = UniversalMediaDatabase.open(context, name)
        store = RoomMediaStore(database)
        val replay = Traversal(listOf(document("original"), document("second"), document("third")))
        assertEquals(LocalScanResult.COMPLETE, runner(replay).run(root.id))
        assertEquals(3L, scalar("SELECT COUNT(*) FROM asset WHERE presence = 'PRESENT'"))
        assertEquals(3L, scalar("SELECT COUNT(*) FROM library_entry WHERE membership = 'ACTIVE'"))
        assertEquals(4L, scalar("SELECT COUNT(DISTINCT run_id) FROM scan_run"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM scan_run WHERE outcome = 'INTERRUPTED'"))
        assertEquals(before, store.progress.load(MediaTarget(item.mediaId)))
    }

    @Test
    fun revokedAccessFailsScanAndResolutionWithoutChangingUserState() = runBlocking {
        val item = seed()
        val target = MediaTarget(item.mediaId)
        val before = store.progress.load(target)
        val traversal =
            Traversal(emptyList(), TraversalResult.Failed(LocalAccessFailure.ACCESS_LOST))
        assertEquals(LocalScanResult.FAILURE, runner(traversal).run(root.id))
        val resolver = LocalSourceResolver(
            store.sources,
            LocalDocumentAccess { _, _ ->
                LocalDocumentAccessResult.Failed(LocalAccessFailure.ACCESS_LOST)
            },
        )
        assertEquals(
            SourceResolution.Failed(ResolutionFailure.ACCESS_LOST),
            resolver.resolve(target),
        )
        store.registerOrReauthorize(RootRegistrationEvidence(root.descriptor, false, 30))
        assertEquals(LocalScanResult.FAILURE, runner(traversal).run(root.id))
        assertEquals(1, traversal.calls)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM library_entry WHERE membership = 'ACTIVE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM asset WHERE presence = 'PRESENT'"))
        assertEquals(before, store.progress.load(target))
    }

    @Test
    fun changedLocatorWithSameNameAndMetadataDoesNotInferIdentityContinuity() = runBlocking {
        val original = seed()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 10)
        // Q-ID-001/Q-REC-001 own future rename/move matching; this slice uses exact locator evidence.
        val moved = store.commitRecognizedLocalVideo(run.id, document("moved"))
        assertNotEquals(original.mediaId, moved.mediaId)
        assertNotEquals(original.assetId, moved.assetId)
        assertNotEquals(original.bindingId, moved.bindingId)
        assertEquals(original.sourceId, moved.sourceId)
        assertEquals(2L, scalar("SELECT COUNT(*) FROM library_entry WHERE membership = 'ACTIVE'"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM asset WHERE presence = 'PRESENT'"))
        assertEquals(4000L, store.progress.load(MediaTarget(original.mediaId))?.anchor?.positionMs)
        assertEquals(null, store.progress.load(MediaTarget(moved.mediaId)))
    }

    private suspend fun seed(): app.universalmedia.core.domain.MaterializedLocalVideo {
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val item = store.commitRecognizedLocalVideo(run.id, document("original"))
        store.progress.checkpointVideo(
            VideoProgressCheckpoint(
                MediaTarget(item.mediaId),
                VideoResumeAnchor(4000, 10000),
                CompletionState.IN_PROGRESS,
                VideoResumeContext(item.bindingId, item.assetId, item.assetRevision),
                2,
                0,
            ),
        )
        assertEquals(4000L, store.progress.load(MediaTarget(item.mediaId))?.anchor?.positionMs)
        return item
    }

    private fun document(locator: String) = LocalDocumentObservation(
        LocalDocumentLocator(root.id, "fixture", locator),
        "same.mp4",
        "video/mp4",
        100,
        10,
        1,
    )

    private fun runner(traversal: Traversal) = LocalRootScanRunner(store, traversal, store, store) {
        100L
    }

    private fun scalar(sql: String): Long = database.openHelper.readableDatabase.query(sql).use {
        assertTrue(it.moveToFirst())
        it.getLong(0)
    }

    private class Traversal(
        private val batch: List<LocalDocumentObservation>,
        private val ending: TraversalResult = TraversalResult.Complete,
        private val pause: Boolean = false,
    ) : LocalTreeObservationSource {
        val committed = CompletableDeferred<Unit>()
        var calls = 0

        override suspend fun observe(
            root: StorageRoot,
            scope: DeclaredScanScope,
            cancellation: ScanCancellation,
            onBatch: suspend (List<LocalDocumentObservation>) -> Unit,
        ): TraversalResult {
            calls++
            onBatch(batch)
            committed.complete(Unit)
            if (pause) awaitCancellation()
            return ending
        }
    }
}
