package app.openstory.catalog.domain.identity

data class SourceStoryKey(
    val catalogSourceKey: CatalogSourceKey,
    val sourceStoryId: String,
)
