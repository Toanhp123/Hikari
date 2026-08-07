package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageLayoutValidator
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import java.security.MessageDigest

data class PluginPackageInspection(
    val pluginId: String,
    val version: String,
    val entries: List<PackageArchiveEntry>,
    val declaredExecutableEntries: Set<String>,
    val declaredCapabilities: Set<PluginCapability>,
)

fun interface PackageArchiveInspector {

    fun inspect(
        packageBytes: ByteArray,
    ): AppResult<PluginPackageInspection>
}

data class PackageSignatureDecision(
    val signatureState: PackageSignatureState,
    val signerKeyId: String?,
    val signerFingerprintSha256: String?,
) {
    init {
        if (
            signatureState ==
            PackageSignatureState.VERIFIED
        ) {
            require(!signerKeyId.isNullOrBlank()) {
                "Verified signatures require a signer key ID."
            }

            require(
                signerFingerprintSha256
                    ?.matches(
                        SHA_256_PATTERN,
                    ) == true,
            ) {
                "Verified signatures require a signer fingerprint."
            }
        } else {
            require(signerKeyId == null) {
                "Unverified signatures must not identify a signer."
            }

            require(
                signerFingerprintSha256 ==
                    null,
            ) {
                "Unverified signatures must not expose a fingerprint."
            }
        }
    }

    companion object {

        fun verified(
            signerKeyId: String,
            signerFingerprintSha256: String,
        ): PackageSignatureDecision =
            PackageSignatureDecision(
                signatureState =
                    PackageSignatureState.VERIFIED,
                signerKeyId =
                    signerKeyId,
                signerFingerprintSha256 =
                    signerFingerprintSha256,
            )

        fun unsigned():
            PackageSignatureDecision =
            PackageSignatureDecision(
                signatureState =
                    PackageSignatureState.UNSIGNED,
                signerKeyId =
                    null,
                signerFingerprintSha256 =
                    null,
            )

        fun invalid():
            PackageSignatureDecision =
            PackageSignatureDecision(
                signatureState =
                    PackageSignatureState.INVALID,
                signerKeyId =
                    null,
                signerFingerprintSha256 =
                    null,
            )

        private val SHA_256_PATTERN =
            Regex(
                """[0-9a-f]{64}""",
            )
    }
}

fun interface PackageSignatureVerifier {

    fun verify(
        metadata: PluginPackageMetadata,
    ): PackageSignatureDecision
}

class PackageVerifier(
    private val archiveInspector:
        PackageArchiveInspector,
    private val signatureVerifier:
        PackageSignatureVerifier =
        RejectingPackageSignatureVerifier,
) {

    fun verify(
        request: InstallRequest,
    ): AppResult<VerifiedPluginPackage> {
        val verifiedRequest =
            request.copy(
                packageBytes =
                    request.packageBytes.copyOf(),
            )

        val actualPackageSha256 =
            sha256(
                verifiedRequest.packageBytes,
            )

        return if (
            actualPackageSha256 !=
            verifiedRequest
                .metadata
                .exactPackageSha256
        ) {
            checksumMismatch()
        } else {
            inspectAndVerify(
                request =
                    verifiedRequest,
                actualPackageSha256 =
                    actualPackageSha256,
            )
        }
    }

    private fun inspectAndVerify(
        request: InstallRequest,
        actualPackageSha256: String,
    ): AppResult<VerifiedPluginPackage> =
        when (
            val inspectionResult =
                archiveInspector.inspect(
                    request.packageBytes.copyOf(),
                )
        ) {
            is AppResult.Failure ->
                inspectionResult

            is AppResult.Success ->
                verifyInspection(
                    request =
                        request,
                    actualPackageSha256 =
                        actualPackageSha256,
                    inspection =
                        inspectionResult.value,
                )
        }

    private fun verifyInspection(
        request: InstallRequest,
        actualPackageSha256: String,
        inspection: PluginPackageInspection,
    ): AppResult<VerifiedPluginPackage> {
        val layoutErrors =
            PackageLayoutValidator.validateArchive(
                entries =
                    inspection.entries,
                declaredExecutableEntries =
                    inspection
                        .declaredExecutableEntries,
                requiredRuntimeEntry =
                    inspection
                        .declaredExecutableEntries
                        .singleOrNull(),
            )

        val signatureDecision =
            signatureDecision(
                metadata =
                    request.metadata,
            )

        return when {
            !matchesExternalMetadata(
                metadata =
                    request.metadata,
                inspection =
                    inspection,
            ) ->
                metadataMismatch()

            !inspection.declaredCapabilities
                .containsAll(
                    request.acceptedCapabilities,
                ) ->
                capabilityNotDeclared()

            layoutErrors.isNotEmpty() ->
                invalidLayout()

            signatureDecision.signatureState ==
                PackageSignatureState.UNSIGNED &&
                !request.provenance
                    .unsignedWarningAcknowledged ->
                unsignedWarningNotAcknowledged()

            request.metadata.signature != null &&
                signatureDecision.signatureState !=
                PackageSignatureState.VERIFIED ->
                invalidSignature()

            else ->
                verifiedPackage(
                    request =
                        request,
                    packageSha256 =
                        actualPackageSha256,
                    signatureDecision =
                        signatureDecision,
                )
        }
    }

    private fun matchesExternalMetadata(
        metadata: PluginPackageMetadata,
        inspection: PluginPackageInspection,
    ): Boolean =
        metadata.pluginId ==
            inspection.pluginId &&
            metadata.version ==
            inspection.version

    private fun signatureDecision(
        metadata: PluginPackageMetadata,
    ): PackageSignatureDecision =
        if (metadata.signature == null) {
            PackageSignatureDecision.unsigned()
        } else {
            signatureVerifier.verify(
                metadata,
            )
        }

    private fun verifiedPackage(
        request: InstallRequest,
        packageSha256: String,
        signatureDecision:
            PackageSignatureDecision,
    ): AppResult<VerifiedPluginPackage> =
        AppResult.Success(
            VerifiedPluginPackage(
                packageBytes =
                    request.packageBytes,
                packageSha256 =
                    packageSha256,
                pluginId =
                    request.metadata.pluginId,
                version =
                    request.metadata.version,
                signatureDecision =
                    signatureDecision,
                provenance =
                    request.provenance.copy(
                        signatureState =
                            signatureDecision.signatureState,
                    ),
                acceptedCapabilities =
                    request.acceptedCapabilities,
            ),
        )
}

private object RejectingPackageSignatureVerifier :
    PackageSignatureVerifier {

    override fun verify(
        metadata: PluginPackageMetadata,
    ): PackageSignatureDecision =
        PackageSignatureDecision.invalid()
}

private fun sha256(
    bytes: ByteArray,
): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString(
            separator = "",
        ) { byte ->
            "%02x".format(
                byte.toInt() and
                    UNSIGNED_BYTE_MASK,
            )
        }

private fun checksumMismatch():
    AppResult.Failure =
    pluginFailure(
        code =
            "plugin.package_checksum_mismatch",
    )

private fun metadataMismatch():
    AppResult.Failure =
    pluginFailure(
        code =
            "plugin.package_metadata_mismatch",
    )

private fun capabilityNotDeclared():
    AppResult.Failure =
    pluginFailure(
        code =
            "plugin.package_capability_not_declared",
    )

private fun invalidLayout():
    AppResult.Failure =
    pluginFailure(
        code =
            "plugin.package_layout_invalid",
    )

private fun unsignedWarningNotAcknowledged():
    AppResult.Failure =
    pluginFailure(
        code =
            "plugin.package_unsigned_warning_not_acknowledged",
    )

private fun invalidSignature():
    AppResult.Failure =
    pluginFailure(
        code =
            "plugin.package_signature_invalid",
    )

private fun pluginFailure(
    code: String,
): AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                code,
            retryable =
                false,
        ),
    )

private const val UNSIGNED_BYTE_MASK =
    0xff
