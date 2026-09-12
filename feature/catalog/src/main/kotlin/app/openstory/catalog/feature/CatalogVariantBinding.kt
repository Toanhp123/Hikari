package app.openstory.catalog.feature

import android.content.Context
import app.openstory.catalog.feature.assets.CatalogImageLoaderCallbacks
import app.openstory.catalog.feature.assets.RemoteCoverTransport
import app.openstory.catalog.runtime.CatalogRuntimeOwnershipCallbacks
import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal interface CatalogVariantBinding {
    val binding: CatalogSourceBinding?
    val diagnostics: CatalogCompositionDiagnostics
        get() = NoOpCatalogCompositionDiagnostics

    fun remoteCoverTransport(context: Context): RemoteCoverTransport? = null

    val queryListener: ((String) -> Unit)?
        get() = null

}

internal interface CatalogCompositionDiagnostics {
    fun activationStarted()

    fun storageReady()

    fun discoverCollectorStarted() = Unit

    fun discoverCollectorStopped() = Unit

    fun runtimeSessionClosed() = Unit

    fun storyCollectorStarted() = Unit

    fun storyCollectorStopped() = Unit

    val runtimeOwnershipCallbacks: CatalogRuntimeOwnershipCallbacks
        get() = CatalogRuntimeOwnershipCallbacks()

    val imageLoaderCallbacks: CatalogImageLoaderCallbacks
        get() = CatalogImageLoaderCallbacks()
}

private object NoOpCatalogCompositionDiagnostics : CatalogCompositionDiagnostics {
    override fun activationStarted() = Unit

    override fun storageReady() = Unit
}
