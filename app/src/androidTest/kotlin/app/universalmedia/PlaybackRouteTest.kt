package app.universalmedia

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.domain.CompletionState
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalDocumentAccess
import app.universalmedia.core.domain.LocalDocumentAccessResult
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoResumeAnchor
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.ConsumptionTargetRef.MediaTarget
import app.universalmedia.data.RoomMediaStore
import app.universalmedia.data.UniversalMediaDatabase
import app.universalmedia.playback.api.PlaybackController
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.playback.api.PlaybackState
import app.universalmedia.source.local.LocalSourceResolver
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Real Room/resolver/root UI; a recording playback port isolates app routing from codec acceptance. */
class PlaybackRouteTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cardResumeControlsRecreationAndBackPreserveIdentity() = fixture(accessLost = false) {
            graph,
            scenario,
        ->
        compose.onNodeWithTag(graph.cardTag).performClick()
        compose.waitUntil(5000) { graph.player.starts == 1 }
        waitForText("Pause")
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithText("+10s").performClick()
        compose.waitUntil(5000) { graph.player.snapshot.positionMs == 14000L }
        val connection = graph.coordinator.controller
        scenario.recreate()
        waitForText("Play")
        compose.onNodeWithText("Back to Library").assertIsDisplayed()
        compose.waitForIdle()
        assertSame(connection, graph.coordinator.controller)
        assertEquals(1, graph.player.starts)
        assertEquals(14000L, graph.player.snapshot.positionMs)
        assertEquals(false, graph.player.snapshot.isPlaying)
        compose.onNodeWithText("Back to Library").performClick()
        compose.onNodeWithTag(graph.cardTag).assertIsDisplayed().performClick()
        waitForText("Play")
        compose.onNodeWithText("Back to Library").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(1, graph.player.starts)
        assertEquals(1, graph.library.state.value.cards.size)
        assertEquals(4000L, graph.player.request?.initialPositionMs)
    }

    @Test fun accessFailureRetryAndBackKeepLibraryAndDurableProgress() = fixture(
        accessLost = true,
    ) {
            graph,
            _,
        ->
        val before = runBlocking { graph.store.progress.load(graph.target) }
        compose.onNodeWithTag(graph.cardTag).performClick()
        waitForText("Folder access was lost. Authorize the folder from Library.")
        compose.onNodeWithText("Folder access was lost. Authorize the folder from Library.")
            .assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        waitForText("Folder access was lost. Authorize the folder from Library.")
        compose.onNodeWithText("Folder access was lost. Authorize the folder from Library.")
            .assertIsDisplayed()
        compose.onNodeWithText("Back to Library").performClick()
        compose.onNodeWithTag(graph.cardTag).assertIsDisplayed()
        assertEquals(0, graph.player.starts)
        assertEquals(before, runBlocking { graph.store.progress.load(graph.target) })
        assertEquals(1, graph.library.state.value.cards.size)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun fixture(
        accessLost: Boolean,
        test: (Graph, ActivityScenario<PlaybackRouteFixtureActivity>) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "playback-route-${UUID.randomUUID()}.db"
        val database = UniversalMediaDatabase.open(context, name)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val graph = runBlocking { createGraph(RoomMediaStore(database), scope, accessLost) }
            PlaybackRouteFixtureActivity.content = {
                val state by graph.library.state.collectAsStateWithLifecycle()
                LibraryPlaybackContent(state, graph.coordinator, scope, {}, {}, {})
            }
            ActivityScenario.launch(PlaybackRouteFixtureActivity::class.java).use { scenario ->
                compose.waitUntil(5000) { graph.library.state.value.cards.size == 1 }
                test(graph, scenario)
            }
        } finally {
            scope.cancel()
            PlaybackRouteFixtureActivity.content = null
            database.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun createGraph(
        store: RoomMediaStore,
        scope: CoroutineScope,
        accessLost: Boolean,
    ): Graph {
        val root = store.registerOrReauthorize(
            RootRegistrationEvidence(
                LocalRootDescriptor("fixture", "content://fixture/tree/root"),
                true,
                1,
            ),
        )
        val run = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 2)
        val video = store.commitRecognizedLocalVideo(
            run.id,
            LocalDocumentObservation(
                LocalDocumentLocator(root.id, "fixture", "content://fixture/video"),
                "route-fixture.mp4",
                "video/mp4",
                100,
                1,
                3,
            ),
        )
        val target = MediaTarget(video.mediaId)
        store.progress.checkpointVideo(
            VideoProgressCheckpoint(
                target,
                VideoResumeAnchor(4000, 30000),
                CompletionState.IN_PROGRESS,
                VideoResumeContext(video.bindingId, video.assetId, video.assetRevision),
                4,
                0,
            ),
        )
        val resolver = LocalSourceResolver(
            store.sources,
            LocalDocumentAccess { _, _ ->
                if (accessLost) {
                    LocalDocumentAccessResult.Failed(LocalAccessFailure.ACCESS_LOST)
                } else {
                    LocalDocumentAccessResult.Readable("video/mp4")
                }
            },
        )
        val player = RecordingPlayer()
        val coordinator = PlaybackCoordinator(store.progress, resolver) { player }
        val library = withContext(Dispatchers.Main) {
            LibraryStateHolder(store, LibraryCoordinator(store) {}, scope)
        }
        return Graph(store, target, library, coordinator, player)
    }

    private data class Graph(
        val store: RoomMediaStore,
        val target: MediaTarget,
        val library: LibraryStateHolder,
        val coordinator: PlaybackCoordinator,
        val player: RecordingPlayer,
    ) {
        val cardTag = "media:${target.mediaId.value}"
    }

    private class RecordingPlayer : PlaybackController {
        @Volatile var starts = 0

        @Volatile var snapshot = PlaybackState(PlaybackPhase.IDLE, false, 0, 30000)
        var request: PlaybackRequest? = null
        override suspend fun start(request: PlaybackRequest): Boolean {
            this.request = request
            snapshot = PlaybackState(PlaybackPhase.READY, true, request.initialPositionMs, 30000)
            starts++
            return true
        }
        override fun play() {
            snapshot = snapshot.copy(isPlaying = true)
        }
        override fun pause() {
            snapshot = snapshot.copy(isPlaying = false)
        }
        override fun seekTo(positionMs: Long) {
            snapshot = snapshot.copy(positionMs = positionMs)
        }
        override fun stop() {
            snapshot = snapshot.copy(phase = PlaybackPhase.IDLE)
        }
        override fun state(): PlaybackState = snapshot
        override fun close() = Unit
    }
}
