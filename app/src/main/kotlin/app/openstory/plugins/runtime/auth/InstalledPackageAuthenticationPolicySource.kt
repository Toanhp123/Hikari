package app.openstory.plugins.runtime.auth

import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.PluginPackageStorage
import app.openstory.plugins.runtime.persistence.PluginStateStore
import kotlinx.serialization.json.Json

class InstalledPackageAuthenticationPolicySource(
    private val state: PluginStateStore,
    private val storage: PluginPackageStorage,
    private val json: Json,
) : InstalledAuthenticationPolicySource {
    override suspend fun installedAuthenticationPolicies(): List<InstalledAuthenticationPolicy> =
        state.all().mapNotNull { stored ->
            val bytes = storage.readEntry(
                stored.pluginId,
                stored.activeVersion.version,
                "manifest.json",
            )
            val manifest = (bytes as? PluginCallResult.Success)?.value?.let { raw ->
                runCatching { json.decodeFromString(PluginManifest.serializer(), raw.decodeToString()) }.getOrNull()
            }
            manifest?.capabilities?.authentication?.let { capability ->
                InstalledAuthenticationPolicy(stored.pluginId, stored.enabled, capability)
            }
        }.sortedBy { it.pluginId.value }
}
