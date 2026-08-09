package app.openstory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import app.openstory.di.OpenStoryAppGraph
import app.openstory.navigation.OpenStoryNavDisplay

@Composable
fun OpenStoryApp(
    graph: OpenStoryAppGraph,
    viewModelStoreOwner: ViewModelStoreOwner,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        OpenStoryNavDisplay(
            graph = graph,
            viewModelStoreOwner = viewModelStoreOwner,
            modifier = modifier,
        )
    }
}
