package app.openstory.catalog.feature

import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal interface CatalogVariantBinding {
    val binding: CatalogSourceBinding?

    fun localCoverResource(logicalAssetId: String, assetVersion: String): Int?
}
