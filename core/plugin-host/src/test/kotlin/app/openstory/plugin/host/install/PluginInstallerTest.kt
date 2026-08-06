package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginRegistration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginInstallerTest {

    @Test
    fun invalidChecksumLeavesRegistryUnchanged() =
        runTest {
            val storage =
                RecordingPluginPackageStorage()

            val registry =
                RecordingPluginRegistry()

            val installer =
                PluginInstaller(
                    verifier =
                        PackageVerifier(
                            archiveInspector =
                                validArchiveInspector(),
                        ),
                    storage = storage,
                    registry = registry,
                )

            val result =
                installer.install(
                    fixtureInstallRequest(
                        version =
                            "1.0.0",
                        sourceReference =
                            "fixture-invalid-checksum.osp",
                        exactPackageSha256 =
                            "0".repeat(64),
                    ),
                )

            assertTrue(
                result is AppResult.Failure,
            )

            assertNull(
                registry.find(
                    pluginId =
                        FIXTURE_PLUGIN_ID,
                ),
            )

            assertEquals(
                expected = 0,
                actual = storage.stageCalls,
                message =
                    "Invalid package bytes must not enter staging.",
            )

            assertEquals(
                expected = 0,
                actual = registry.activationCalls,
                message =
                    "Checksum failure must not change the registry.",
            )
        }

    @Test
    fun activationFailureRemovesStagingDirectory() =
        runTest {
            val storage =
                SuccessfulStagingStorage()

            val registry =
                FailingActivationRegistry()

            val installer =
                PluginInstaller(
                    verifier =
                        PackageVerifier(
                            archiveInspector =
                                validArchiveInspector(),
                        ),
                    storage = storage,
                    registry = registry,
                )

            val result =
                installer.install(
                    fixtureInstallRequest(
                        version =
                            "1.0.0",
                        sourceReference =
                            "fixture-valid-checksum.osp",
                    ),
                )

            assertTrue(
                result is AppResult.Failure,
            )

            assertEquals(
                expected =
                    listOf(
                        "staging/community.fixture/1.0.0",
                    ),
                actual =
                    storage.removedLocations,
                message =
                    "Failed activation must remove the staged package.",
            )

            assertNull(
                registry.find(
                    pluginId =
                        FIXTURE_PLUGIN_ID,
                ),
            )

            assertEquals(
                expected = 1,
                actual = registry.activationCalls,
            )
        }
    @Test
    fun lowerVersionIsRejectedBeforeStaging() =
        runTest {
            val storage =
                DowngradeRecordingStorage()

            val registry =
                ExistingVersionRegistry()

            val installer =
                PluginInstaller(
                    verifier =
                        PackageVerifier(
                            archiveInspector =
                                downgradeArchiveInspector(),
                        ),
                    storage = storage,
                    registry = registry,
                )

            val failure =
                assertIs<AppResult.Failure>(
                    installer.install(
                        downgradeInstallRequest(),
                    ),
                )

            assertEquals(
                expected =
                    "plugin.package_downgrade_denied",
                actual =
                    failure.error.code,
            )

            assertEquals(
                expected = 0,
                actual = storage.stageCalls,
                message =
                    "Downgrade must be rejected before staging.",
            )

            assertEquals(
                expected = 0,
                actual =
                    registry.activationCalls,
                message =
                    "Downgrade must not change the registry.",
            )
        }
    @Test
    fun higherVersionStagesAndActivates() =
        runTest {
            val storage =
                UpgradeRecordingStorage()

            val registry =
                UpgradeRecordingRegistry()

            val result =
                upgradeInstaller(
                    storage = storage,
                    registry = registry,
                ).install(
                    upgradeInstallRequest(),
                )

            val success =
                assertIs<AppResult.Success<*>>(
                    result,
                )

            val installedPlugin =
                assertIs<InstalledPlugin>(
                    success.value,
                )

            assertEquals(
                expected = "2.0.0",
                actual = installedPlugin.version,
            )

            assertEquals(
                expected = 1,
                actual = storage.stageCalls,
            )

            assertEquals(
                expected = 1,
                actual = registry.activationCalls,
            )
        }
    private companion object {
        const val FIXTURE_PLUGIN_ID =
            "community.fixture"
    }
}

private fun fixtureInstallRequest(
    version: String,
    sourceReference: String,
    exactPackageSha256: String =
        FIXTURE_PACKAGE_SHA_256,
): InstallRequest =
    InstallRequest(
        packageBytes =
            "fixture-package"
                .encodeToByteArray(),
        metadata =
            pluginMetadata(
                version =
                    version,
                exactPackageSha256 =
                    exactPackageSha256,
            ),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.LOCAL_FILE,
                sourceReference =
                    sourceReference,
                signatureState =
                    PackageSignatureState.UNSIGNED,
                unsignedWarningAcknowledged =
                    true,
            ),
    )
private fun pluginMetadata(
    version: String,
    exactPackageSha256: String =
        FIXTURE_PACKAGE_SHA_256,
): PluginPackageMetadata =
    PluginPackageMetadata(
        pluginId =
            "community.fixture",
        version =
            version,
        exactPackageSha256 =
            exactPackageSha256,
        signature =
            null,
    )

private const val FIXTURE_PACKAGE_SHA_256 =
    "1d58907e64cc82808cc22f3525edeec6a73fc1521d6b951b24b33b01f687dac4"
private fun validArchiveInspector():
    PackageArchiveInspector =
    PackageArchiveInspector {
        AppResult.Success(
            PluginPackageInspection(
                pluginId =
                    "community.fixture",
                version = "1.0.0",

                entries =
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
                            path = "main.js",
                            compressedSizeBytes =
                                10L,
                            uncompressedSizeBytes =
                                10L,
                            isSymbolicLink =
                                false,
                            isExecutable =
                                true,
                        ),
                    ),
                declaredExecutableEntries =
                    setOf("main.js"),
                declaredCapabilities =
                    emptySet(),
            ),
        )
    }

private class RecordingPluginPackageStorage :
    PluginPackageStorage {

    var stageCalls: Int = 0
        private set

    override suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage> {
        stageCalls += 1

        error(
            "Package with invalid checksum must not be staged.",
        )
    }

    override suspend fun remove(
        location: String,
    ) {
        error(
            "No staging location should exist after checksum failure.",
        )
    }
}

private class RecordingPluginRegistry :
    MutablePluginRegistry {

    var activationCalls: Int = 0
        private set

    override suspend fun find(
        pluginId: String,
    ): PluginRegistration? =
        null

    override suspend fun activate(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin> {
        activationCalls += 1

        error(
            "Checksum failure must not activate a plugin.",
        )
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> =
        error(
            "Enabled state is not used by this test.",
        )
}
private class SuccessfulStagingStorage :
    PluginPackageStorage {

    val removedLocations =
        mutableListOf<String>()

    override suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage> =
        AppResult.Success(
            StagedPluginPackage(
                pluginId =
                    "community.fixture",
                version = "1.0.0",
                location =
                    "staging/community.fixture/1.0.0",
                packageSha256 =
                    verifiedPackage
                        .packageSha256,
                signatureDecision =
                    verifiedPackage
                        .signatureDecision,
                provenance =
                    verifiedPackage.provenance,
            ),
        )

    override suspend fun remove(
        location: String,
    ) {
        removedLocations += location
    }
}

private class FailingActivationRegistry :
    MutablePluginRegistry {

    var activationCalls: Int = 0
        private set

    override suspend fun find(
        pluginId: String,
    ): PluginRegistration? =
        null

    override suspend fun activate(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin> {
        activationCalls += 1

        return AppResult.Failure(
            AppError.Storage(
                code =
                    "storage.plugin_registry_write_failed",
                retryable = true,
            ),
        )
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> =
        error(
            "Enabled state is not used by this test.",
        )
}
private fun downgradeInstallRequest():
    InstallRequest =
    InstallRequest(
        packageBytes =
            "fixture-package"
                .encodeToByteArray(),
        metadata =
            pluginMetadata(
                version =
                    "1.5.0",
            ),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.LOCAL_FILE,
                sourceReference =
                    "fixture-downgrade.osp",
                signatureState =
                    PackageSignatureState.UNSIGNED,
                unsignedWarningAcknowledged =
                    true,
            ),
    )

private fun downgradeArchiveInspector():
    PackageArchiveInspector =
    PackageArchiveInspector {
        AppResult.Success(
            PluginPackageInspection(
                pluginId =
                    "community.fixture",
                version =
                    "1.5.0",

                entries =
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
                    ),
                declaredExecutableEntries =
                    setOf("main.js"),
                declaredCapabilities =
                    emptySet(),
            ),
        )
    }

private class DowngradeRecordingStorage :
    PluginPackageStorage {

    var stageCalls: Int = 0
        private set

    override suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage> {
        stageCalls += 1

        error(
            "Downgrade must be rejected before staging.",
        )
    }

    override suspend fun remove(
        location: String,
    ) {
        error(
            "Downgrade rejection must not create staging files.",
        )
    }
}

private class ExistingVersionRegistry :
    MutablePluginRegistry {

    var activationCalls: Int = 0
        private set

    override suspend fun find(
        pluginId: String,
    ): PluginRegistration =
        PluginRegistration(
            pluginId = pluginId,
            enabled = true,
            activeVersion = "2.0.0",
            previousVersion = "1.0.0",
        )

    override suspend fun activate(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin> {
        activationCalls += 1

        error(
            "Downgrade must not activate a plugin.",
        )
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> =
        error(
            "Enabled state is not used by this test.",
        )
}
private fun upgradeInstaller(
    storage: PluginPackageStorage,
    registry: MutablePluginRegistry,
): PluginInstaller =
    PluginInstaller(
        verifier =
            PackageVerifier(
                archiveInspector =
                    upgradeArchiveInspector(),
            ),
        storage = storage,
        registry = registry,
    )

private fun upgradeInstallRequest():
    InstallRequest =
    InstallRequest(
        packageBytes =
            "fixture-package"
                .encodeToByteArray(),
        metadata =
            pluginMetadata(
                version =
                    "2.0.0",
            ),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.LOCAL_FILE,
                sourceReference =
                    "fixture-upgrade.osp",
                signatureState =
                    PackageSignatureState.UNSIGNED,
                unsignedWarningAcknowledged =
                    true,
            ),
    )

private fun upgradeArchiveInspector():
    PackageArchiveInspector =
    PackageArchiveInspector {
        AppResult.Success(
            PluginPackageInspection(
                pluginId =
                    "community.fixture",
                version = "2.0.0",

                entries =
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
                            path = "main.js",
                            compressedSizeBytes =
                                10L,
                            uncompressedSizeBytes =
                                10L,
                            isSymbolicLink =
                                false,
                            isExecutable =
                                true,
                        ),
                    ),
                declaredExecutableEntries =
                    setOf("main.js"),
                declaredCapabilities =
                    emptySet(),
            ),
        )
    }

private class UpgradeRecordingStorage :
    PluginPackageStorage {

    var stageCalls: Int = 0
        private set

    override suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage> {
        stageCalls += 1

        return AppResult.Success(
            StagedPluginPackage(
                pluginId =
                    verifiedPackage.pluginId,
                version =
                    verifiedPackage.version,
                location =
                    "plugins/${verifiedPackage.pluginId}/${verifiedPackage.version}",
                packageSha256 =
                    verifiedPackage
                        .packageSha256,
                signatureDecision =
                    verifiedPackage
                        .signatureDecision,
                provenance =
                    verifiedPackage.provenance,
            ),
        )
    }

    override suspend fun remove(
        location: String,
    ) {
        error(
            "Successful upgrade must not remove the staged package.",
        )
    }
}

private class UpgradeRecordingRegistry :
    MutablePluginRegistry {

    var activationCalls: Int = 0
        private set

    override suspend fun find(
        pluginId: String,
    ): PluginRegistration =
        PluginRegistration(
            pluginId = pluginId,
            enabled = true,
            activeVersion = "1.5.0",
            previousVersion = "1.0.0",
        )

    override suspend fun activate(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin> {
        activationCalls += 1

        return AppResult.Success(
            InstalledPlugin(
                pluginId =
                    stagedPackage.pluginId,
                version =
                    stagedPackage.version,
                location =
                    stagedPackage.location,
                enabled = true,
            ),
        )
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> =
        error(
            "Enabled state is not used by this test.",
        )
}
