package app.openstory.catalog.feature.runtime

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.feature.VariantCatalogBinding
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.feature.trace.CatalogUiTrace
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory

class CatalogRuntimeAccess internal constructor(
    internal val holder: CatalogRuntimeHolder,
    internal val trace: CatalogUiTrace,
) {
    suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation =
        holder.activate(sourceKey)

    fun storyCollectorStarted() = VariantCatalogBinding.diagnostics.storyCollectorStarted()

    fun storyCollectorStopped() = VariantCatalogBinding.diagnostics.storyCollectorStopped()

    fun storyUiPublished() = trace.storyUiPublished()

    fun storyHeroMaterialized() = trace.storyHeroMaterialized()

    fun storyBodyMaterialized() = trace.storyBodyMaterialized()
}

@Composable
fun rememberCatalogRuntimeAccess(): CatalogRuntimeAccess {
    val applicationContext = LocalContext.current.applicationContext
    val trace = remember { CatalogUiTrace(AndroidCatalogTraceSink) }
    val holder = rememberCatalogRuntimeHolder(applicationContext, trace)
    return remember(holder, trace) { CatalogRuntimeAccess(holder, trace) }
}

@Composable
private fun rememberCatalogRuntimeHolder(
    applicationContext: Context,
    trace: CatalogUiTrace,
): CatalogRuntimeHolder {
    val runtimeFactory = remember(applicationContext, trace) {
        CatalogRuntimeHolder.factory(
            diagnostics = VariantCatalogBinding.diagnostics,
        ) {
            CatalogRuntimeFactory(
                context = applicationContext,
                bindings = VariantCatalogBinding.bindings,
                traceSink = AndroidCatalogTraceSink,
                queryListener = VariantCatalogBinding.queryListener,
                ownershipCallbacks = VariantCatalogBinding.diagnostics.runtimeOwnershipCallbacks,
            ).createHost()
        }
    }
    return viewModel(factory = runtimeFactory)
}
