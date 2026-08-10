package app.openstory.catalog.repository

import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.StoryId

data class CatalogDetailsMutation(
    val storyId: StoryId,
    val entry: CatalogEntry,
    val pluginVersion: String,
    val fetchedAtEpochMillis: Long,
) {
    init {
        require(entry.storyId == storyId)
        require(pluginVersion.isNotBlank())
        require(fetchedAtEpochMillis >= 0)
    }
}
