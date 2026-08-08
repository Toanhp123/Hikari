package app.openstory.model

data class CatalogSourceMetadata(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    val contentType: ContentType,
    val languageTags: Set<LanguageTag>,
    val coverReference: String?,
    val publicationStatus: String?,
    val score: Double?,
    val scoreScale: Double?,
    val popularityRank: Long?,
) {
    init {
        requireStableCatalogSourceId(sourceId, "Catalog metadata source ID")
        require(sourceUrl == null || sourceUrl.isNotBlank()) {
            "Catalog metadata source URL must be null or non-blank"
        }
        require(title.isNotBlank()) {
            "Catalog metadata title must not be blank"
        }
        require(aliases.none(String::isBlank)) {
            "Catalog metadata aliases must not contain blank values"
        }
        require(authors.none(String::isBlank)) {
            "Catalog metadata authors must not contain blank values"
        }
        require(genres.none(String::isBlank)) {
            "Catalog metadata genres must not contain blank values"
        }
        require(coverReference == null || coverReference.isNotBlank()) {
            "Catalog metadata cover reference must be null or non-blank"
        }
        require(
            publicationStatus == null ||
                publicationStatus.isNotBlank()
        ) {
            "Catalog metadata publication status must be null or non-blank"
        }
        requireValidCatalogScore(score, scoreScale)
        require(popularityRank == null || popularityRank > 0L) {
            "Catalog metadata popularity rank must be positive"
        }
    }
}
