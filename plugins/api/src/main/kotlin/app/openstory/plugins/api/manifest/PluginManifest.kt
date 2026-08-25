package app.openstory.plugins.api.manifest

import app.openstory.plugins.api.protocol.PluginOperation
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
    val operations: Set<PluginOperation>? = null,
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
        operations?.let { declared ->
            require(declared.isNotEmpty()) { "Declared plugin operations must not be empty" }
            require(declared.all { operation -> operation.service in provides }) {
                "Plugin operations must belong to a declared service"
            }
        }
        require(languages.all(::isNormalizedLanguageTag)) { "Language tags must be normalized and non-blank" }
        require(isHttpsUrl(homepageUrl)) { "Homepage URL must be HTTPS" }
        require(isHttpsUrl(sourceUrl)) { "Source URL must be HTTPS" }
        require(capabilities.reader == null || supports(PluginOperation.CONTENT_CHAPTER)) {
            "Reader capability requires content.chapter"
        }
        capabilities.authentication?.let { authentication ->
            val networkHosts = capabilities.network?.hosts.orEmpty()
            require(authentication.credentialTargets.all { target -> target.host in networkHosts }) {
                "Authentication credential targets must belong to declared network hosts"
            }
        }
    }

    fun supports(operation: PluginOperation): Boolean =
        operation.service in provides && (operations == null || operation in operations)

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
    val reader: ReaderCapability? = null,
    val authentication: PluginAuthenticationCapability? = null,
)

@Serializable
data class ReaderCapability(
    val offlineDownload: Boolean = true,
    val remoteImages: Boolean = false,
) {
    init {
        require(!remoteImages || !offlineDownload) {
            "Remote image reader capability cannot declare offline download"
        }
    }
}

@Serializable
data class NetworkCapability(
    val hosts: Set<String>,
) {
    init {
        require(hosts.isNotEmpty()) { "Network capability must declare hosts" }
        require(hosts.all(::isNormalizedHttpsHost)) { "Network hosts must be normalized HTTPS hostnames" }
    }
}

internal val HOST_PATTERN = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")

internal fun isNormalizedHttpsHost(host: String): Boolean =
    host.isNotBlank() && host == host.lowercase() && host.none(Char::isWhitespace) &&
        !host.contains("*") && !host.contains("/") && !host.contains(":") &&
        HOST_PATTERN.matches(host) && host.split('.').all { it.isNotEmpty() }

internal fun requireNormalizedPathPrefix(pathPrefix: String) {
    require(pathPrefix.isNotBlank() && pathPrefix == pathPrefix.trim() && pathPrefix.startsWith('/')) {
        "Authentication path prefix must be absolute and non-blank"
    }
    require(!pathPrefix.contains("//") && pathPrefix.none(Char::isISOControl)) {
        "Authentication path prefix must be normalized"
    }
    val normalized = URI(null, null, pathPrefix, null).normalize().path
    require(normalized == pathPrefix) { "Authentication path prefix must not contain dot segments" }
}
