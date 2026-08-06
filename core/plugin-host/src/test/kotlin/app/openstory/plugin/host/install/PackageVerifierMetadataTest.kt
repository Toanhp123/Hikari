package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PackageVerifierMetadataTest {

    @Test
    fun manifestIdentityMustMatchExternalPackageMetadata() {
        val verifier =
            PackageVerifier(
                archiveInspector =
                    PackageArchiveInspector {
                        AppResult.Success(
                            PluginPackageInspection(
                                pluginId =
                                    MANIFEST_PLUGIN_ID,
                                version =
                                    FIXTURE_VERSION,
                                entries =
                                    validArchiveEntries(),
                                declaredExecutableEntries =
                                    setOf("main.js"),
                                declaredCapabilities =
                                    emptySet(),
                            ),
                        )
                    },
            )

        val failure =
            assertIs<AppResult.Failure>(
                verifier.verify(
                    InstallRequest(
                        packageBytes =
                            FIXTURE_PACKAGE_BYTES,
                        metadata =
                            PluginPackageMetadata(
                                pluginId =
                                    METADATA_PLUGIN_ID,
                                version =
                                    FIXTURE_VERSION,
                                exactPackageSha256 =
                                    FIXTURE_PACKAGE_SHA_256,
                                signature =
                                    null,
                            ),
                        provenance =
                            fixtureProvenance(),
                    ),
                ),
            )

        assertEquals(
            expected =
                "plugin.package_metadata_mismatch",
            actual =
                failure.error.code,
        )
    }
}

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

private fun fixtureProvenance():
    PackageInstallProvenance =
    PackageInstallProvenance(
        source =
            PackageInstallSource.LOCAL_FILE,
        sourceReference =
            "fixture-package.osp",
        signatureState =
            PackageSignatureState.UNSIGNED,
        unsignedWarningAcknowledged =
            true,
    )

private const val METADATA_PLUGIN_ID =
    "community.expected"

private const val MANIFEST_PLUGIN_ID =
    "community.actual"

private const val FIXTURE_VERSION =
    "1.0.0"

private val FIXTURE_PACKAGE_BYTES =
    "fixture-package"
        .encodeToByteArray()

private const val FIXTURE_PACKAGE_SHA_256 =
    "1d58907e64cc82808cc22f3525edeec6a73fc1521d6b951b24b33b01f687dac4"
