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
        acquisitionSource = LocalSeedCatalogSource(sourceKey),
        assetPolicy = SourceAssetPolicy(sourceKey, emptySet()),
    )

    override fun localCoverResource(logicalAssetId: String, assetVersion: String): Int? {
        if (assetVersion != ASSET_VERSION) return null
        return when (logicalAssetId) {
            "debug:manga:cover-a" -> R.drawable.catalog_debug_manga_a
            "debug:manga:cover-b" -> R.drawable.catalog_debug_manga_b
            "debug:light-novel:cover-a" -> R.drawable.catalog_debug_light_novel_a
            "debug:light-novel:cover-b" -> R.drawable.catalog_debug_light_novel_b
            else -> null
        }
    }

    private const val ASSET_VERSION = "1"
}
