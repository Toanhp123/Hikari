package app.openstory.plugins.api.packageformat

import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class PluginArtifact(
    val pluginId: String,
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val signatureEd25519: String? = null,
) {
    init {
        require(PLUGIN_ID_PATTERN.matches(pluginId)) { "Plugin id must be a lowercase reverse-domain identifier" }
        require(SEMANTIC_VERSION_PATTERN.matches(version)) { "Plugin version must be semantic" }
        require(isHttpsUrl(downloadUrl)) { "Artifact download URL must be HTTPS" }
        require(SHA256_PATTERN.matches(sha256)) { "Artifact SHA-256 must be 64 lowercase hexadecimal characters" }
        require(
            signatureEd25519 == null || signatureEd25519.isNotBlank(),
        ) { "Artifact signature must be null or non-blank" }
    }

    private companion object {
        val PLUGIN_ID_PATTERN = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)+")
        val SEMANTIC_VERSION_PATTERN = Regex(
            "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?",
        )
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")

        fun isHttpsUrl(value: String): Boolean {
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            return uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }
    }
}
