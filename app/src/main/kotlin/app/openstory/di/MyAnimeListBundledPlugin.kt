package app.openstory.di

import app.openstory.plugins.runtime.install.BundledPluginDescriptor

internal object MyAnimeListBundledPlugin {
    const val PLUGIN_ID = "org.openstory.catalog.myanimelist"
    const val VERSION = "2.0.0"
    const val ASSET_PATH = "plugins/myanimelist-catalog.osp"
    const val PACKAGE_SHA_256 = "6362cd42177ba16e61470b6502909e8368ecf60cf4e7a71aca1f027f80a98d86"

    val descriptor: BundledPluginDescriptor
        get() = BundledPluginDescriptor(
            assetPath = ASSET_PATH,
            pluginId = PLUGIN_ID,
            version = VERSION,
            sha256 = PACKAGE_SHA_256,
        )
}
