package app.openstory.database.repository

import app.openstory.common.id.PluginId
import app.openstory.database.OpenStoryDatabase
import app.openstory.database.dao.PluginDiagnosticDao
import app.openstory.database.dao.PluginDiagnosticEntity
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink

class RoomPluginDiagnosticsSink internal constructor(
    private val dao: PluginDiagnosticDao,
) : PluginDiagnosticsSink {
    constructor(database: OpenStoryDatabase) : this(database.pluginDiagnosticDao())

    override suspend fun record(event: PluginDiagnosticEvent) {
        dao.record(event.toEntity(), PER_PLUGIN_LIMIT, GLOBAL_LIMIT)
    }

    override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> =
        dao.recent(pluginId.value, limit).map(PluginDiagnosticEntity::toEvent)

    private companion object {
        const val PER_PLUGIN_LIMIT = 100
        const val GLOBAL_LIMIT = 1_000
    }
}

private fun PluginDiagnosticEvent.toEntity() = PluginDiagnosticEntity(
    pluginId = pluginId.value,
    version = safeDetail.orEmpty(),
    operation = operation.orEmpty(),
    outcome = "RUNTIME",
    errorCode = code,
    durationMillis = 0L,
    recordedAtEpochMillis = occurredAtEpochMillis,
    responseStatusCategory = null,
    retryAfterMillis = null,
)

private fun PluginDiagnosticEntity.toEvent() = PluginDiagnosticEvent(
    pluginId = PluginId(pluginId),
    code = requireNotNull(errorCode),
    operation = operation.ifBlank { null },
    occurredAtEpochMillis = recordedAtEpochMillis,
    safeDetail = version.ifBlank { null },
)
