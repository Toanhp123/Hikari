package app.openstory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.openstory.di.OpenStoryAppGraph
import app.openstory.ui.OpenStoryApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var graph: OpenStoryAppGraph

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            OpenStoryApp(
                graph = graph,
                viewModelStoreOwner = this,
            )
        }
    }
}
