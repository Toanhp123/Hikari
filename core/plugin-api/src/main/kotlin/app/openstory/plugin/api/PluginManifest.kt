package app.openstory.plugin.api

import java.net.URI
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class PluginRepositoryProvenance(
    val repositoryId: String,
    val repositoryUrl: String,
) {
    init {
        require(repositoryId.matches(PLUGIN_ID_PATTERN)) {
            "Repository ID must be a lowercase reverse-domain-like token."
        }
        require(isHttpsUrl(repositoryUrl)) {
            "Repository URL must use HTTPS."
        }
    }
}

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val packageChecksumSha256: String,
    val homepageUrl: String? = null,
    val sourceUrl: String? = null,
    val minimumHostVersion: String,
    val updateUrl: String? = null,
    val repositoryProvenance: PluginRepositoryProvenance? = null,
    val api: PluginApiVersion,
    val kinds: Set<PluginKind>,
    val languages: Set<String>,
    val allowedHosts: Set<String>,
    val capabilities: Set<PluginCapability> = emptySet(),
    val runtime: PluginRuntime,
    val entry: String,
    val declarativeOrigin: String? = null,
) {
    init {
        require(id.matches(PLUGIN_ID_PATTERN)) {
            "Plugin ID must be a lowercase reverse-domain-like token."
        }
        require(name.isNotBlank()) {
            "Plugin name must not be blank."
        }
        require(version.matches(SEMANTIC_VERSION_PATTERN)) {
            "Plugin version must use semantic version format."
        }
        require(packageChecksumSha256.matches(SHA_256_PATTERN)) {
            "Package checksum must be a lowercase SHA-256 value."
        }
        require(minimumHostVersion.matches(SEMANTIC_VERSION_PATTERN)) {
            "Minimum host version must use semantic version format."
        }
        require(homepageUrl == null || isHttpsUrl(homepageUrl)) {
            "Plugin homepage URL must use HTTPS."
        }
        require(sourceUrl == null || isHttpsUrl(sourceUrl)) {
            "Plugin source URL must use HTTPS."
        }
        require(updateUrl == null || isHttpsUrl(updateUrl)) {
            "Plugin update URL must use HTTPS."
        }
        require(updateUrl != null || repositoryProvenance != null) {
            "Plugin must declare an update URL or repository provenance."
        }
        require(kinds.isNotEmpty()) {
            "Plugin must declare at least one kind."
        }
        requireNormalizedLanguageTags(languages, "Plugin languages")
        require(entry.isNotBlank()) {
            "Plugin entry must not be blank."
        }
        require(!hasUnsafeRelativePath(entry)) {
            "Plugin entry must be a safe relative path."
        }
        require(allowedHosts.all { it == it.lowercase(Locale.ROOT) }) {
            "Allowed hosts must be normalized to lowercase."
        }
        require(allowedHosts.all { it.matches(HOST_PATTERN) }) {
            "Allowed hosts must contain normalized hostnames only."
        }
        require(allowedHosts.none { it.startsWith("*.") }) {
            "Wildcard hosts are not allowed."
        }
        require(
            if (PluginCapability.NETWORK in capabilities) {
                allowedHosts.isNotEmpty()
            } else {
                allowedHosts.isEmpty()
            },
        ) {
            "Network capability and allowed hosts must be declared together."
        }
        require(
            declarativeOrigin == null ||
                isValidDeclarativeOrigin(
                    value = declarativeOrigin,
                    allowedHosts = allowedHosts,
                ),
        ) {
            "Declarative origin must be an absolute HTTPS URL on an allowed host " +
                "without user information, query, or fragment."
        }
        require(runtime == PluginRuntime.DECLARATIVE || declarativeOrigin == null) {
            "Declarative origin is only valid for declarative plugins."
        }
        require(
            when (runtime) {
                PluginRuntime.DECLARATIVE -> entry == DECLARATIVE_ENTRY
                PluginRuntime.JAVASCRIPT -> entry == JAVASCRIPT_ENTRY
            },
        ) {
            "Plugin runtime and entry must use the canonical package entry."
        }
    }

    private fun isValidDeclarativeOrigin(
        value: String,
        allowedHosts: Set<String>,
    ): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT)

        uri.isAbsolute &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !host.isNullOrBlank() &&
            host in allowedHosts &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null
    }.getOrDefault(false)

    private fun hasUnsafeRelativePath(value: String): Boolean =
        value.startsWith('/') ||
            value.startsWith('\\') ||
            DRIVE_PATH.matches(value) ||
            value.contains('\\') ||
            value.split('/').any { segment ->
                segment.isBlank() || segment == "." || segment == ".."
            } ||
            value.any(Char::isISOControl)

    companion object {
        private const val DECLARATIVE_ENTRY = "selector.json"
        private const val JAVASCRIPT_ENTRY = "main.js"
        private val DRIVE_PATH = Regex("""[A-Za-z]:.*""")
    }
}
