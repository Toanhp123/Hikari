package app.openstory.model

data class CatalogEntry(
    val id: CatalogEntryId,
    val catalogPluginId: PluginId,
    val externalStoryId: String,
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
    val pluginVersion: String,
    val fetchedAtEpochMillis: Long,
) {
    init {
        require(externalStoryId.isNotBlank()) {
            "External story ID must not be blank"
        }
        require(externalStoryId.none(Char::isWhitespace)) {
            "External story ID must not contain whitespace"
        }
        require(sourceUrl == null || sourceUrl.isNotBlank()) {
            "Source URL must be null or non-blank"
        }
        require(title.isNotBlank()) {
            "Catalog title must not be blank"
        }
        require(aliases.none(String::isBlank)) {
            "Aliases must not contain blank values"
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
            (score == null && scoreScale == null) ||
                (
                    score != null &&
                        scoreScale != null &&
                        scoreScale > 0.0 &&
                        score in 0.0..scoreScale
                )
        ) {
            "Score and positive score scale must be provided together"
        }
        require(popularityRank == null || popularityRank > 0L) {
            "Popularity rank must be positive"
        }
        require(pluginVersion.isNotBlank()) {
            "Plugin version must not be blank"
        }
        require(fetchedAtEpochMillis >= 0L) {
            "Fetched timestamp must not be negative"
        }
    }
}
