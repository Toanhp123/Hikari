package app.openstory.catalog.repository

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId

data class CatalogHomeMutation(
    val pluginId: PluginId,
    val pluginVersion: String,
    val refreshedAtEpochMillis: Long,
    val stories: List<Story>,
    val entries: List<CatalogEntry>,
    val sections: List<CatalogHomeSection>,
    val orderedSourceItemIds: Map<String, List<String>>,
) {
    init {
        require(pluginVersion.isNotBlank())
        require(refreshedAtEpochMillis >= 0)
        require(entries.all { it.pluginId == pluginId })
        require(sections.flatMap { it.items }.all { it.pluginId == pluginId })
        val storyIds = stories.map { it.id }.toSet()
        require(entries.all { it.storyId in storyIds })
        require(sections.flatMap { it.items }.toSet() == entries.toSet())
        require(orderedSourceItemIds.keys == sections.map { it.sourceId }.toSet())
        require(
            sections.all { section ->
                orderedSourceItemIds.getValue(section.sourceId) == section.items.map { it.sourceId }
            },
        )
    }
}
