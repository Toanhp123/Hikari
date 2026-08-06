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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PackageVerifierTest {

    @Test
    fun traversalEntryIsRejectedAfterChecksumAndBeforeStaging() {
        val inspector =
            RecordingArchiveInspector(
                inspection =
                    traversalInspection(),
            )

        val verifier =
            PackageVerifier(
                archiveInspector =
                    inspector,
            )

        val failure =
            assertIs<AppResult.Failure>(
                verifier.verify(
                    fixtureRequest(),
                ),
            )

        assertEquals(
            expected =
                "plugin.package_layout_invalid",
            actual =
                failure.error.code,
        )

        assertEquals(
            expected = 1,
            actual =
                inspector.inspectionCalls,
        )
    }

    @Test
    fun invalidSignatureIsRejectedBeforeStaging() {
        val signatureVerifier =
            RecordingPackageSignatureVerifier(
                verificationResult =
                    PackageSignatureDecision.invalid(),
            )

        val verifier =
            PackageVerifier(
                archiveInspector =
                    RecordingArchiveInspector(
                        inspection =
                            validInspection(),
                    ),
                signatureVerifier =
                    signatureVerifier,
            )

        val failure =
            assertIs<AppResult.Failure>(
                verifier.verify(
                    fixtureRequest(
                        signature =
                            invalidSignature(),
                    ),
                ),
            )

        assertEquals(
            expected =
                "plugin.package_signature_invalid",
            actual =
                failure.error.code,
        )

        assertEquals(
            expected = 1,
            actual =
                signatureVerifier.verificationCalls,
        )
    }

    @Test
    fun verificationUsesImmutablePackageByteSnapshot() {
        val request =
            fixtureRequest()

        val mutablePackageBytes =
            request.packageBytes.copyOf()

        val expectedPackageBytes =
            mutablePackageBytes.copyOf()

        var inspectedPackageBytes:
            ByteArray? =
            null

        val verifier =
            PackageVerifier(
                archiveInspector =
                    PackageArchiveInspector { packageBytes ->
                        mutablePackageBytes.fill(
                            0,
                        )

                        inspectedPackageBytes =
                            packageBytes.copyOf()

                        AppResult.Success(
                            validInspection(),
                        )
                    },
            )

        val success =
            assertIs<AppResult.Success<*>>(
                verifier.verify(
                    request.copy(
                        packageBytes =
                            mutablePackageBytes,
                    ),
                ),
            )

        val verifiedPackage =
            assertIs<VerifiedPluginPackage>(
                success.value,
            )

        assertContentEquals(
            expected =
                expectedPackageBytes,
            actual =
                requireNotNull(
                    inspectedPackageBytes,
                ),
        )

        assertContentEquals(
            expected =
                expectedPackageBytes,
            actual =
                verifiedPackage.packageBytes,
        )
    }

    private fun traversalInspection():
        PluginPackageInspection =
        PluginPackageInspection(
            pluginId =
                FIXTURE_PLUGIN_ID,
            version =
                FIXTURE_VERSION,
            entries =
                listOf(
                    archiveEntry(
                        path =
                            "manifest.json",
                    ),
                    archiveEntry(
                        path =
                            "../escape.js",
                    ),
                ),
            declaredExecutableEntries =
                emptySet(),
            declaredCapabilities =
                emptySet(),
        )

    private fun validInspection():
        PluginPackageInspection =
        PluginPackageInspection(
            pluginId =
                FIXTURE_PLUGIN_ID,
            version =
                FIXTURE_VERSION,
            entries =
                listOf(
                    archiveEntry(
                        path =
                            "manifest.json",
                    ),
                    archiveEntry(
                        path =
                            "main.js",
                    ),
                ),
            declaredExecutableEntries =
                emptySet(),
            declaredCapabilities =
                emptySet(),
        )

    private fun fixtureRequest(
        signature: PluginPackageSignature? =
            null,
    ): InstallRequest =
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
                        signature,
                ),
            provenance =
                fixtureProvenance(
                    isSigned =
                        signature != null,
                ),
        )

    private fun invalidSignature():
        PluginPackageSignature =
        PluginPackageSignature(
            algorithm =
                PluginSignatureAlgorithm.ED25519,
            signerKeyId =
                "fixture-author",
            signatureBase64 =
                "aW52YWxpZA==",
        )

    private fun archiveEntry(
        path: String,
    ): PackageArchiveEntry =
        PackageArchiveEntry(
            path =
                path,
            compressedSizeBytes =
                10L,
            uncompressedSizeBytes =
                10L,
            isSymbolicLink =
                false,
            isExecutable =
                false,
        )

    private fun fixtureProvenance(
        isSigned: Boolean,
    ): PackageInstallProvenance =
        PackageInstallProvenance(
            source =
                PackageInstallSource.LOCAL_FILE,
            sourceReference =
                "fixture-package.osp",
            signatureState =
                if (isSigned) {
                    PackageSignatureState.INVALID
                } else {
                    PackageSignatureState.UNSIGNED
                },
            unsignedWarningAcknowledged =
                !isSigned,
        )

    private companion object {
        const val FIXTURE_PLUGIN_ID =
            "community.fixture"

        const val FIXTURE_VERSION =
            "1.0.0"

        val FIXTURE_PACKAGE_BYTES =
            "fixture-package"
                .encodeToByteArray()

        const val FIXTURE_PACKAGE_SHA_256 =
            "1d58907e64cc82808cc22f3525edeec6a73fc1521d6b951b24b33b01f687dac4"
    }
}

private class RecordingArchiveInspector(
    private val inspection:
        PluginPackageInspection,
) : PackageArchiveInspector {

    var inspectionCalls: Int = 0
        private set

    override fun inspect(
        packageBytes: ByteArray,
    ): AppResult<PluginPackageInspection> {
        inspectionCalls += 1

        return AppResult.Success(
            inspection,
        )
    }
}

private class RecordingPackageSignatureVerifier(
    private val verificationResult:
        PackageSignatureDecision,
) : PackageSignatureVerifier {

    var verificationCalls: Int = 0
        private set

    override fun verify(
        metadata: PluginPackageMetadata,
    ): PackageSignatureDecision {
        verificationCalls += 1

        return verificationResult
    }
}
