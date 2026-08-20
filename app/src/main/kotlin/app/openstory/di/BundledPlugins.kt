package app.openstory.di

import app.openstory.plugins.runtime.install.BundledPluginDescriptor

internal object BundledPlugins {
    val descriptors: List<BundledPluginDescriptor> = listOf(
        BundledPluginDescriptor(
            assetPath = "plugins/myanimelist-catalog.osp",
            pluginId = "org.openstory.catalog.myanimelist",
            version = "2.0.0",
            sha256 = "6362cd42177ba16e61470b6502909e8368ecf60cf4e7a71aca1f027f80a98d86",
        ),
        BundledPluginDescriptor(
            assetPath = "plugins/mangaupdates-catalog.osp",
            pluginId = "org.openstory.catalog.mangaupdates",
            version = "1.1.4",
            sha256 = "9ce7c04515cc48ca5ce1bc36cc37bbc6b1a3e47992a115b97bf2547d0ab71887",
        ),
        BundledPluginDescriptor(
            assetPath = "plugins/mangadex-content.osp",
            pluginId = "org.openstory.content.mangadex",
            version = "1.3.0",
            sha256 = "d967cc65604d27bfb972d84664eaa3c5d2005d5635344a12ee9284d83efa1709",
        ),
    )
}
