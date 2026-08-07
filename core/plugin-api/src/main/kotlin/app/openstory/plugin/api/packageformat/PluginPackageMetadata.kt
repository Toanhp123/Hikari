package app.openstory.plugin.api.packageformat

import app.openstory.plugin.api.PLUGIN_ID_PATTERN
import app.openstory.plugin.api.SEMANTIC_VERSION_PATTERN
import app.openstory.plugin.api.SHA_256_PATTERN
import app.openstory.plugin.api.isHttpsUrl
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
enum class PluginSignatureAlgorithm {
    ED25519,
}

@Serializable
data class PluginPackageSignature(
    val algorithm: PluginSignatureAlgorithm,
    val signerKeyId: String,
    val signatureBase64: String,
) {
    init {
        require(signerKeyId.matches(SIGNER_KEY_PATTERN)) {
            "Signer key ID must use the canonical token format."
        }
        val signatureBytes = runCatching {
            Base64.getDecoder().decode(signatureBase64)
        }.getOrNull()
        require(
            when (algorithm) {
                PluginSignatureAlgorithm.ED25519 -> signatureBytes?.size == ED25519_SIGNATURE_BYTES
            },
        ) {
            "Package signature has an invalid encoding or length."
        }
    }

    private companion object {
        const val ED25519_SIGNATURE_BYTES = 64
        val SIGNER_KEY_PATTERN = Regex("""[a-z0-9]+(?:[._-][a-z0-9]+)*""")
    }
}

@Serializable
data class PluginPackageMetadata(
    val pluginId: String,
    val version: String,
    val exactPackageSha256: String,
    val signature: PluginPackageSignature?,
) {
    init {
        require(pluginId.matches(PLUGIN_ID_PATTERN)) {
            "Plugin ID must use the canonical token format."
        }
        require(version.matches(SEMANTIC_VERSION_PATTERN)) {
            "Plugin version must use semantic version format."
        }
        require(exactPackageSha256.matches(SHA_256_PATTERN)) {
            "Exact package checksum must be a lowercase SHA-256 value."
        }
    }

    fun signaturePayload(): String = "$exactPackageSha256\n$pluginId\n$version"
}

@Serializable
enum class PackageInstallSource {
    LOCAL_FILE,
    MANIFEST_URL,
    REPOSITORY,
}

@Serializable
enum class PackageSignatureState {
    VERIFIED,
    UNSIGNED,
    INVALID,
}

@Serializable
data class PackageInstallProvenance(
    val source: PackageInstallSource,
    val sourceReference: String,
    val signatureState: PackageSignatureState,
    val unsignedWarningAcknowledged: Boolean,
) {
    init {
        require(sourceReference.isNotBlank()) {
            "Package install source reference must not be blank."
        }
        require(
            source != PackageInstallSource.MANIFEST_URL || isHttpsUrl(sourceReference),
        ) {
            "Manifest URL provenance must use HTTPS."
        }
        require(
            signatureState != PackageSignatureState.UNSIGNED || unsignedWarningAcknowledged,
        ) {
            "Unsigned packages require explicit warning acknowledgement."
        }
    }
}
