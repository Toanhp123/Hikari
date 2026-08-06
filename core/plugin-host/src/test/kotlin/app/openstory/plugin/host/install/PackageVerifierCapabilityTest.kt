package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PackageVerifierCapabilityTest {

    @Test
    fun capabilityNotDeclaredByManifestIsRejected() {
        val packageBytes =
            "fixture-package"
                .encodeToByteArray()

        val result =
            capabilityVerifier(
                declaredCapabilities =
                    emptySet(),
            ).verify(
                capabilityInstallRequest(
                    packageBytes =
                        packageBytes,
                    acceptedCapabilities =
                        setOf(
                            PluginCapability.NETWORK,
                        ),
                ),
            )

        val failure =
            assertIs<AppResult.Failure>(
                result,
            )

        assertEquals(
            expected =
                "plugin.package_capability_not_declared",
            actual =
                failure.error.code,
        )
    }

    @Test
    fun acceptedCapabilityIsPreservedInVerifiedPackage() {
        val packageBytes =
            "fixture-package"
                .encodeToByteArray()

        val result =
            capabilityVerifier(
                declaredCapabilities =
                    setOf(
                        PluginCapability.NETWORK,
                    ),
            ).verify(
                capabilityInstallRequest(
                    packageBytes =
                        packageBytes,
                    acceptedCapabilities =
                        setOf(
                            PluginCapability.NETWORK,
                        ),
                ),
            )

        val success =
            assertIs<AppResult.Success<*>>(
                result,
            )

        val verifiedPackage =
            assertIs<VerifiedPluginPackage>(
                success.value,
            )

        assertEquals(
            expected =
                setOf(
                    PluginCapability.NETWORK,
                ),
            actual =
                verifiedPackage
                    .acceptedCapabilities,
        )
    }
}

private fun capabilityVerifier(
    declaredCapabilities:
        Set<PluginCapability>,
): PackageVerifier =
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
                            declaredCapabilities,
                    ),
                )
            },
    )

private fun capabilityInstallRequest(
    packageBytes: ByteArray,
    acceptedCapabilities:
        Set<PluginCapability>,
): InstallRequest =
    InstallRequest(
        packageBytes =
            packageBytes,
        metadata =
            PluginPackageMetadata(
                pluginId =
                    FIXTURE_PLUGIN_ID,
                version =
                    FIXTURE_VERSION,
                exactPackageSha256 =
                    packageBytes.sha256(),
                signature =
                    null,
            ),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.LOCAL_FILE,
                sourceReference =
                    "fixture-package.osp",
                signatureState =
                    PackageSignatureState.UNSIGNED,
                unsignedWarningAcknowledged =
                    true,
            ),
        acceptedCapabilities =
            acceptedCapabilities,
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

private fun ByteArray.sha256():
    String =
    MessageDigest
        .getInstance(
            "SHA-256",
        )
        .digest(this)
        .joinToString(
            separator = "",
        ) { byte ->
            "%02x".format(
                byte.toInt() and
                    0xff,
            )
        }

private const val FIXTURE_PLUGIN_ID =
    "community.fixture"

private const val FIXTURE_VERSION =
    "1.0.0"
