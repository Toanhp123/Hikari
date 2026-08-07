package app.openstory.database.repository

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.database.OpenStoryDatabase
import app.openstory.database.dao.PluginStateDao
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity
import app.openstory.plugin.host.registry.ActivatedPlugin
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginActivation
import app.openstory.plugin.host.registry.PluginRegistration

class RoomPluginRegistry internal constructor(
    private val dao: PluginStateDao,
    private val clock: Clock = SystemClock,
) : MutablePluginRegistry {

    constructor(
        database: OpenStoryDatabase,
        clock: Clock = SystemClock,
    ) : this(
        dao =
            database.pluginStateDao(),
        clock =
            clock,
    )

    override suspend fun find(
        pluginId: String,
    ): PluginRegistration? =
        dao.find(pluginId)
            ?.toRegistration()

    override suspend fun activate(
        activation: PluginActivation,
    ): AppResult<ActivatedPlugin> =
        storageWrite {
            val nowEpochMillis =
                clock.nowEpochMillis()

            val activatedState =
                dao.activate(
                    version =
                        activation.toVersionEntity(
                            installedAtEpochMillis =
                                nowEpochMillis,
                        ),
                    updatedAtEpochMillis =
                        nowEpochMillis,
                )

            ActivatedPlugin(
                pluginId =
                    activation.pluginId,
                version =
                    activation.version,
                location =
                    activation.location,
                enabled =
                    activatedState.enabled,
            )
        }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> =
        storageWrite {
            val updatedRows =
                dao.updateEnabled(
                    pluginId =
                        pluginId,
                    enabled =
                        enabled,
                    updatedAtEpochMillis =
                        clock.nowEpochMillis(),
                )

            check(updatedRows == 1) {
                "Expected an installed plugin registration."
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> storageWrite(
        block: suspend () -> T,
    ): AppResult<T> =
        try {
            AppResult.Success(
                block(),
            )
        } catch (exception: Exception) {
            if (
                exception is
                java.util.concurrent.CancellationException
            ) {
                throw exception
            }

            AppResult.Failure(
                AppError.Storage(
                    code =
                        "storage.plugin_registry_write_failed",
                    retryable =
                        true,
                ),
            )
        }
}

private fun PluginActivation.toVersionEntity(
    installedAtEpochMillis: Long,
): PluginVersionEntity =
    PluginVersionEntity(
        pluginId =
            pluginId,
        version =
            version,
        packageSha256 =
            packageSha256,
        location =
            location,
        trustSignatureState =
            signatureState,
        signerKeyId =
            signerKeyId,
        signerFingerprintSha256 =
            signerFingerprintSha256,
        installSource =
            installSource,
        sourceReference =
            sourceReference,
        unsignedWarningAcknowledged =
            unsignedWarningAcknowledged,
        acceptedCapabilities =
            acceptedCapabilities
                .sorted()
                .joinToString(
                    separator = ",",
                ),
        installedAtEpochMillis =
            installedAtEpochMillis,
    )

private fun PluginStateEntity.toRegistration():
    PluginRegistration =
    PluginRegistration(
        pluginId =
            pluginId,
        enabled =
            enabled,
        activeVersion =
            activeVersion,
        previousVersion =
            previousVersion,
    )
