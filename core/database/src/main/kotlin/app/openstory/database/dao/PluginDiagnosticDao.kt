package app.openstory.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(
    tableName = "plugin_diagnostics",
)
internal data class PluginDiagnosticEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "plugin_id", index = true)
    val pluginId: String,
    val version: String,
    val operation: String,
    val outcome: String,
    @ColumnInfo(name = "error_code")
    val errorCode: String?,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long,
    @ColumnInfo(name = "recorded_at_epoch_millis", index = true)
    val recordedAtEpochMillis: Long,
    @ColumnInfo(name = "response_status_category")
    val responseStatusCategory: String?,
    @ColumnInfo(name = "retry_after_millis")
    val retryAfterMillis: Long?,
)

@Dao
internal abstract class PluginDiagnosticDao {
    @Insert
    protected abstract suspend fun insert(diagnostic: PluginDiagnosticEntity)

    @Query(
        """
        DELETE FROM plugin_diagnostics
        WHERE id IN (
            SELECT id
            FROM plugin_diagnostics
            WHERE plugin_id = :pluginId
            ORDER BY recorded_at_epoch_millis DESC, id DESC
            LIMIT -1 OFFSET :limit
        )
        """,
    )
    protected abstract suspend fun trimPlugin(
        pluginId: String,
        limit: Int,
    )

    @Query(
        """
        DELETE FROM plugin_diagnostics
        WHERE id IN (
            SELECT id
            FROM plugin_diagnostics
            ORDER BY recorded_at_epoch_millis DESC, id DESC
            LIMIT -1 OFFSET :limit
        )
        """,
    )
    protected abstract suspend fun trimGlobal(limit: Int)

    @Query(
        """
        SELECT *
        FROM plugin_diagnostics
        ORDER BY recorded_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun recent(limit: Int): List<PluginDiagnosticEntity>

    @Query(
        """
        SELECT *
        FROM plugin_diagnostics
        WHERE plugin_id = :pluginId
        ORDER BY recorded_at_epoch_millis DESC, id DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun recent(
        pluginId: String,
        limit: Int,
    ): List<PluginDiagnosticEntity>

    @Transaction
    open suspend fun record(
        diagnostic: PluginDiagnosticEntity,
        perPluginLimit: Int,
        globalLimit: Int,
    ) {
        insert(diagnostic)
        trimPlugin(diagnostic.pluginId, perPluginLimit)
        trimGlobal(globalLimit)
    }
}
