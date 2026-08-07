package app.openstory.plugin.api.selector

import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import app.openstory.plugin.api.selector.content.ContentSelectorEndpoints
import kotlinx.serialization.Serializable

@Serializable
data class SelectorPluginDefinitionV2(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val catalog: CatalogSelectorEndpoints? = null,
    val content: ContentSelectorEndpoints? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
