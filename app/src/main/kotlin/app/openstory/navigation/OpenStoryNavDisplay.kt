package app.openstory.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import app.openstory.di.OpenStoryAppGraph

@Composable
fun OpenStoryNavDisplay(
    graph: OpenStoryAppGraph,
    viewModelStoreOwner: ViewModelStoreOwner,
    modifier: Modifier = Modifier,
) {
    AppNavHost(rememberAppNavigator(), modifier)
}
