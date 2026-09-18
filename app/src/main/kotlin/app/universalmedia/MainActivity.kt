package app.universalmedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.universalmedia.feature.library.LibraryRoot
import app.universalmedia.storage.local.SafRegistrationResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = application as UniversalMediaApplication
        setContent {
            val state by graph.library.state.collectAsStateWithLifecycle()
            var selectedMedia by rememberSaveable { mutableStateOf<String?>(null) }
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
            MaterialTheme {
                Surface {
                    LibraryRoot(
                        state = state,
                        onAddRoot = { picker.launch(null) },
                        onMediaSelected = { selectedMedia = it.value.toString() },
                        onRetryScan = graph.library::retry,
                        onReloadLibrary = graph.library::refresh,
                    )
                    if (selectedMedia != null) {
                        AlertDialog(
                            onDismissRequest = { selectedMedia = null },
                            title = { Text("Video in Library") },
                            text = { Text("Playback is not available yet.") },
                            confirmButton = {
                                TextButton(onClick = { selectedMedia = null }) { Text("OK") }
                            },
                        )
                    }
                }
            }
        }
    }
}
