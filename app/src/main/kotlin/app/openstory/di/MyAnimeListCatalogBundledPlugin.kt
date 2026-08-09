package app.openstory.di

import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.host.install.BundledPluginAssetDescriptor

internal object MyAnimeListCatalogBundledPlugin {
    const val PLUGIN_ID = "org.openstory.catalog.myanimelist"
    const val VERSION = "1.1.0"
    const val ASSET_PATH = "plugins/myanimelist-catalog.osp"
    const val PACKAGE_SHA_256 = "a989e68e28e397de7de965547dc2e06b62794ac29d2274d8cefe2eba8b385c0d"

    val descriptor: BundledPluginAssetDescriptor
        get() = BundledPluginAssetDescriptor(
            assetPath = ASSET_PATH,
            pluginId = PLUGIN_ID,
            version = VERSION,
            exactPackageSha256 = PACKAGE_SHA_256,
            acceptedCapabilities = setOf(PluginCapability.NETWORK),
        )
}
