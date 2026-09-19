package app.universalmedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.RootId
import app.universalmedia.feature.library.LibraryRoot
import app.universalmedia.feature.library.LibraryUiState
import app.universalmedia.storage.local.SafRegistrationResult
import java.util.UUID
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = application as UniversalMediaApplication
        setContent {
            val state by graph.library.state.collectAsStateWithLifecycle()
            val picker =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        graph.library.register {
                            when (val result = graph.storage.register(uri)) {
                                is SafRegistrationResult.Registered -> result.evidence
                                is SafRegistrationResult.Failed -> null
                            }
                        }
                    }
                }
            LibraryPlaybackContent(
                state,
                graph.playback,
                graph.playbackScope,
                { picker.launch(null) },
                graph.library::retry,
                graph.library::refresh,
            )
        }
    }
}

@Composable
internal fun LibraryPlaybackContent(
    state: LibraryUiState,
    coordinator: PlaybackCoordinator,
    scope: CoroutineScope,
    onAddRoot: () -> Unit,
    onRetryScan: (RootId) -> Unit,
    onReloadLibrary: () -> Unit,
) {
    var selectedMedia by rememberSaveable { mutableStateOf<String?>(null) }
    MaterialTheme {
        Surface {
            val media = selectedMedia
            if (media == null) {
                LibraryRoot(
                    state,
                    onAddRoot,
                    { selectedMedia = it.value.toString() },
                    onRetryScan,
                    onReloadLibrary,
                )
            } else {
                PlayerRoute(
                    MediaId(UUID.fromString(media)),
                    coordinator,
                    scope,
                    onBack = { selectedMedia = null },
                )
            }
        }
    }
}
