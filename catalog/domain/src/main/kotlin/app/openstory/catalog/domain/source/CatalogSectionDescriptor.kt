package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.model.CatalogSectionKind

enum class SectionExpansion { NONE, PAGED }

data class CatalogSectionDescriptor(
    val key: String,
    val kind: CatalogSectionKind,
    val displayLabel: String?,
    val expansion: SectionExpansion,
    val globallyOrdered: Boolean,
)
