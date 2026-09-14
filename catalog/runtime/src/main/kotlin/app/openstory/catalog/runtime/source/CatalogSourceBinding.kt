package app.openstory.catalog.runtime.source

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.source.CatalogAcquisitionSource
import app.openstory.catalog.domain.source.CatalogAuthorityDescriptor
import app.openstory.catalog.domain.source.CatalogCapabilitySet

data class CatalogSourceBinding(
    val catalogSourceKey: CatalogSourceKey,
    val sourceVersion: String,
    val acquisitionSource: CatalogAcquisitionSource? = null,
    val assetPolicy: SourceAssetPolicy? = null,
    val displayName: String = catalogSourceKey.value,
    val mediaTypes: Set<CatalogMediaType> = CatalogMediaType.entries.toSet(),
    val capabilities: CatalogCapabilitySet = CatalogCapabilitySet(
        discover = true,
        storyDetail = true,
    ),
) {
    init {
        AcquisitionProvenance(catalogSourceKey, sourceVersion, 0L)
        require(assetPolicy == null || assetPolicy.catalogSourceKey == catalogSourceKey)
        CatalogAuthorityDescriptor(
            sourceKey = catalogSourceKey,
            displayName = displayName,
            mediaTypes = mediaTypes,
            capabilities = capabilities,
            artworkPolicy = assetPolicy,
        )
    }

    fun descriptor(): CatalogAuthorityDescriptor = CatalogAuthorityDescriptor(
        sourceKey = catalogSourceKey,
        displayName = displayName,
        mediaTypes = mediaTypes.toSet(),
        capabilities = capabilities.copy(expandedSections = capabilities.expandedSections.toSet()),
        artworkPolicy = assetPolicy,
    )
}
