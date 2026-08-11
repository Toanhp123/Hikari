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
            assetPath = "plugins/mangadex-content.osp",
            pluginId = "org.openstory.content.mangadex",
            version = "1.1.0",
            sha256 = "592cf603e910f4f333ad2dce77ef27af18bee540c6bfd1a66e5a9c47295cfb5d",
        ),
    )
}
