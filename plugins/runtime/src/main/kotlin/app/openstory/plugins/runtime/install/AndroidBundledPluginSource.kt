package app.openstory.plugins.runtime.install

import android.content.Context
import app.openstory.plugins.api.packageformat.PluginArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidBundledPluginSource(
    context: Context,
    private val descriptors: List<BundledPluginDescriptor>,
) : BundledPluginSource {
    private val assets = context.applicationContext.assets

    override suspend fun packages(): List<BundledPluginPackage> = withContext(Dispatchers.IO) {
        descriptors.map { descriptor ->
            require(descriptor.assetPath.isNotBlank() && !descriptor.assetPath.contains("..")) {
                "Bundled asset path is invalid"
            }
            val bytes = assets.open(descriptor.assetPath).use { it.readBytes() }
            require(sha256(bytes) == descriptor.sha256) { "Bundled package checksum mismatch" }
            BundledPluginPackage(
                bytes,
                PluginArtifact(
                    pluginId = descriptor.pluginId,
                    version = descriptor.version,
                    downloadUrl = "https://bundled.openstory.app/${descriptor.pluginId}/${descriptor.version}.osp",
                    sha256 = descriptor.sha256,
                ),
            )
        }
    }
}
