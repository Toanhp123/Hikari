package app.openstory.model

data class CanonicalStory(
    val id: StoryId,
    val contentType: ContentType,
    val preferredTitle: String,
    val aliases: Set<String>,
    val catalogEntries: List<CatalogEntry>,
) {
    init {
        require(preferredTitle.isNotBlank()) {
            "Preferred title must not be blank"
        }
    }
}
