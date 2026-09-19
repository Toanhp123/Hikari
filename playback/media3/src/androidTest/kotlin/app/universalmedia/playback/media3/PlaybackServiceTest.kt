package app.universalmedia.playback.media3

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackProvenance
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.source.api.ResolvedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class PlaybackServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val uri = Uri.parse("content://app.universalmedia.playback.fixture/fixture.mp4")

    @Test fun localMp4SurvivesControllerDisconnectAndServiceCanBeRecreated() = runBlocking {
        instrumentation.context.grantUriPermission(
            context.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, PlaybackService::class.java),
            0,
        )
        assertFalse(info.exported)
        val bluetooth = context.packageManager.getActivityInfo(
            ComponentName(context, "androidx.media3.session.BluetoothValidationActivity"),
            0,
        )
        assertFalse(bluetooth.exported)
        val request = request()
        var controller: InternalPlaybackController? = null
        var screen = ActivityScenario.launch(PlaybackFixtureActivity::class.java)
        try {
            withContext(Dispatchers.Main) {
                controller = InternalPlaybackController.connect(context)
                assertTrue(controller.start(request))
            }
            awaitPlaying { controller!! }
            screen.close()
            withContext(Dispatchers.Main) {
                controller!!.close()
                controller = InternalPlaybackController.connect(context)
            }
            awaitPlaying { controller!! }
            withContext(Dispatchers.Main) { controller!!.pause() }
            val application = context.applicationContext as PlaybackTestApplication
            withTimeout(10_000) {
                while (application.checkpoints.none { it.request.target == request.target }) {
                    delay(50)
                }
            }
            withContext(Dispatchers.Main) {
                controller!!.stop()
                controller.close()
                controller = null
                context.stopService(Intent(context, PlaybackService::class.java))
            }
            // Deliver teardown on the main loop before binding again.
            instrumentation.waitForIdleSync()
            screen = ActivityScenario.launch(PlaybackFixtureActivity::class.java)
            withContext(Dispatchers.Main) {
                controller = InternalPlaybackController.connect(context)
                assertEquals(PlaybackPhase.IDLE, controller.state().phase)
                assertTrue(controller.start(request))
            }
            awaitPlaying { controller!! }
        } finally {
            screen.close()
            withContext(Dispatchers.Main) {
                controller?.stop()
                controller?.close()
                context.stopService(Intent(context, PlaybackService::class.java))
            }
            instrumentation.context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Test fun privateWireRoundTripsCanonicalContextAndRejectsNonContentUris() {
        val request = request()
        assertEquals(request, PlaybackWire.decode(PlaybackWire.encode(request)))
        val invalid = PlaybackWire.encode(request).apply {
            putString("uri", "https://example.com/video.mp4")
        }
        assertRejected(invalid)
        val ambiguous = PlaybackWire.encode(request).apply {
            putString("unit", MediaId.generate().value.toString())
        }
        assertRejected(ambiguous)
    }

    private fun assertRejected(bundle: Bundle) {
        val failure = runCatching { PlaybackWire.decode(bundle) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private suspend fun awaitPlaying(controller: () -> InternalPlaybackController) =
        withTimeout(15_000) {
            while (true) {
                val state = withContext(Dispatchers.Main) { controller().state() }
                assertFalse(
                    "Playback failed: ${state.failure}",
                    state.phase == PlaybackPhase.FAILED,
                )
                if (state.isPlaying && state.positionMs > 200) break
                delay(50)
            }
        }

    private fun request() = PlaybackRequest(
        ConsumptionTargetRef.MediaTarget(MediaId.generate()),
        ResolvedVideo(uri.toString(), "video/mp4"),
        PlaybackProvenance(SourceBindingId.generate(), AssetId.generate(), 0),
    )
}
