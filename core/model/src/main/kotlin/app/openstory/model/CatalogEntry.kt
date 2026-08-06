package app.openstory.model

data class CatalogEntry(
    val id: CatalogEntryId,
    val catalogPluginId: PluginId,
    val title: String,
    val description: String?,
    val score: Double?,
    val scoreScale: Double?,
    val externalStoryId: String = id.value,
    val sourceUrl: String? = null,
    val authors: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val coverReference: String? = null,
    val publicationStatus: String? = null,
) {
    init {
        require(title.isNotBlank()) {
            "Catalog title must not be blank"
        }
        require(externalStoryId.isNotBlank()) {
            "External story ID must not be blank"
        }
        require(sourceUrl == null || sourceUrl.isNotBlank()) {
            "Source URL must be null or non-blank"
        }
        require(authors.none(String::isBlank)) {
            "Authors must not contain blank values"
        }
        require(genres.none(String::isBlank)) {
            "Genres must not contain blank values"
        }
        require(coverReference == null || coverReference.isNotBlank()) {
            "Cover reference must be null or non-blank"
        }
        require(
            publicationStatus == null ||
                publicationStatus.isNotBlank()
        ) {
            "Publication status must be null or non-blank"
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
