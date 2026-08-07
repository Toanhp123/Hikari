package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.host.registry.ActivatedPlugin
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginActivation
import app.openstory.plugin.host.registry.PluginRegistration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginRollbackManagerTest {

    @Test
    fun previousInstalledVersionCanBeRolledBack() =
        runTest {
            val packageLookup =
                RecordingInstalledPackageLookup()

            val registry =
                RecordingRollbackRegistry()

            val manager =
                PluginRollbackManager(
                    packageLookup =
                        packageLookup,
                    registry =
                        registry,
                )

            val success =
                assertIs<AppResult.Success<*>>(
                    manager.rollback(
                        FIXTURE_PLUGIN_ID,
                    ),
                )

            val installedPlugin =
                assertIs<InstalledPlugin>(
                    success.value,
                )

            assertEquals(
                expected =
                    FIXTURE_PREVIOUS_VERSION,
                actual =
                    installedPlugin.version,
            )

            assertEquals(
                expected =
                    FIXTURE_PREVIOUS_VERSION,
                actual =
                    packageLookup.requestedVersion,
            )

            assertEquals(
                expected =
                    FIXTURE_PREVIOUS_VERSION,
                actual =
                    registry.activatedVersion,
            )
        }
}

private class RecordingInstalledPackageLookup :
    InstalledPluginPackageLookup {

    var requestedVersion: String? = null
        private set

    override suspend fun findInstalled(
        pluginId: String,
        version: String,
    ): AppResult<StagedPluginPackage?> {
        requestedVersion =
            version

        return AppResult.Success(
            StagedPluginPackage(
                pluginId =
                    pluginId,
                version =
                    version,
                location =
                    "plugins/$pluginId/$version",
                packageSha256 =
                    "0".repeat(64),
                signatureDecision =
                    PackageSignatureDecision
                        .unsigned(),
                provenance =
                    fixtureProvenance(),
            ),
        )
    }
}

private class RecordingRollbackRegistry :
    MutablePluginRegistry {

    var activatedVersion: String? = null
        private set

    override suspend fun find(
        pluginId: String,
    ): PluginRegistration =
        PluginRegistration(
            pluginId =
                pluginId,
            enabled =
                true,
            activeVersion =
                FIXTURE_ACTIVE_VERSION,
            previousVersion =
                FIXTURE_PREVIOUS_VERSION,
        )

    override suspend fun activate(
        activation: PluginActivation,
    ): AppResult<ActivatedPlugin> {
        activatedVersion =
            activation.version

        return AppResult.Success(
            ActivatedPlugin(
                pluginId =
                    activation.pluginId,
                version =
                    activation.version,
                location =
                    activation.location,
                enabled =
                    true,
            ),
        )
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> =
        error(
            "Rollback must not change enabled state directly.",
        )
}

private fun fixtureProvenance():
    PackageInstallProvenance =
    PackageInstallProvenance(
        source =
            PackageInstallSource.LOCAL_FILE,
        sourceReference =
            "fixture-1.0.0.osp",
        signatureState =
            PackageSignatureState.UNSIGNED,
        unsignedWarningAcknowledged =
            true,
    )

private const val FIXTURE_PLUGIN_ID =
    "community.fixture"

private const val FIXTURE_ACTIVE_VERSION =
    "2.0.0"

private const val FIXTURE_PREVIOUS_VERSION =
    "1.0.0"
