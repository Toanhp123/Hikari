package app.universalmedia

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef.MediaTarget
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.data.RoomMediaStore
import app.universalmedia.data.UniversalMediaDatabase
import app.universalmedia.feature.library.LibraryScanStatus
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackState
import app.universalmedia.source.api.SourceResolution
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Task 11 acceptance harness.
 *
 * Phase A and Phase B must be invoked by scripts/task11-process-death.* as separate
 * instrumentation invocations. The script performs the real external am force-stop in between.
 * The evidence file is test-only assertion input; production code never reads it.
 */
class ProcessDeathSafEndToEndTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application = ApplicationProvider.getApplicationContext<UniversalMediaApplication>()

    @Test
    fun phaseA_establishDurableState() {
        evidenceFile().delete()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Add Folder").performClick()
            waitForCondition(PICKER_AND_SCAN_TIMEOUT_MS, "folder grant and scan completion") {
                val state = application.library.state.value
                state.cards.size == 1 &&
                    state.roots.size == 1 &&
                    state.roots.single().status == LibraryScanStatus.COMPLETE
            }

            val readGrants = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
            assertEquals(
                "clean Phase A should persist exactly one SAF read grant",
                1,
                readGrants.size,
            )
            val grant = readGrants.single()

            val card = application.library.state.value.cards.single()
            val mediaId = card.mediaId
            val target = MediaTarget(mediaId)
            val source = runBlocking { application.sources.resolve(target) }
            assertTrue(source is SourceResolution.Ready)
            source as SourceResolution.Ready

            val before = databaseSnapshot(target)
            assertEquals(1L, before.mediaRows)
            assertEquals(1L, before.bindingRows)
            assertEquals(1L, before.assetRows)
            assertEquals(1L, before.libraryRows)
            assertEquals(1L, before.activeLibraryRows)
            assertEquals(1L, before.scanRunRows)

            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            compose.waitForIdle()
            compose.onNodeWithTag("media:${mediaId.value}").performClick()
            waitForPlayerRoute()
            waitForPlaybackEstablished("initial playback")

            onPlaybackMainThread {
                requireNotNull(application.playback.controller).seekTo(SEEK_POSITION_MS)
                requireNotNull(application.playback.controller).play()
            }
            waitForCondition(PLAYBACK_TIMEOUT_MS, "seek position reached") {
                val state = playbackState()
                state?.phase == PlaybackPhase.READY && state.positionMs >= SEEK_POSITION_MS
            }
            Thread.sleep(PLAY_AFTER_SEEK_MS)
            val positionBeforePause = requireNotNull(playbackState()).positionMs
            assertTrue(positionBeforePause >= SEEK_POSITION_MS + MIN_ADVANCE_AFTER_SEEK_MS)
            onPlaybackMainThread { requireNotNull(application.playback.controller).pause() }
            waitForCondition(PLAYBACK_TIMEOUT_MS, "playback paused") {
                playbackState()?.isPlaying == false
            }
            val pausedPosition = requireNotNull(playbackState()).positionMs

            val checkpoint = waitForCheckpoint(target, pausedPosition)
            assertTrue(checkpoint.anchorPositionMs > 0)
            assertTrue(
                "pause checkpoint must reflect the pause boundary, not only the earlier seek",
                abs(checkpoint.anchorPositionMs - pausedPosition) <= PAUSE_CHECKPOINT_TOLERANCE_MS,
            )

            val after = databaseSnapshot(target)
            assertStableRows(before, after)
            assertEquals(before.scanRunRows, after.scanRunRows)
            assertEquals(source.provenance.bindingId, after.bindingId)
            assertEquals(source.provenance.assetId, after.assetId)
            assertEquals(source.provenance.assetRevision.value, after.assetRevision)

            Evidence(
                treeUri = grant.uri.toString(),
                mediaId = mediaId,
                bindingId = source.provenance.bindingId,
                assetId = source.provenance.assetId,
                assetRevision = source.provenance.assetRevision.value,
                checkpointPositionMs = checkpoint.anchorPositionMs,
                checkpointRevision = checkpoint.progressRevision,
                rowSnapshot = after,
            ).write(evidenceFile())
        }
        assertTrue(evidenceFile().isFile)
    }

    @Test
    fun phaseB_reconstructAfterForceStop() {
        val evidence = Evidence.read(evidenceFile())
        val target = MediaTarget(evidence.mediaId)

        assertTrue(
            context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && it.uri == Uri.parse(evidence.treeUri)
            },
        )

        waitForCondition(PLAYBACK_TIMEOUT_MS, "Library reconstruction") {
            application.library.state.value.cards.any { it.mediaId == evidence.mediaId }
        }
        val library = application.library.state.value
        assertEquals(1, library.cards.size)
        assertEquals(evidence.mediaId, library.cards.single().mediaId)

        val reconstructed = databaseSnapshot(target)
        assertStableRows(evidence.rowSnapshot, reconstructed)
        assertEquals(
            "Library reconstruction must not enqueue a new scan",
            evidence.rowSnapshot.scanRunRows,
            reconstructed.scanRunRows,
        )
        assertTrue(reconstructed.progressRevision >= evidence.checkpointRevision)
        assertTrue(
            abs(reconstructed.anchorPositionMs - evidence.checkpointPositionMs) <=
                PAUSE_CHECKPOINT_TOLERANCE_MS,
        )

        val source = runBlocking { application.sources.resolve(target) }
        assertTrue(source is SourceResolution.Ready)
        source as SourceResolution.Ready
        assertEquals(evidence.bindingId, source.provenance.bindingId)
        assertEquals(evidence.assetId, source.provenance.assetId)
        assertEquals(evidence.assetRevision, source.provenance.assetRevision.value)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            compose.waitForIdle()
            compose.onNodeWithTag("media:${evidence.mediaId.value}").performClick()
            waitForPlayerRoute()
            waitForPlaybackEstablished("resumed playback")
            val resumed = requireNotNull(playbackState())
            assertTrue(
                "resumed position ${resumed.positionMs}ms must be near durable checkpoint " +
                    "${evidence.checkpointPositionMs}ms",
                abs(resumed.positionMs - evidence.checkpointPositionMs) <= RESUME_TOLERANCE_MS,
            )
            onPlaybackMainThread { requireNotNull(application.playback.controller).pause() }
            waitForCondition(PLAYBACK_TIMEOUT_MS, "resumed playback paused") {
                playbackState()?.isPlaying == false
            }
        }

        val afterOpen = databaseSnapshot(target)
        assertStableRows(evidence.rowSnapshot, afterOpen)
        assertEquals(
            "Opening the durable Library item must not start a scan",
            evidence.rowSnapshot.scanRunRows,
            afterOpen.scanRunRows,
        )
        assertTrue(afterOpen.progressRevision >= evidence.checkpointRevision)
    }

    private fun assertStableRows(expected: RowSnapshot, actual: RowSnapshot) {
        assertEquals(expected.mediaRows, actual.mediaRows)
        assertEquals(expected.bindingRows, actual.bindingRows)
        assertEquals(expected.assetRows, actual.assetRows)
        assertEquals(expected.libraryRows, actual.libraryRows)
        assertEquals(expected.activeLibraryRows, actual.activeLibraryRows)
        assertEquals(expected.bindingId, actual.bindingId)
        assertEquals(expected.assetId, actual.assetId)
        assertEquals(expected.assetRevision, actual.assetRevision)
    }

    private fun databaseSnapshot(target: MediaTarget): RowSnapshot {
        val database = UniversalMediaDatabase.open(context)
        return try {
            val store = RoomMediaStore(database)
            val progress = runBlocking { store.progress.load(target) }
            val source = runBlocking { store.sources.load(target) }
            val found = source as? app.universalmedia.core.domain.LocalSourceLookup.Found
            RowSnapshot(
                mediaRows = scalar(database, "SELECT COUNT(*) FROM media"),
                bindingRows = scalar(database, "SELECT COUNT(*) FROM source_binding"),
                assetRows = scalar(database, "SELECT COUNT(*) FROM asset"),
                libraryRows = scalar(database, "SELECT COUNT(*) FROM library_entry"),
                activeLibraryRows = scalar(
                    database,
                    "SELECT COUNT(*) FROM library_entry WHERE membership = 'ACTIVE'",
                ),
                scanRunRows = scalar(database, "SELECT COUNT(*) FROM scan_run"),
                bindingId = found?.context?.bindingId,
                assetId = found?.context?.assetId,
                assetRevision = found?.context?.assetRevision?.value,
                anchorPositionMs = progress?.anchor?.positionMs ?: 0,
                progressRevision = progress?.stateRevision ?: 0,
            )
        } finally {
            database.close()
        }
    }

    private fun waitForCondition(
        timeoutMs: Long,
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun waitForPlayerRoute() {
        compose.waitUntil(PLAYER_ROUTE_TIMEOUT_MS) { hasText("Video") }
        compose.waitForIdle()
    }

    private fun hasText(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun playerOpenFailure(): String? = when {
        hasText("Could not connect to playback.") -> "connection failed"
        hasText("Could not load saved progress.") -> "progress load failed"
        hasText("Folder access was lost. Authorize the folder from Library.") ->
            "source access lost"
        hasText("No source is available for this video.") -> "source missing"
        hasText("The video could not be found.") -> "source not found"
        hasText("This source is not a supported video.") -> "source unsupported"
        hasText("The source is unavailable.") -> "source unavailable"
        hasText("The source could not be read. Try again.") -> "source provider failure"
        else -> null
    }

    private fun waitForPlaybackEstablished(description: String): PlaybackState {
        val deadline = System.nanoTime() + PLAYBACK_TIMEOUT_MS * 1_000_000
        var last: PlaybackState? = null
        while (System.nanoTime() < deadline) {
            playerOpenFailure()?.let { failure ->
                throw AssertionError("$description failed in PlayerRoute: $failure")
            }
            val state = playbackState()
            last = state
            if (state?.phase == PlaybackPhase.FAILED) {
                throw AssertionError(
                    "$description failed before readiness: " +
                        "failure=${state.failure}, position=${state.positionMs}ms, " +
                        "duration=${state.durationMs}ms",
                )
            }
            if (state?.phase == PlaybackPhase.READY) return state
            compose.waitForIdle()
            Thread.sleep(100)
        }
        throw AssertionError(
            "Timed out waiting for $description; last playback state=$last, " +
                "controllerPresent=${application.playback.controller != null}, " +
                "routeFailure=${playerOpenFailure()}",
        )
    }

    private fun waitForCheckpoint(target: MediaTarget, pausedPosition: Long): RowSnapshot {
        val deadline = System.nanoTime() + PLAYBACK_TIMEOUT_MS * 1_000_000
        var last = databaseSnapshot(target)
        while (System.nanoTime() < deadline) {
            last = databaseSnapshot(target)
            if (
                last.progressRevision > 0 &&
                abs(last.anchorPositionMs - pausedPosition) <= PAUSE_CHECKPOINT_TOLERANCE_MS
            ) {
                return last
            }
            Thread.sleep(100)
        }
        throw AssertionError(
            "Timed out waiting for durable pause checkpoint near ${pausedPosition}ms; " +
                "last=${last.anchorPositionMs}ms revision=${last.progressRevision}",
        )
    }

    private fun scalar(database: UniversalMediaDatabase, sql: String): Long =
        database.openHelper.readableDatabase.query(sql).use {
            assertTrue(it.moveToFirst())
            it.getLong(0)
        }

    private fun playbackState(): PlaybackState? {
        var state: PlaybackState? = null
        instrumentation.runOnMainSync { state = application.playback.controller?.state() }
        return state
    }

    private fun onPlaybackMainThread(block: () -> Unit) {
        instrumentation.runOnMainSync { block() }
    }

    private fun evidenceFile(): File = File(context.filesDir, EVIDENCE_FILE)

    private data class RowSnapshot(
        val mediaRows: Long,
        val bindingRows: Long,
        val assetRows: Long,
        val libraryRows: Long,
        val activeLibraryRows: Long,
        val scanRunRows: Long,
        val bindingId: SourceBindingId?,
        val assetId: AssetId?,
        val assetRevision: Long?,
        val anchorPositionMs: Long,
        val progressRevision: Long,
    )

    private data class Evidence(
        val treeUri: String,
        val mediaId: MediaId,
        val bindingId: SourceBindingId,
        val assetId: AssetId,
        val assetRevision: Long,
        val checkpointPositionMs: Long,
        val checkpointRevision: Long,
        val rowSnapshot: RowSnapshot,
    ) {
        fun write(file: File) {
            val json = JSONObject()
                .put("treeUri", treeUri)
                .put("mediaId", mediaId.value.toString())
                .put("bindingId", bindingId.value.toString())
                .put("assetId", assetId.value.toString())
                .put("assetRevision", assetRevision)
                .put("checkpointPositionMs", checkpointPositionMs)
                .put("checkpointRevision", checkpointRevision)
                .put("mediaRows", rowSnapshot.mediaRows)
                .put("bindingRows", rowSnapshot.bindingRows)
                .put("assetRows", rowSnapshot.assetRows)
                .put("libraryRows", rowSnapshot.libraryRows)
                .put("activeLibraryRows", rowSnapshot.activeLibraryRows)
                .put("scanRunRows", rowSnapshot.scanRunRows)
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.toString())
            check(temporary.renameTo(file)) { "Could not commit Task 11 evidence file" }
        }

        companion object {
            fun read(file: File): Evidence {
                assertTrue("Phase A evidence is missing", file.isFile)
                val json = JSONObject(file.readText())
                val bindingId = SourceBindingId(UUID.fromString(json.getString("bindingId")))
                val assetId = AssetId(UUID.fromString(json.getString("assetId")))
                val assetRevision = json.getLong("assetRevision")
                return Evidence(
                    treeUri = json.getString("treeUri"),
                    mediaId = MediaId(UUID.fromString(json.getString("mediaId"))),
                    bindingId = bindingId,
                    assetId = assetId,
                    assetRevision = assetRevision,
                    checkpointPositionMs = json.getLong("checkpointPositionMs"),
                    checkpointRevision = json.getLong("checkpointRevision"),
                    rowSnapshot = RowSnapshot(
                        mediaRows = json.getLong("mediaRows"),
                        bindingRows = json.getLong("bindingRows"),
                        assetRows = json.getLong("assetRows"),
                        libraryRows = json.getLong("libraryRows"),
                        activeLibraryRows = json.getLong("activeLibraryRows"),
                        scanRunRows = json.getLong("scanRunRows"),
                        bindingId = bindingId,
                        assetId = assetId,
                        assetRevision = assetRevision,
                        anchorPositionMs = json.getLong("checkpointPositionMs"),
                        progressRevision = json.getLong("checkpointRevision"),
                    ),
                )
            }
        }
    }

    private companion object {
        const val EVIDENCE_FILE = "task11-process-death-evidence.json"
        const val SEEK_POSITION_MS = 12_000L
        const val PLAY_AFTER_SEEK_MS = 1_500L
        const val MIN_ADVANCE_AFTER_SEEK_MS = 750L
        const val PAUSE_CHECKPOINT_TOLERANCE_MS = 750L
        const val RESUME_TOLERANCE_MS = 3_000L
        const val PLAYER_ROUTE_TIMEOUT_MS = 5_000L
        const val PLAYBACK_TIMEOUT_MS = 30_000L
        const val PICKER_AND_SCAN_TIMEOUT_MS = 180_000L
    }
}
