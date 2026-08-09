package app.openstory.catalog.model

data class StoryCatalogSnapshot(
    val story: Story,
    val entries: List<CatalogEntry>,
)
