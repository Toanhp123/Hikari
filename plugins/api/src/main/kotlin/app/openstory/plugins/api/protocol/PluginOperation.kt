package app.openstory.plugins.api.protocol

enum class PluginOperation(val wireName: String) {
    CATALOG_HOME("catalog.home"),
    CATALOG_SEARCH("catalog.search"),
    CATALOG_DETAILS("catalog.details"),
    CATALOG_FILTERS("catalog.filters"),
    CONTENT_SEARCH("content.search"),
    CONTENT_STORY("content.story"),
    CONTENT_CHAPTERS("content.chapters"),
    CONTENT_CHAPTER("content.chapter"),
}
