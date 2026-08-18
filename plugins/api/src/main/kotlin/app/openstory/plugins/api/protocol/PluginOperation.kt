package app.openstory.plugins.api.protocol

import app.openstory.plugins.api.manifest.PluginService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PluginOperation(
    val wireName: String,
    val service: PluginService,
) {
    @SerialName("catalog.home")
    CATALOG_HOME("catalog.home", PluginService.CATALOG),

    @SerialName("catalog.search")
    CATALOG_SEARCH("catalog.search", PluginService.CATALOG),

    @SerialName("catalog.details")
    CATALOG_DETAILS("catalog.details", PluginService.CATALOG),

    @SerialName("catalog.filters")
    CATALOG_FILTERS("catalog.filters", PluginService.CATALOG),

    @SerialName("content.search")
    CONTENT_SEARCH("content.search", PluginService.CONTENT),

    @SerialName("content.resolveUrl")
    CONTENT_RESOLVE_URL("content.resolveUrl", PluginService.CONTENT),

    @SerialName("content.story")
    CONTENT_STORY("content.story", PluginService.CONTENT),

    @SerialName("content.chapters")
    CONTENT_CHAPTERS("content.chapters", PluginService.CONTENT),

    @SerialName("content.chapter")
    CONTENT_CHAPTER("content.chapter", PluginService.CONTENT),
}
