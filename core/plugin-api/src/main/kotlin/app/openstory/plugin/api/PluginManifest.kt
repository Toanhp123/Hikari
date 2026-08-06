package app.openstory.plugin.api

import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class PluginRepositoryProvenance(
    val repositoryId: String,
    val repositoryUrl: String,
)

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

        require(entry.isNotBlank()) {
            "Plugin entry must not be blank."
        }

        require(".." !in entry) {
            "Plugin entry must not contain parent-directory traversal."
        }

        require(!entry.startsWith('/')) {
            "Plugin entry must be a relative path."
        }

        require(allowedHosts.all { it == it.lowercase() }) {
            "Allowed hosts must be normalized to lowercase."
        }

        require(allowedHosts.all { it.matches(HOST_PATTERN) }) {
            "Allowed hosts must contain normalized hostnames only."
        }

        require(allowedHosts.none { it.startsWith("*.") }) {
            "Wildcard hosts are not allowed."
        }
    }

    companion object {
        private val PLUGIN_ID_PATTERN =
            Regex("""[a-z0-9]+(?:[._-][a-z0-9]+)+""")

        private val SEMANTIC_VERSION_PATTERN =
            Regex("""\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""")

        private val SHA_256_PATTERN =
            Regex("""[0-9a-f]{64}""")

        private val HOST_PATTERN =
            Regex("""(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?""")

        private fun isHttpsUrl(
            value: String,
        ): Boolean =
            runCatching {
                val uri =
                    URI(value)

                uri.scheme == "https" &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null
            }.getOrDefault(false)
    }
}
