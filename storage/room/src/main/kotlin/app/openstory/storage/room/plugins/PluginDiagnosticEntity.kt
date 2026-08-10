package app.openstory.storage.room.plugins

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plugin_diagnostics",
    indices = [Index("plugin_id"), Index("occurred_at_epoch_millis")],
)
internal data class PluginDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    val code: String,
    val operation: String?,
    @ColumnInfo(name = "occurred_at_epoch_millis") val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "safe_detail") val safeDetail: String?,
)
