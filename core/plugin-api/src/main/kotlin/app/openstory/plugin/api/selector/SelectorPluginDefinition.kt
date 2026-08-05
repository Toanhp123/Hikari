package app.openstory.plugin.api.selector

import kotlinx.serialization.Serializable

@Serializable
data class SelectorPluginDefinition(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val operations: List<SelectorOperation>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
