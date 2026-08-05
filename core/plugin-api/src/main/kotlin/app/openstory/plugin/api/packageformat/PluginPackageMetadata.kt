package app.openstory.plugin.api.packageformat

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
        require(signerKeyId.isNotBlank()) {
            "Signer key ID must not be blank."
        }

        require(signatureBase64.isNotBlank()) {
            "Package signature must not be blank."
        }
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
        require(pluginId.isNotBlank()) {
            "Plugin ID must not be blank."
        }

        require(version.isNotBlank()) {
            "Plugin version must not be blank."
        }

        require(exactPackageSha256.matches(SHA_256_PATTERN)) {
            "Exact package checksum must be a lowercase SHA-256 value."
        }
    }

    fun signaturePayload(): String =
        "$exactPackageSha256\n$pluginId\n$version"

    private companion object {
        val SHA_256_PATTERN = Regex("""[0-9a-f]{64}""")
    }
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
            signatureState != PackageSignatureState.UNSIGNED ||
                unsignedWarningAcknowledged,
        ) {
            "Unsigned packages require explicit warning acknowledgement."
        }
    }
}
