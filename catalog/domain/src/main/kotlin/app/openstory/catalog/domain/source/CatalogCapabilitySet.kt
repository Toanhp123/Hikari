package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.model.CatalogSectionKind

data class CatalogCapabilitySet(
    val discover: Boolean = false,
    val storyDetail: Boolean = false,
    val search: Boolean = false,
    val expandedSections: Set<CatalogSectionKind> = emptySet(),
    val similar: Boolean = false,
)
