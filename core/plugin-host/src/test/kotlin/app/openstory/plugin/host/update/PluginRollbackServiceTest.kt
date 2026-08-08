package app.openstory.plugin.host.update

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.host.install.InstalledPluginPackageLookup
import app.openstory.plugin.host.install.PackageSignatureDecision
import app.openstory.plugin.host.install.StagedPluginPackage
import app.openstory.plugin.host.registry.ActivatedPlugin
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginActivation
import app.openstory.plugin.host.registry.PluginRegistration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginRollbackServiceTest {
    @Test
    fun failedActivationKeepsCurrentVersionAndInstalledBytes() = runTest {
        val lookup = RecordingPackageLookup()
        val registry = FailingRollbackRegistry()
        val service = PluginRollbackService(lookup, registry)

        val result = service.rollback(PLUGIN_ID)

        assertIs<AppResult.Failure>(result)
        assertEquals("2.0.0", registry.registration.activeVersion)
        assertEquals(setOf("1.0.0", "2.0.0"), lookup.installedVersions)
    }
}

private class RecordingPackageLookup : InstalledPluginPackageLookup {
    val installedVersions = mutableSetOf("1.0.0", "2.0.0")

    override suspend fun findInstalled(
        pluginId: String,
        version: String,
    ): AppResult<StagedPluginPackage?> = AppResult.Success(
        version.takeIf(installedVersions::contains)?.let {
            StagedPluginPackage(
                pluginId = pluginId,
                version = it,
                location = "plugins/$pluginId/$it",
                packageSha256 = "a".repeat(64),
                signatureDecision = PackageSignatureDecision.unsigned(),
                provenance = PackageInstallProvenance(
                    source = PackageInstallSource.LOCAL_FILE,
                    sourceReference = "fixture-$it.osp",
                    signatureState = PackageSignatureState.UNSIGNED,
                    unsignedWarningAcknowledged = true,
                ),
            )
        },
    )
}

private class FailingRollbackRegistry : MutablePluginRegistry {
    var registration = PluginRegistration(
        pluginId = PLUGIN_ID,
        enabled = true,
        activeVersion = "2.0.0",
        previousVersion = "1.0.0",
    )
        private set

    override suspend fun find(pluginId: String): PluginRegistration = registration

    override suspend fun activate(
        activation: PluginActivation,
    ): AppResult<ActivatedPlugin> = AppResult.Failure(
        AppError.Storage("storage.plugin_registry_write_failed", retryable = true),
    )

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> = error("Rollback must preserve enabled state.")
}

private const val PLUGIN_ID = "community.fixture"
