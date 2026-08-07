package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.host.registry.MutablePluginRegistry

interface InstalledPluginPackageLookup {

    suspend fun findInstalled(
        pluginId: String,
        version: String,
    ): AppResult<StagedPluginPackage?>
}

class PluginRollbackManager(
    private val packageLookup:
        InstalledPluginPackageLookup,
    private val registry:
        MutablePluginRegistry,
) {

    suspend fun rollback(
        pluginId: String,
    ): AppResult<InstalledPlugin> {
        val registration =
            registry.find(
                pluginId,
            )

        val previousVersion =
            registration?.previousVersion
                ?: return rollbackUnavailable()

        return when (
            val lookupResult =
                packageLookup.findInstalled(
                    pluginId =
                        pluginId,
                    version =
                        previousVersion,
                )
        ) {
            is AppResult.Failure ->
                lookupResult

            is AppResult.Success ->
                activateInstalledPackage(
                    pluginId =
                        pluginId,
                    previousVersion =
                        previousVersion,
                    installedPackage =
                        lookupResult.value,
                )
        }
    }

    private suspend fun activateInstalledPackage(
        pluginId: String,
        previousVersion: String,
        installedPackage:
            StagedPluginPackage?,
    ): AppResult<InstalledPlugin> =
        when {
            installedPackage == null ->
                rollbackPackageMissing()

            installedPackage.pluginId != pluginId ||
                installedPackage.version != previousVersion ->
                rollbackPackageMismatch()

            else -> when (
                val activation = registry.activate(
                    installedPackage.toActivation(),
                )
            ) {
                is AppResult.Failure -> activation
                is AppResult.Success -> AppResult.Success(
                    activation.value.toInstalledPlugin(),
                )
            }
        }
}

private fun rollbackUnavailable():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.rollback_unavailable",
            retryable = false,
        ),
    )

private fun rollbackPackageMissing():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.rollback_package_missing",
            retryable = false,
        ),
    )

private fun rollbackPackageMismatch():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.rollback_package_mismatch",
            retryable = false,
        ),
    )
