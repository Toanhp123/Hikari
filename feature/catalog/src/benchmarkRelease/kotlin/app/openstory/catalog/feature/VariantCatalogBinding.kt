package app.openstory.catalog.feature

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.feature.seed.BenchmarkCatalogSource
import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal object VariantCatalogBinding : CatalogVariantBinding {
    private val sourceKey = CatalogSourceKey("hikari.benchmark.local")

    override val binding = CatalogSourceBinding(
        catalogSourceKey = sourceKey,
        sourceVersion = "benchmark-seed-v1",
        acquisitionSource = BenchmarkCatalogSource(sourceKey),
        assetPolicy = SourceAssetPolicy(sourceKey, emptySet()),
    )
}
