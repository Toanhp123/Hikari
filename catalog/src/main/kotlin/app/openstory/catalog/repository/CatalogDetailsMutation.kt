package app.openstory.catalog.repository

import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.StoryId

data class CatalogDetailsMutation(
    // Proposed identity for a source reference that is not persisted yet.
    // The repository owns the durable StoryId chosen at commit time.
    val storyId: StoryId,
    val entry: CatalogEntry,
    val pluginVersion: String,
    val resolvedAtEpochMillis: Long,
) {
    init {
        require(entry.storyId == storyId)
        require(pluginVersion.isNotBlank())
        require(resolvedAtEpochMillis >= 0)
    }
}
