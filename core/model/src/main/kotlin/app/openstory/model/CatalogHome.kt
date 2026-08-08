package app.openstory.model

data class CatalogEntryWithStory(
    val storyId: StoryId,
    val entry: CatalogEntry,
)

data class CatalogHomeSnapshot(
    val pluginId: PluginId,
    val pluginVersion: String,
    val refreshedAtEpochMillis: Long,
    val sections: List<CatalogHomeSection>,
) {
    init {
        require(pluginVersion.isNotBlank()) {
            "Catalog Home plugin version must not be blank"
        }
        require(refreshedAtEpochMillis >= 0L) {
            "Catalog Home refresh timestamp must not be negative"
        }
        require(sections.map(CatalogHomeSection::sourceId).distinct().size == sections.size) {
            "Catalog Home section IDs must be unique"
        }
    }
}

data class CatalogHomeSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogEntryWithStory>,
) {
    init {
        requireStableCatalogSourceId(sourceId, "Catalog Home section source ID")
        require(title.isNotBlank()) {
            "Catalog Home section title must not be blank"
        }
        require(items.map { item -> item.entry.externalStoryId }.distinct().size == items.size) {
            "Catalog Home section items must have unique source identities"
        }
    }
}
