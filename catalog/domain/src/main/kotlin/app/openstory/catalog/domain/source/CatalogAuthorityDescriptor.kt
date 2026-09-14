package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType

data class CatalogAuthorityDescriptor(
    val sourceKey: CatalogSourceKey,
    val displayName: String,
    val mediaTypes: Set<CatalogMediaType>,
    val capabilities: CatalogCapabilitySet,
    val artworkPolicy: SourceAssetPolicy?,
) {
    init {
        require(displayName.isNotBlank())
        require(mediaTypes.isNotEmpty())
        require(artworkPolicy == null || artworkPolicy.catalogSourceKey == sourceKey)
    }
}
