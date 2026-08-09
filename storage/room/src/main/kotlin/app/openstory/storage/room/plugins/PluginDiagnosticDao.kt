package app.openstory.storage.room.plugins

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal abstract class PluginDiagnosticDao {
    @Insert
    protected abstract suspend fun insert(event: PluginDiagnosticEntity)

    @Query("SELECT * FROM plugin_diagnostics WHERE plugin_id = :pluginId ORDER BY occurred_at_epoch_millis DESC, id DESC LIMIT :limit")
    abstract suspend fun recent(pluginId: String, limit: Int): List<PluginDiagnosticEntity>

    @Query("DELETE FROM plugin_diagnostics WHERE id IN (SELECT id FROM plugin_diagnostics WHERE plugin_id = :pluginId ORDER BY occurred_at_epoch_millis DESC, id DESC LIMIT -1 OFFSET :limit)")
    protected abstract suspend fun trimPlugin(pluginId: String, limit: Int)

    @Query("DELETE FROM plugin_diagnostics WHERE id IN (SELECT id FROM plugin_diagnostics ORDER BY occurred_at_epoch_millis DESC, id DESC LIMIT -1 OFFSET :limit)")
    protected abstract suspend fun trimGlobal(limit: Int)

    @Transaction
    open suspend fun record(event: PluginDiagnosticEntity, perPluginLimit: Int, globalLimit: Int) {
        insert(event)
        trimPlugin(event.pluginId, perPluginLimit)
        trimGlobal(globalLimit)
    }
}
