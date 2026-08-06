package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.api.packageformat.PluginPackageSignature
import app.openstory.plugin.api.packageformat.PluginSignatureAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PackageVerifierTrustDecisionTest {

    @Test
    fun verifiedPackageCarriesCryptographicSignatureDecision() {
        val expectedDecision =
            PackageSignatureDecision.verified(
                signerKeyId =
                    FIXTURE_SIGNER_KEY_ID,
                signerFingerprintSha256 =
                    FIXTURE_SIGNER_FINGERPRINT,
            )

        val verifier =
            PackageVerifier(
                archiveInspector =
                    PackageArchiveInspector {
                        AppResult.Success(
                            PluginPackageInspection(
                                pluginId =
                                    FIXTURE_PLUGIN_ID,
                                version =
                                    FIXTURE_VERSION,
                                entries =
                                    validArchiveEntries(),
                                declaredExecutableEntries =
                                    setOf(
                                        "main.js",
                                    ),
                                declaredCapabilities =
                                    emptySet(),
                            ),
                        )
                    },
                signatureVerifier =
                    PackageSignatureVerifier {
                        expectedDecision
                    },
            )

        val result =
            assertIs<
                AppResult.Success<
                    VerifiedPluginPackage,
                >,
            >(
                verifier.verify(
                    fixtureInstallRequest(),
                ),
            )

        assertEquals(
            expected =
                expectedDecision,
            actual =
                result.value
                    .signatureDecision,
        )
    }

    @Test
    fun unsignedPackageWithoutAcknowledgementIsRejected() {
        val verifier =
            PackageVerifier(
                archiveInspector =
                    PackageArchiveInspector {
                        AppResult.Success(
                            validInspection(),
                        )
                    },
            )

        val failure =
            assertIs<AppResult.Failure>(
                verifier.verify(
                    unsignedInstallRequestWithoutAcknowledgement(),
                ),
            )

        assertEquals(
            expected =
                "plugin.package_unsigned_warning_not_acknowledged",
            actual =
                failure.error.code,
        )
    }
}

private fun fixtureInstallRequest():
    InstallRequest =
    InstallRequest(
        packageBytes =
            FIXTURE_PACKAGE_BYTES,
        metadata =
            PluginPackageMetadata(
                pluginId =
                    FIXTURE_PLUGIN_ID,
                version =
                    FIXTURE_VERSION,
                exactPackageSha256 =
                    FIXTURE_PACKAGE_SHA_256,
                signature =
                    PluginPackageSignature(
                        algorithm =
                            PluginSignatureAlgorithm.ED25519,
                        signerKeyId =
                            FIXTURE_SIGNER_KEY_ID,
                        signatureBase64 =
                            "AA==",
                    ),
            ),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.REPOSITORY,
                sourceReference =
                    "fixture-repository",
                signatureState =
                    PackageSignatureState.INVALID,
                unsignedWarningAcknowledged =
                    false,
            ),
    )

private fun unsignedInstallRequestWithoutAcknowledgement():
    InstallRequest =
    InstallRequest(
        packageBytes =
            FIXTURE_PACKAGE_BYTES,
        metadata =
            PluginPackageMetadata(
                pluginId =
                    FIXTURE_PLUGIN_ID,
                version =
                    FIXTURE_VERSION,
                exactPackageSha256 =
                    FIXTURE_PACKAGE_SHA_256,
                signature =
                    null,
            ),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.LOCAL_FILE,
                sourceReference =
                    "unsigned-fixture.osp",
                signatureState =
                    PackageSignatureState.INVALID,
                unsignedWarningAcknowledged =
                    false,
            ),
    )

private fun validInspection():
    PluginPackageInspection =
    PluginPackageInspection(
        pluginId =
            FIXTURE_PLUGIN_ID,
        version =
            FIXTURE_VERSION,
        entries =
            validArchiveEntries(),
        declaredExecutableEntries =
            setOf(
                "main.js",
            ),
        declaredCapabilities =
            emptySet(),
    )

private fun validArchiveEntries():
    List<PackageArchiveEntry> =
    listOf(
        PackageArchiveEntry(
            path =
                "manifest.json",
            compressedSizeBytes =
                10L,
            uncompressedSizeBytes =
                10L,
            isSymbolicLink =
                false,
            isExecutable =
                false,
        ),
        PackageArchiveEntry(
            path =
                "main.js",
            compressedSizeBytes =
                10L,
            uncompressedSizeBytes =
                10L,
            isSymbolicLink =
                false,
            isExecutable =
                true,
        ),
    )

private const val FIXTURE_PLUGIN_ID =
    "community.fixture"

private const val FIXTURE_VERSION =
    "1.0.0"

private const val FIXTURE_SIGNER_KEY_ID =
    "fixture-author-main"

private const val FIXTURE_SIGNER_FINGERPRINT =
    "abababababababababababababababababababababababababababababababab"

private val FIXTURE_PACKAGE_BYTES =
    "fixture-package"
        .encodeToByteArray()

private const val FIXTURE_PACKAGE_SHA_256 =
    "1d58907e64cc82808cc22f3525edeec6a73fc1521d6b951b24b33b01f687dac4"
