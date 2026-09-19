package app.universalmedia.data

import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.domain.CompletionState
import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LibraryCard
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.LocalSourceLookup
import app.universalmedia.core.domain.ProgressWriteResult
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.ScanCoverage
import app.universalmedia.core.domain.ScanFinalization
import app.universalmedia.core.domain.ScanOutcome
import app.universalmedia.core.domain.SeenLocalAsset
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoResumeAnchor
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.ConsumptionTargetRef.MediaTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RoomMediaStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "room-media-store-test.db"
    private lateinit var database: UniversalMediaDatabase
    private lateinit var store: RoomMediaStore

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = UniversalMediaDatabase.open(context, name)
        store = RoomMediaStore(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun replayAndConcurrentObservationReuseCanonicalIds() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val observation = observation(root)
        val secondDatabase = UniversalMediaDatabase.open(context, name)
        val secondStore = RoomMediaStore(secondDatabase)
        val results = try {
            coroutineScope {
                List(8) { index ->
                    async {
                        (
                            if (index % 2 ==
                                0
                            ) {
                                store
                            } else {
                                secondStore
                            }
                            ).commitRecognizedLocalVideo(run.id, observation)
                    }
                }.map { it.await() }
            }
        } finally {
            secondDatabase.close()
        }
        assertEquals(1, results.distinct().size)
        assertEquals(results.first(), store.commitRecognizedLocalVideo(run.id, observation))
        assertEquals(1, database.catalog().cards().first().size)
        val other = store.commitRecognizedLocalVideo(
            run.id,
            observation.copy(locator = observation.locator.copy(documentLocator = "other")),
        )
        assertNotEquals(results.first().mediaId, other.mediaId)
        assertEquals(results.first().sourceId, other.sourceId)
    }

    @Test
    fun reopenRestoresIdentityLibraryAndBackwardSeekAndRejectsStaleCheckpoint() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val item = store.commitRecognizedLocalVideo(run.id, observation(root))
        val target = MediaTarget(item.mediaId)
        val checkpoint =
            VideoProgressCheckpoint(
                target,
                VideoResumeAnchor(9000, 10000),
                CompletionState.IN_PROGRESS,
                VideoResumeContext(item.bindingId, item.assetId, item.assetRevision),
                2,
                0,
            )
        assertTrue(store.progress.checkpointVideo(checkpoint) is ProgressWriteResult.Applied)
        assertEquals(ProgressWriteResult.Stale, store.progress.checkpointVideo(checkpoint))
        assertTrue(
            store.progress.checkpointVideo(
                checkpoint.copy(anchor = VideoResumeAnchor(1000), expectedStateRevision = 1),
            ) is ProgressWriteResult.Applied,
        )
        database.close()
        database = UniversalMediaDatabase.open(context, name)
        store = RoomMediaStore(database)
        assertEquals(1000L, store.progress.load(target)?.anchor?.positionMs)
        assertEquals(2L, store.progress.load(target)?.stateRevision)
        assertEquals(
            app.universalmedia.core.domain.VideoResumeDecision.Exact(VideoResumeAnchor(1000)),
            app.universalmedia.core.domain.selectVideoResume(
                target,
                store.progress.load(target),
                checkpoint.context,
            ),
        )
        assertEquals(
            app.universalmedia.core.domain.VideoResumeDecision.StartFromBeginning,
            app.universalmedia.core.domain.selectVideoResume(
                target,
                store.progress.load(target),
                checkpoint.context.copy(
                    assetRevision = app.universalmedia.core.domain.AssetRevision(999),
                ),
            ),
        )
        assertEquals(
            item.mediaId.value.toString(),
            database.catalog().cards().first().single().mediaId,
        )
        assertEquals(
            item.assetId,
            (store.sources.load(target) as LocalSourceLookup.Found).context.assetId,
        )
    }

    @Test
    fun supersededFinalizedAndReauthorizedRunsCannotWrite() = runBlocking {
        val root = root()
        val scope = DeclaredScanScope(root.id, root.configGeneration)
        val old = store.beginRun(scope, 1)
        val current = store.beginRun(scope, 2)
        rejected { store.commitRecognizedLocalVideo(old.id, observation(root)) }
        store.finalizeRun(
            current.id,
            ScanFinalization(
                ScanOutcome.PARTIAL,
                ScanCoverage.Incomplete(
                    listOf(
                        CoverageGap(observation(root).locator, IncompleteReason.PROVIDER_LOADING),
                    ),
                ),
                3,
            ),
        )
        rejected { store.commitRecognizedLocalVideo(current.id, observation(root)) }
        val next = store.beginRun(scope, 4)
        root()
        rejected { store.commitRecognizedLocalVideo(next.id, observation(root)) }
        assertTrue(database.catalog().cards().first().isEmpty())
    }

    @Test
    fun suppressionAndAccessLossPreserveUserState() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val item = store.commitRecognizedLocalVideo(run.id, observation(root))
        val target = MediaTarget(item.mediaId)
        store.progress.markVideoCompleted(target, 0, 2)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE library_entry SET membership = 'SUPPRESSED'",
        )
        store.commitRecognizedLocalVideo(run.id, observation(root))
        assertTrue(database.catalog().cards().first().isEmpty())
        store.registerOrReauthorize(RootRegistrationEvidence(root.descriptor, false, 3))
        assertEquals(CompletionState.COMPLETED, store.progress.load(target)?.completion)
        assertEquals(LocalAccessState.ACCESS_LOST, store.get(root.id)?.access)
        assertEquals(1L, count("library_entry"))
    }

    @Test
    fun targetBridgeRejectsMissingOrMultipleReferentsAndSchemaReopens() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        store.commitRecognizedLocalVideo(run.id, observation(root))
        rejected {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO consumption_target (target_kind) VALUES ('MEDIA')",
            )
        }
        rejected {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE consumption_target SET target_kind = 'UNIT'",
            )
        }
        rejected {
            database.openHelper.writableDatabase.execSQL(
                """
        INSERT INTO consumption_target (target_kind, media_id) VALUES ('MEDIA',
        'missing')
    """,
            )
        }
        // Removing Room's identity cache forces its generated schema validator on reopen.
        database.openHelper.writableDatabase.execSQL("DROP TABLE room_master_table")
        database.close()
        database = UniversalMediaDatabase.open(context, name)
        assertEquals(1L, count("media"))
    }

    @Test
    fun wrongScopeAndBatchFailureRollBackAllPositiveEvidence() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val observation = observation(root)
        rejected {
            store.commitRecognizedLocalVideo(run.id, observation.copy(mimeType = "video/webm"))
        }
        rejected {
            store.commitRecognizedLocalVideo(
                run.id,
                observation.copy(locator = observation.locator.copy(providerAuthority = "other")),
            )
        }
        assertEquals(0L, count("media"))
        val item = store.commitRecognizedLocalVideo(run.id, observation)
        val next = store.beginRun(run.scope, 2)
        rejected {
            store.recordPositiveBatch(
                next.id,
                listOf(
                    SeenLocalAsset(item.assetId, observation),
                    SeenLocalAsset(
                        item.assetId,
                        observation.copy(
                            locator = observation.locator.copy(documentLocator = "wrong"),
                        ),
                    ),
                ),
            )
        }
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM scan_seen_asset WHERE run_id = ?",
            arrayOf(next.id.value.toString()),
        ).use {
            it.moveToFirst()
            assertEquals(0L, it.getLong(0))
        }
        store.recordPositiveBatch(next.id, listOf(SeenLocalAsset(item.assetId, observation)))
        assertEquals(2L, count("scan_seen_asset"))
    }

    @Test
    fun partialFinalizationAndAssetLossDoNotDeleteLibraryOrProgress() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val item = store.commitRecognizedLocalVideo(run.id, observation(root))
        val target = MediaTarget(item.mediaId)
        store.progress.markVideoCompleted(target, 0, 2)
        store.finalizeRun(
            run.id,
            ScanFinalization(
                ScanOutcome.PARTIAL,
                ScanCoverage.Incomplete(
                    listOf(
                        CoverageGap(observation(root).locator, IncompleteReason.PROVIDER_LOADING),
                    ),
                ),
                3,
            ),
        )
        database.openHelper.readableDatabase.query("SELECT coverage, gaps FROM scan_scope").use {
            it.moveToFirst()
            assertEquals("INCOMPLETE", it.getString(0))
            val gap = org.json.JSONArray(it.getString(1)).getJSONObject(0)
            assertEquals("PROVIDER_LOADING", gap.getString("reason"))
            assertEquals("document", gap.getString("documentLocator"))
        }
        database.openHelper.writableDatabase.execSQL("UPDATE asset SET presence = 'MISSING'")
        assertEquals(
            item.mediaId.value.toString(),
            database.catalog().cards().first().single().mediaId,
        )
        assertEquals(CompletionState.COMPLETED, store.progress.load(target)?.completion)
    }

    @Test
    fun libraryDomainObservationEmitsChangesAndCancels() = runBlocking {
        val initial = CompletableDeferred<Unit>()
        val added = CompletableDeferred<LibraryCard>()
        val observer = launch {
            store.observeLibraryCards {
                if (it.isEmpty()) initial.complete(Unit) else added.complete(it.single())
            }
        }
        withTimeout(5000) { initial.await() }
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val item = store.commitRecognizedLocalVideo(run.id, observation(root))
        val card = withTimeout(5000) { added.await() }
        assertEquals(item.mediaId, card.mediaId)
        assertEquals("same.mp4", card.fallbackDisplayName)
        observer.cancel()
        observer.join()
    }

    @Test
    fun checkpointCannotUseAnotherTargetsRepresentationAndCompletionRetainsAnchor() = runBlocking {
        val root = root()
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 1)
        val first = store.commitRecognizedLocalVideo(run.id, observation(root))
        val second = store.commitRecognizedLocalVideo(
            run.id,
            observation(root).let {
                it.copy(locator = it.locator.copy(documentLocator = "second"))
            },
        )
        val target = MediaTarget(first.mediaId)
        val checkpoint =
            VideoProgressCheckpoint(
                target,
                VideoResumeAnchor(42),
                CompletionState.IN_PROGRESS,
                VideoResumeContext(second.bindingId, second.assetId, second.assetRevision),
                2,
                0,
            )
        rejected { store.progress.checkpointVideo(checkpoint) }
        assertNull(store.progress.load(target))
        store.progress.checkpointVideo(
            checkpoint.copy(
                context = VideoResumeContext(first.bindingId, first.assetId, first.assetRevision),
            ),
        )
        assertEquals(ProgressWriteResult.Stale, store.progress.markVideoCompleted(target, 0, 3))
        store.progress.markVideoCompleted(target, 1, 3)
        assertEquals(42L, store.progress.load(target)?.anchor?.positionMs)
        assertEquals(CompletionState.COMPLETED, store.progress.load(target)?.completion)
    }

    private fun count(table: String): Long =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getLong(0)
        }

    @Test
    fun corruptDatabaseIsNotSilentlyDeletedOrRecreated() = runBlocking {
        root()
        database.close()
        val file = context.getDatabasePath(name)
        java.io.RandomAccessFile(file, "rw").use {
            it.seek(0)
            it.write(ByteArray(100) { 0x55 })
        }
        val corruptedBytes = file.readBytes()
        database = UniversalMediaDatabase.open(context, name)
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase
        }
        assertTrue(file.exists())
        assertTrue(corruptedBytes.contentEquals(file.readBytes()))
    }

    private suspend fun root() = store.registerOrReauthorize(
        RootRegistrationEvidence(LocalRootDescriptor("test", "tree"), true, 0),
    )

    private fun observation(root: StorageRoot) = LocalDocumentObservation(
        LocalDocumentLocator(root.id, "test", "document"),
        "same.mp4",
        "video/mp4",
        100,
        10,
        1,
    )

    private suspend fun rejected(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected persistence invariant rejection")
        } catch (_: IllegalArgumentException) {
            // Domain invariant rejection.
        } catch (_: IllegalStateException) {
            // Stale run rejection.
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Database referential invariant rejection.
        }
    }
}
