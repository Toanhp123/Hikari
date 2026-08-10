package app.openstory.storage.room.plugins

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "plugin_versions",
    primaryKeys = ["plugin_id", "version"],
    foreignKeys = [
        ForeignKey(
            entity = PluginStateEntity::class,
            parentColumns = ["plugin_id"],
            childColumns = ["plugin_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index("plugin_id")],
)
internal data class PluginVersionEntity(
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    val version: String,
    @ColumnInfo(name = "package_location") val packageLocation: String,
    val sha256: String,
    @ColumnInfo(name = "signer_fingerprint") val signerFingerprint: String?,
    val services: Set<String>,
    @ColumnInfo(name = "accepted_network_hosts") val acceptedNetworkHosts: Set<String>,
    @ColumnInfo(name = "installed_at_epoch_millis") val installedAtEpochMillis: Long,
)
