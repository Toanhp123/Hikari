package app.openstory.plugin.host.install

import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal data class PluginInstallMetadata(
    val packageSha256: String,
    val provenance: PackageInstallProvenance,
    val signatureDecision: PackageSignatureDecision,
    val acceptedCapabilities: Set<PluginCapability>,
)

internal object PluginInstallMetadataSidecar {

    fun write(
        directory: Path,
        packageSha256: String,
        provenance: PackageInstallProvenance,
        signatureDecision:
            PackageSignatureDecision,
        acceptedCapabilities:
            Set<PluginCapability>,
    ) {
        val properties =
            Properties().apply {
                writePackageMetadata(
                    packageSha256 =
                        packageSha256,
                    provenance =
                        provenance,
                    acceptedCapabilities =
                        acceptedCapabilities,
                )

                writeTrustDecision(
                    signatureDecision,
                )
            }

        writeProperties(
            directory =
                directory,
            properties =
                properties,
        )
    }

    private fun Properties.writePackageMetadata(
        packageSha256: String,
        provenance: PackageInstallProvenance,
        acceptedCapabilities:
            Set<PluginCapability>,
    ) {
        setProperty(
            PACKAGE_SHA_256,
            packageSha256,
        )

        setProperty(
            PROVENANCE_SOURCE,
            provenance.source.name,
        )

        setProperty(
            PROVENANCE_SOURCE_REFERENCE,
            provenance.sourceReference,
        )

        setProperty(
            PROVENANCE_SIGNATURE_STATE,
            provenance.signatureState.name,
        )

        setProperty(
            PROVENANCE_UNSIGNED_ACKNOWLEDGED,
            provenance
                .unsignedWarningAcknowledged
                .toString(),
        )

        setProperty(
            ACCEPTED_CAPABILITIES,
            acceptedCapabilities
                .map { capability ->
                    capability.name
                }
                .sorted()
                .joinToString(
                    separator = ",",
                ),
        )
    }

    private fun Properties.writeTrustDecision(
        signatureDecision:
            PackageSignatureDecision,
    ) {
        setProperty(
            TRUST_SIGNATURE_STATE,
            signatureDecision
                .signatureState
                .name,
        )

        signatureDecision.signerKeyId
            ?.let { signerKeyId ->
                setProperty(
                    TRUST_SIGNER_KEY_ID,
                    signerKeyId,
                )
            }

        signatureDecision
            .signerFingerprintSha256
            ?.let { fingerprint ->
                setProperty(
                    TRUST_SIGNER_FINGERPRINT_SHA_256,
                    fingerprint,
                )
            }
    }

    private fun writeProperties(
        directory: Path,
        properties: Properties,
    ) {
        Files.newOutputStream(
            directory.resolve(
                FILE_NAME,
            ),
        ).use { output ->
            properties.store(
                output,
                null,
            )
        }
    }

    fun read(
        directory: Path,
    ): PluginInstallMetadata {
        val properties =
            Properties()

        Files.newInputStream(
            directory.resolve(
                FILE_NAME,
            ),
        ).use { input ->
            properties.load(
                input,
            )
        }

        return properties.toInstallMetadata()
    }

    private fun Properties.toInstallMetadata():
        PluginInstallMetadata =
        PluginInstallMetadata(
            packageSha256 =
                required(
                    PACKAGE_SHA_256,
                ),
            provenance =
                PackageInstallProvenance(
                    source =
                        PackageInstallSource.valueOf(
                            required(
                                PROVENANCE_SOURCE,
                            ),
                        ),
                    sourceReference =
                        required(
                            PROVENANCE_SOURCE_REFERENCE,
                        ),
                    signatureState =
                        PackageSignatureState.valueOf(
                            required(
                                PROVENANCE_SIGNATURE_STATE,
                            ),
                        ),
                    unsignedWarningAcknowledged =
                        required(
                            PROVENANCE_UNSIGNED_ACKNOWLEDGED,
                        ).toBooleanStrict(),
                ),
            signatureDecision =
                PackageSignatureDecision(
                    signatureState =
                        PackageSignatureState.valueOf(
                            required(
                                TRUST_SIGNATURE_STATE,
                            ),
                        ),
                    signerKeyId =
                        getProperty(
                            TRUST_SIGNER_KEY_ID,
                        ),
                    signerFingerprintSha256 =
                        getProperty(
                            TRUST_SIGNER_FINGERPRINT_SHA_256,
                        ),
                ),
            acceptedCapabilities =
                readAcceptedCapabilities(),
        )

    private fun Properties.readAcceptedCapabilities():
        Set<PluginCapability> =
        required(
            ACCEPTED_CAPABILITIES,
        )
            .split(
                ",",
            )
            .filter {
                it.isNotEmpty()
            }
            .mapTo(
                mutableSetOf(),
            ) { capabilityName ->
                PluginCapability.valueOf(
                    capabilityName,
                )
            }

    private fun Properties.required(
        key: String,
    ): String =
        requireNotNull(
            getProperty(key),
        ) {
            "Missing plugin install metadata property: $key"
        }
}

private const val FILE_NAME =
    ".openstory-install.properties"

private const val PACKAGE_SHA_256 =
    "packageSha256"

private const val PROVENANCE_SOURCE =
    "source"

private const val PROVENANCE_SOURCE_REFERENCE =
    "sourceReference"

private const val PROVENANCE_SIGNATURE_STATE =
    "signatureState"

private const val PROVENANCE_UNSIGNED_ACKNOWLEDGED =
    "unsignedWarningAcknowledged"

private const val TRUST_SIGNATURE_STATE =
    "trustSignatureState"

private const val TRUST_SIGNER_KEY_ID =
    "trustSignerKeyId"

private const val TRUST_SIGNER_FINGERPRINT_SHA_256 =
    "trustSignerFingerprintSha256"

private const val ACCEPTED_CAPABILITIES =
    "acceptedCapabilities"
