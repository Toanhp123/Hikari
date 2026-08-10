package app.openstory.plugins.runtime.install

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.persistence.StoredPluginVersion

interface PluginPackageStorage {
    suspend fun store(value: VerifiedPluginPackage): PluginCallResult<StoredPluginVersion>

    suspend fun readEntry(
        pluginId: PluginId,
        version: String,
        entry: String,
    ): PluginCallResult<ByteArray>

    suspend fun remove(location: String)
}
