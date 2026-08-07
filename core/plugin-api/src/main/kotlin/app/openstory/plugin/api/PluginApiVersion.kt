package app.openstory.plugin.api

import kotlinx.serialization.Serializable

@Serializable
data class PluginApiVersion(
    val major: Int,
    val minor: Int,
) {
    init {
        require(major > 0) {
            "Plugin API major version must be positive."
        }
        require(minor >= 0) {
            "Plugin API minor version must not be negative."
        }
    }

    fun requireSupportedBy(hostApi: PluginApiVersion) {
        require(major == hostApi.major) {
            "Plugin API major $major is not supported by host major ${hostApi.major}."
        }

        require(minor <= hostApi.minor) {
            "Plugin API minor $minor is newer than host minor ${hostApi.minor}."
        }
    }
}
