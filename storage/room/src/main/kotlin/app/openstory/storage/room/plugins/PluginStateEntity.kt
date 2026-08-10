package app.openstory.storage.room.plugins

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_state")
internal data class PluginStateEntity(
    @PrimaryKey @ColumnInfo(name = "plugin_id") val pluginId: String,
    val enabled: Boolean,
    @ColumnInfo(name = "active_version") val activeVersion: String,
    @ColumnInfo(name = "previous_version") val previousVersion: String?,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)
