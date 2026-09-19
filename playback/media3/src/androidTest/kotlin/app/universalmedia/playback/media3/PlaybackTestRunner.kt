package app.universalmedia.playback.media3

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import app.universalmedia.playback.api.PlaybackCheckpoint
import app.universalmedia.playback.api.PlaybackProgressSink
import app.universalmedia.playback.api.PlaybackRuntimeDependencies
import app.universalmedia.playback.api.PlaybackRuntimeDependenciesProvider
import java.util.concurrent.CopyOnWriteArrayList

class PlaybackTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, PlaybackTestApplication::class.java.name, context)
}

class PlaybackTestApplication :
    Application(),
    PlaybackRuntimeDependenciesProvider {
    val checkpoints = CopyOnWriteArrayList<PlaybackCheckpoint>()
    override val playbackDependencies = PlaybackRuntimeDependencies(
        PlaybackProgressSink {
            checkpoints.add(it)
            it.expectedRevision + 1
        },
    )
}
