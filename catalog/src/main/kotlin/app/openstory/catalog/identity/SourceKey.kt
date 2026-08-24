package app.openstory.catalog.identity

import app.openstory.common.id.PluginId

data class SourceKey(
    val pluginId: PluginId,
    val sourceId: String,
) {
    init {
        require(sourceId.isNotBlank()) { "Source identity must not be blank" }
    }
}
