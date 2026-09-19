package app.universalmedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Debug-only Activity exercises the production root's saved state across actual recreation. */
class PlaybackRouteFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { requireNotNull(content).invoke() }
    }

    companion object {
        internal var content: (@Composable () -> Unit)? = null
    }
}
