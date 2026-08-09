package app.openstory.database.repository

import app.openstory.common.id.PluginId
import app.openstory.database.dao.PluginDiagnosticDao
import app.openstory.database.dao.PluginDiagnosticEntity
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoomPluginDiagnosticsSinkTest {
    @Test
    fun diagnosticsRoundTripOnlyRedactedFields() = runTest {
        val dao = RecordingPluginDiagnosticDao()
        val sink = RoomPluginDiagnosticsSink(dao)

        sink.record(
            PluginDiagnosticEvent(
                pluginId = PluginId("org.example.plugin"),
                code = "plugin.execution_failed",
                operation = "catalog.home",
                occurredAtEpochMillis = 100L,
                safeDetail = "safe marker",
            ),
        )

        val restored = sink.recent(PluginId("org.example.plugin"), 10).single()
        assertEquals("safe marker", restored.safeDetail)
        assertFalse(restored.toString().contains("secret-cookie"))
    }
}

private class RecordingPluginDiagnosticDao : PluginDiagnosticDao() {
    private val rows = mutableListOf<PluginDiagnosticEntity>()

    override suspend fun insert(diagnostic: PluginDiagnosticEntity) { rows += diagnostic }
    override suspend fun trimPlugin(pluginId: String, limit: Int) {
        while (rows.count { it.pluginId == pluginId } > limit) rows.removeAt(0)
    }
    override suspend fun trimGlobal(limit: Int) { while (rows.size > limit) rows.removeAt(0) }
    override suspend fun recent(limit: Int) = rows.takeLast(limit).reversed()
    override suspend fun recent(pluginId: String, limit: Int) = rows
        .filter { it.pluginId == pluginId }
        .takeLast(limit)
        .reversed()
}
