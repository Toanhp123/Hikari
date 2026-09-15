package app.openstory.library.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.library.runtime.LibraryRuntime

@Composable
fun HomeEntryPoint(
    libraryRuntime: LibraryRuntime,
    onExploreManga: () -> Unit,
    onExploreLightNovels: () -> Unit,
    onStorySelected: (LibraryStoryPosterUi) -> Unit,
    artwork: @Composable (LibraryStoryPosterUi, Modifier) -> Unit,
) {
    val factory = remember(libraryRuntime) {
        HomeViewModel.factory {
            LibraryHomeRuntime(libraryRuntime, ownsRuntime = false)
        }
    }
    val viewModel = viewModel<HomeViewModel>(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.resume()
                Lifecycle.Event.ON_STOP -> viewModel.quiesce()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.resume()
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.quiesce()
        }
    }

    HomeScreen(
        state = state,
        onInputQueryChanged = viewModel::updateInputQuery,
        onFilterSelected = viewModel::selectFilter,
        onExploreManga = onExploreManga,
        onExploreLightNovels = onExploreLightNovels,
        onStorySelected = onStorySelected,
        onRetry = viewModel::retry,
        artwork = artwork,
    )
}
