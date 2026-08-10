package app.openstory.storage.room.plugins

import app.openstory.common.id.PluginId
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.storage.room.OpenStoryDatabase

class RoomPluginDiagnosticsSink internal constructor(
    private val dao: PluginDiagnosticDao,
) : PluginDiagnosticsSink {
    constructor(database: OpenStoryDatabase) : this(database.pluginDiagnosticDao())

    override suspend fun record(event: PluginDiagnosticEvent) {
        dao.record(event.toEntity(), PER_PLUGIN_LIMIT, GLOBAL_LIMIT)
    }

    override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> {
        require(limit > 0) { "Diagnostic limit must be positive" }
        return dao.recent(pluginId.value, limit).map(PluginDiagnosticEntity::toEvent)
    }

    private companion object {
        const val PER_PLUGIN_LIMIT = 100
        const val GLOBAL_LIMIT = 1_000
    }
}

private fun PluginDiagnosticEvent.toEntity() = PluginDiagnosticEntity(
    pluginId = pluginId.value,
    code = code,
    operation = operation,
    occurredAtEpochMillis = occurredAtEpochMillis,
    safeDetail = safeDetail,
)

private fun PluginDiagnosticEntity.toEvent() = PluginDiagnosticEvent(
    pluginId = PluginId(pluginId),
    code = code,
    operation = operation,
    occurredAtEpochMillis = occurredAtEpochMillis,
    safeDetail = safeDetail,
)
