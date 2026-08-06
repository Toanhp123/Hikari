package app.openstory.plugin.api

import kotlinx.serialization.Serializable

@Serializable
enum class PluginKind {
    CATALOG,
    CONTENT,
}

@Serializable
enum class PluginRuntime {
    DECLARATIVE,
    JAVASCRIPT,
}

@Serializable
enum class PluginCapability {
    NETWORK,
}
