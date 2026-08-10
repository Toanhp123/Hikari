package app.openstory.catalog.model

import app.openstory.common.id.PluginId

data class CatalogHomeSnapshot(
    val pluginId: PluginId,
    val pluginVersion: String,
    val refreshedAtEpochMillis: Long,
    val sections: List<CatalogHomeSection>,
)

data class CatalogHomeSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogEntry>,
) {
    init {
        require(sourceId.isNotBlank()) { "Source identity must not be blank" }
        require(title.isNotBlank()) { "Section title must not be blank" }
    }
}
