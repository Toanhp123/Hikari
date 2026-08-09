package app.openstory.plugins.api.manifest

import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val protocol: PluginProtocolVersion,
    val entry: String = "main.js",
    val provides: Set<PluginService>,
    val languages: Set<String> = emptySet(),
    val homepageUrl: String? = null,
    val sourceUrl: String? = null,
    val capabilities: PluginCapabilities = PluginCapabilities(),
) {
    init {
        require(ID_PATTERN.matches(id)) { "Plugin id must be a lowercase reverse-domain identifier" }
        require(name.isNotBlank() && name == name.trim() && name.none(Char::isISOControl)) {
            "Plugin name must not be blank or contain control characters"
        }
        require(VERSION_PATTERN.matches(version)) { "Plugin version must be semantic" }
        require(entry == "main.js") { "Plugin entry must be main.js" }
        require(provides.isNotEmpty()) { "Plugin must provide at least one service" }
        require(languages.all(::isNormalizedLanguageTag)) { "Language tags must be normalized and non-blank" }
        require(isHttpsUrl(homepageUrl)) { "Homepage URL must be HTTPS" }
        require(isHttpsUrl(sourceUrl)) { "Source URL must be HTTPS" }
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)+")
        private val VERSION_PATTERN = Regex(
            "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?",
        )

        private fun isNormalizedLanguageTag(value: String): Boolean =
            value.isNotBlank() && value == value.trim() && value == value.lowercase() &&
                value.none(Char::isISOControl) && value.none(Char::isWhitespace)

        private fun isHttpsUrl(value: String?): Boolean =
            value == null || runCatching { URI(value) }.getOrNull()?.let { uri ->
                uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
            } == true
    }
}

@Serializable
data class PluginCapabilities(
    val network: NetworkCapability? = null,
)

@Serializable
data class NetworkCapability(
    val hosts: Set<String>,
) {
    init {
        require(hosts.isNotEmpty()) { "Network capability must declare hosts" }
        require(hosts.all(::isNormalizedHttpsHost)) { "Network hosts must be normalized HTTPS hostnames" }
    }

    private companion object {
        val HOST_PATTERN = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")

        fun isNormalizedHttpsHost(host: String): Boolean =
            host.isNotBlank() && host == host.lowercase() && host.none(Char::isWhitespace) &&
                !host.contains("*") && !host.contains("/") && !host.contains(":") &&
                HOST_PATTERN.matches(host) && host.split('.').all { it.isNotEmpty() }
    }
}
