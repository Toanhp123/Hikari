package app.openstory.catalog.domain.source

data class CatalogCapabilitySet(
    val discover: Boolean = false,
    val storyDetail: Boolean = false,
    val search: Boolean = false,
    val sectionDescriptors: List<CatalogSectionDescriptor> = emptyList(),
    val similar: Boolean = false,
)
