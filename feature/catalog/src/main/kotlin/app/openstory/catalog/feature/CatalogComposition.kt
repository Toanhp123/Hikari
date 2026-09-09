package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.trace.CatalogTrace
import java.util.concurrent.CancellationException

@Composable
internal fun CatalogComposition() {
    val applicationContext = LocalContext.current.applicationContext
    val session = remember(applicationContext) {
        CatalogRuntimeFactory(
            context = applicationContext,
            binding = VariantCatalogBinding.binding,
        ).createSession()
    }
    var screenState by remember(session) {
        mutableStateOf(CatalogScreenState.Activating)
    }

    DisposableEffect(session) {
        onDispose(session::close)
    }

    LaunchedEffect(session) {
        VariantCatalogBinding.diagnostics.activationStarted()
        AndroidCatalogTraceSink.mark(CatalogTrace.ACTIVATION_START)
        screenState = try {
            when (session.activate()) {
                is CatalogCapabilityActivation.Available -> {
                    VariantCatalogBinding.diagnostics.storageReady()
                    CatalogScreenState.Ready
                }
                is CatalogCapabilityActivation.Unavailable -> CatalogScreenState.SourceUnavailable
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CatalogScreenState.Failed
        }
    }

    CatalogScreen(screenState)
}
