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

class PackageVerifierRuntimeLayoutTest {
    @Test
    fun mixedRuntimeArchiveIsRejectedBeforeStaging() {
        val inspection = PluginPackageInspection(
            pluginId = FIXTURE_PLUGIN_ID,
            version = FIXTURE_VERSION,
            entries = listOf(
                archiveEntry("manifest.json"),
                archiveEntry("selector.json"),
                archiveEntry("main.js"),
            ),
            declaredExecutableEntries = setOf("selector.json"),
            declaredCapabilities = emptySet(),
        )
        val verifier = PackageVerifier(
            archiveInspector = PackageArchiveInspector {
                AppResult.Success(inspection)
            },
        )

        val failure = assertIs<AppResult.Failure>(
            verifier.verify(fixtureRequest()),
        )

        assertEquals("plugin.package_layout_invalid", failure.error.code)
    }

    private fun fixtureRequest(): InstallRequest = InstallRequest(
        packageBytes = FIXTURE_PACKAGE_BYTES,
        metadata = PluginPackageMetadata(
            pluginId = FIXTURE_PLUGIN_ID,
            version = FIXTURE_VERSION,
            exactPackageSha256 = FIXTURE_PACKAGE_SHA_256,
            signature = null,
        ),
        provenance = PackageInstallProvenance(
            source = PackageInstallSource.LOCAL_FILE,
            sourceReference = "fixture-package.osp",
            signatureState = PackageSignatureState.UNSIGNED,
            unsignedWarningAcknowledged = true,
        ),
    )

    private fun archiveEntry(path: String): PackageArchiveEntry =
        PackageArchiveEntry(
            path = path,
            compressedSizeBytes = 10L,
            uncompressedSizeBytes = 10L,
            isSymbolicLink = false,
            isExecutable = false,
        )

    private companion object {
        const val FIXTURE_PLUGIN_ID = "community.fixture"
        const val FIXTURE_VERSION = "1.0.0"
        val FIXTURE_PACKAGE_BYTES = "fixture-package".encodeToByteArray()
        const val FIXTURE_PACKAGE_SHA_256 =
            "1d58907e64cc82808cc22f3525edeec6a73fc1521d6b951b24b33b01f687dac4"
    }
}
