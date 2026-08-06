package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransactionalPluginPackageStorageTrustDecisionTest {

    @Test
    fun verifiedSignerDecisionIsRestoredAfterStorageIsRecreated() =
        runTest {
            val rootDirectory =
                Files.createTempDirectory(
                    "openstory-plugin-trust-",
                )

            val expectedDecision =
                verifiedSignatureDecision()

            try {
                val staged =
                    stageVerifiedPackage(
                        rootDirectory =
                            rootDirectory,
                        signatureDecision =
                            expectedDecision,
                    )

                val found =
                    findInstalledPackage(
                        rootDirectory,
                    )

                assertRestoredPackage(
                    staged =
                        staged,
                    found =
                        found,
                    expectedDecision =
                        expectedDecision,
                )
            } finally {
                rootDirectory.deleteRecursively()
            }
        }
}

private fun verifiedSignatureDecision():
    PackageSignatureDecision =
    PackageSignatureDecision.verified(
        signerKeyId =
            FIXTURE_SIGNER_KEY_ID,
        signerFingerprintSha256 =
            FIXTURE_SIGNER_FINGERPRINT,
    )

private suspend fun stageVerifiedPackage(
    rootDirectory: Path,
    signatureDecision:
        PackageSignatureDecision,
): StagedPluginPackage =
    assertIs<StagedPluginPackage>(
        assertIs<AppResult.Success<*>>(
            pluginStorage(
                rootDirectory,
            ).stage(
                verifiedPackage(
                    signatureDecision,
                ),
            ),
        ).value,
    )

private suspend fun findInstalledPackage(
    rootDirectory: Path,
): StagedPluginPackage =
    assertIs<StagedPluginPackage>(
        assertIs<AppResult.Success<*>>(
            pluginStorage(
                rootDirectory,
            ).findInstalled(
                pluginId =
                    FIXTURE_PLUGIN_ID,
                version =
                    FIXTURE_VERSION,
            ),
        ).value,
    )

private fun assertRestoredPackage(
    staged: StagedPluginPackage,
    found: StagedPluginPackage,
    expectedDecision:
        PackageSignatureDecision,
) {
    assertEquals(
        expected =
            staged.location,
        actual =
            found.location,
    )

    assertEquals(
        expected =
            FIXTURE_PACKAGE_SHA_256,
        actual =
            found.packageSha256,
    )

    assertEquals(
        expected =
            expectedDecision,
        actual =
            found.signatureDecision,
    )

    assertEquals(
        expected =
            setOf(
                PluginCapability.NETWORK,
            ),
        actual =
            found.acceptedCapabilities,
    )
}

private fun pluginStorage(
    rootDirectory: Path,
): TransactionalPluginPackageStorage =
    TransactionalPluginPackageStorage(
        rootDirectory =
            rootDirectory,
        extractor =
            PluginPackageExtractor {
                    _,
                    destination,
                ->
                Files.createDirectories(
                    destination,
                )

                Files.writeString(
                    destination.resolve(
                        "manifest.json",
                    ),
                    "{}",
                )

                AppResult.Success(
                    Unit,
                )
            },
        stagingDirectoryName = {
            "fixture-staging"
        },
    )

private fun verifiedPackage(
    signatureDecision:
        PackageSignatureDecision,
): VerifiedPluginPackage =
    VerifiedPluginPackage(
        packageBytes =
            "verified-package"
                .encodeToByteArray(),
        packageSha256 =
            FIXTURE_PACKAGE_SHA_256,
        pluginId =
            FIXTURE_PLUGIN_ID,
        version =
            FIXTURE_VERSION,
        signatureDecision =
            signatureDecision,
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
        acceptedCapabilities =
            setOf(
                PluginCapability.NETWORK,
            ),
    )

private fun Path.deleteRecursively() {
    if (!Files.exists(this)) {
        return
    }

    Files.walk(this).use { paths ->
        paths
            .sorted(
                Comparator.reverseOrder(),
            )
            .forEach { path ->
                path.toFile()
                    .setWritable(
                        true,
                        false,
                    )

                Files.deleteIfExists(
                    path,
                )
            }
    }
}

private const val FIXTURE_PACKAGE_SHA_256 =
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

private const val FIXTURE_PLUGIN_ID =
    "community.fixture"

private const val FIXTURE_VERSION =
    "1.0.0"

private const val FIXTURE_SIGNER_KEY_ID =
    "fixture-author-main"

private const val FIXTURE_SIGNER_FINGERPRINT =
    "abababababababababababababababababababababababababababababababab"
