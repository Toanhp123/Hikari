package app.openstory.model

data class CatalogEntry(
    val id: CatalogEntryId,
    val catalogPluginId: PluginId,
    val title: String,
    val description: String?,
    val score: Double?,
    val scoreScale: Double?,
) {
    init {
        require(title.isNotBlank()) {
            "Catalog title must not be blank"
        }
        require(
            score == null ||
                (
                    scoreScale != null &&
                        scoreScale > 0.0 &&
                        score in 0.0..scoreScale
                )
        ) {
            "Score must be within its positive score scale"
        }
    }
}
