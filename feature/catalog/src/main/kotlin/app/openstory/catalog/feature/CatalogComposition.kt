package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.catalog.feature.discover.CatalogDiscoverRuntime
import app.openstory.catalog.feature.discover.DiscoverScreen
import app.openstory.catalog.feature.discover.DiscoverViewModel
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.trace.CatalogTrace

@Composable
internal fun CatalogComposition() {
    val applicationContext = LocalContext.current.applicationContext
    val factory = remember(applicationContext) {
        DiscoverViewModel.factory {
            CatalogDiscoverRuntime(
                session = CatalogRuntimeFactory(
                    context = applicationContext,
                    binding = VariantCatalogBinding.binding,
                ).createSession(),
                onActivationStarted = {
                    VariantCatalogBinding.diagnostics.activationStarted()
                    AndroidCatalogTraceSink.mark(CatalogTrace.ACTIVATION_START)
                },
                onStorageReady = VariantCatalogBinding.diagnostics::storageReady,
            )
        }
    }
    val discoverViewModel: DiscoverViewModel = viewModel(factory = factory)
    val state by discoverViewModel.state.collectAsStateWithLifecycle()

    DiscoverScreen(
        state = state,
        onMediaSelected = discoverViewModel::selectMedia,
        onStorySelected = { _, _ -> },
        onRetry = discoverViewModel::retry,
    )
}
