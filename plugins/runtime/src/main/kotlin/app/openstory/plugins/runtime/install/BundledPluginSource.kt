package app.openstory.plugins.runtime.install

import app.openstory.plugins.api.packageformat.PluginArtifact

data class BundledPluginDescriptor(
    val assetPath: String,
    val pluginId: String,
    val version: String,
    val sha256: String,
)

data class BundledPluginPackage(
    val bytes: ByteArray,
    val provenance: PluginArtifact,
)

fun interface BundledPluginSource {
    suspend fun packages(): List<BundledPluginPackage>
}
