package app.openstory.catalog.runtime.source

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.source.CatalogAcquisitionSource

data class CatalogSourceBinding(
    val catalogSourceKey: CatalogSourceKey,
    val sourceVersion: String,
    val acquisitionSource: CatalogAcquisitionSource? = null,
    val assetPolicy: SourceAssetPolicy? = null,
) {
    init {
        AcquisitionProvenance(catalogSourceKey, sourceVersion, 0L)
        require(assetPolicy == null || assetPolicy.catalogSourceKey == catalogSourceKey)
    }
}
