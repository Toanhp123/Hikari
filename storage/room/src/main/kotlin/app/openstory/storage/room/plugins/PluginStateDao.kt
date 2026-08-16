package app.openstory.storage.room.plugins

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
internal abstract class PluginStateDao {
    @Query("SELECT * FROM plugin_state WHERE plugin_id = :pluginId")
    abstract suspend fun find(pluginId: String): PluginStateEntity?

    @Query("SELECT * FROM plugin_state ORDER BY plugin_id")
    abstract suspend fun all(): List<PluginStateEntity>

    @Query("SELECT * FROM plugin_versions WHERE plugin_id = :pluginId AND version = :version")
    abstract suspend fun findVersion(pluginId: String, version: String): PluginVersionEntity?

    @Query(
        "SELECT versions.* FROM plugin_versions AS versions " +
            "INNER JOIN plugin_state AS state ON state.plugin_id = versions.plugin_id " +
            "WHERE versions.version = state.active_version OR versions.version = state.previous_version " +
            "ORDER BY versions.plugin_id, versions.version",
    )
    abstract suspend fun stateVersions(): List<PluginVersionEntity>

    @Transaction
    open suspend fun snapshot() = PluginStateSnapshot(
        states = all(),
        versions = stateVersions(),
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertVersion(version: PluginVersionEntity): Long

    @Upsert
    protected abstract suspend fun upsertState(state: PluginStateEntity)

    @Transaction
    open suspend fun replace(state: PluginStateEntity, versions: List<PluginVersionEntity>) {
        upsertState(state)
        versions.forEach { version ->
            val inserted = insertVersion(version)
            if (inserted == INSERT_CONFLICT) {
                val existing = findVersion(version.pluginId, version.version)
                val comparable = version.copy(
                    installedAtEpochMillis = existing?.installedAtEpochMillis
                        ?: version.installedAtEpochMillis,
                )
                check(existing == comparable) {
                    "Installed plugin version metadata is immutable."
                }
            }
        }
    }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}

internal data class PluginStateSnapshot(
    val states: List<PluginStateEntity>,
    val versions: List<PluginVersionEntity>,
)
