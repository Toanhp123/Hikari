package app.openstory.catalog.feature

import android.content.Context
import app.openstory.artwork.runtime.ArtworkRuntimeCallbacks
import app.openstory.artwork.remote.ArtworkTransport
import app.openstory.catalog.runtime.CatalogRuntimeOwnershipCallbacks
import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal interface CatalogVariantBinding {
    val bindings: List<CatalogSourceBinding>
    val diagnostics: CatalogCompositionDiagnostics
        get() = NoOpCatalogCompositionDiagnostics

    fun artworkTransport(context: Context): ArtworkTransport? = null

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

    val artworkRuntimeCallbacks: ArtworkRuntimeCallbacks
        get() = ArtworkRuntimeCallbacks()
}

internal object NoOpCatalogCompositionDiagnostics : CatalogCompositionDiagnostics {
    override fun activationStarted() = Unit

    override fun storageReady() = Unit
}
