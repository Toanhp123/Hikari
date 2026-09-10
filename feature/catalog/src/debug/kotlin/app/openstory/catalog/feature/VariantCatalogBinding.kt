package app.openstory.catalog.feature

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.feature.seed.LocalSeedCatalogSource
import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal object VariantCatalogBinding : CatalogVariantBinding {
    private val sourceKey = CatalogSourceKey("hikari.debug.local")

    override val binding = CatalogSourceBinding(
        catalogSourceKey = sourceKey,
        sourceVersion = "debug-seed-v1",
        acquisitionSource = LocalSeedCatalogSource(
            catalogSourceKey = sourceKey,
            onAcquisitionStarted = CatalogDebugDiagnostics::recordAcquisitionStarted,
        ),
        assetPolicy = SourceAssetPolicy(sourceKey, emptySet()),
    )
    override val diagnostics = object : CatalogCompositionDiagnostics {
        override fun activationStarted() {
            CatalogDebugDiagnostics.recordActivationStarted()
        }

        override fun storageReady() {
            CatalogDebugDiagnostics.recordStorageReady()
        }
    }
}
