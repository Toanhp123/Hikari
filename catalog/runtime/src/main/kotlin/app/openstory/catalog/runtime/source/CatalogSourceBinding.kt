package app.openstory.catalog.runtime.source

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.source.CatalogAuthorityDescriptor
import app.openstory.catalog.domain.source.CatalogCapabilitySet
import app.openstory.catalog.domain.source.CatalogDiscoverCapability
import app.openstory.catalog.domain.source.CatalogSearchCapability
import app.openstory.catalog.domain.source.CatalogSectionCapability
import app.openstory.catalog.domain.source.CatalogSimilarCapability
import app.openstory.catalog.domain.source.CatalogStoryCapability
import app.openstory.catalog.domain.validation.CatalogAcquisitionValidator

data class CatalogSourceBinding(
    val catalogSourceKey: CatalogSourceKey,
    val sourceVersion: String,
    val discoverCapability: CatalogDiscoverCapability? = null,
    val storyCapability: CatalogStoryCapability? = null,
    val searchCapability: CatalogSearchCapability? = null,
    val sectionCapability: CatalogSectionCapability? = null,
    val similarCapability: CatalogSimilarCapability? = null,
    val assetPolicy: SourceAssetPolicy? = null,
    val displayName: String = catalogSourceKey.value,
    val mediaTypes: Set<CatalogMediaType> = CatalogMediaType.entries.toSet(),
    val capabilities: CatalogCapabilitySet = CatalogCapabilitySet(
        discover = discoverCapability != null,
        storyDetail = storyCapability != null,
        search = searchCapability != null,
        similar = similarCapability != null,
    ),
) {
    init {
        AcquisitionProvenance(catalogSourceKey, sourceVersion, 0L)
        require(assetPolicy == null || assetPolicy.catalogSourceKey == catalogSourceKey)
        require(
            capabilities == CatalogCapabilitySet(
                discover = discoverCapability != null,
                storyDetail = storyCapability != null,
                search = searchCapability != null,
                sectionDescriptors = capabilities.sectionDescriptors,
                similar = similarCapability != null,
            ),
        )
        require((sectionCapability != null) == capabilities.sectionDescriptors.isNotEmpty())
        CatalogAcquisitionValidator.requireValidSectionDescriptors(capabilities.sectionDescriptors)
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
        capabilities = capabilities.copy(sectionDescriptors = capabilities.sectionDescriptors.toList()),
        artworkPolicy = assetPolicy,
    )
}
