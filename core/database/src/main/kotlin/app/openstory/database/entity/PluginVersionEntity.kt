package app.openstory.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "plugin_versions",
    primaryKeys = [
        "plugin_id",
        "version",
    ],
)
internal data class PluginVersionEntity(
    @ColumnInfo(name = "plugin_id")
    val pluginId: String,
    val version: String,
    @ColumnInfo(name = "package_sha256")
    val packageSha256: String,
    val location: String,
    @ColumnInfo(name = "trust_signature_state")
    val trustSignatureState: String,
    @ColumnInfo(name = "signer_key_id")
    val signerKeyId: String?,
    @ColumnInfo(name = "signer_fingerprint_sha256")
    val signerFingerprintSha256: String?,
    @ColumnInfo(name = "install_source")
    val installSource: String,
    @ColumnInfo(name = "source_reference")
    val sourceReference: String,
    @ColumnInfo(name = "unsigned_warning_acknowledged")
    val unsignedWarningAcknowledged: Boolean,
    @ColumnInfo(name = "accepted_capabilities")
    val acceptedCapabilities: String,
    @ColumnInfo(name = "installed_at_epoch_millis")
    val installedAtEpochMillis: Long,
)
