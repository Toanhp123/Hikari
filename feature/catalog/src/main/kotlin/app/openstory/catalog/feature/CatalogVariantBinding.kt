package app.openstory.catalog.feature

import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal interface CatalogVariantBinding {
    val binding: CatalogSourceBinding?
    val diagnostics: CatalogCompositionDiagnostics
        get() = NoOpCatalogCompositionDiagnostics

}

internal interface CatalogCompositionDiagnostics {
    fun activationStarted()

    fun storageReady()
}

private object NoOpCatalogCompositionDiagnostics : CatalogCompositionDiagnostics {
    override fun activationStarted() = Unit

    override fun storageReady() = Unit
}
