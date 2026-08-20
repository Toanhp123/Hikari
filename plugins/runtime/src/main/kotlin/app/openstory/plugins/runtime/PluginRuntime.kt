package app.openstory.plugins.runtime

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import kotlinx.serialization.json.JsonElement

interface PluginRuntime {
    suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement>

    suspend fun enabled(service: PluginService): List<InstalledPlugin>

    suspend fun enabled(operation: PluginOperation): List<InstalledPlugin> = enabled(operation.service)
}
