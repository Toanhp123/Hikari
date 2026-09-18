package app.universalmedia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    primaryKeys = ["media_id"],
)
internal data class MediaRow(
    @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "kind") val kind: String,
)

@Entity(
    tableName = "media_unit",
    primaryKeys = ["unit_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaRow::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["media_id"])],
)
internal data class MediaUnitRow(
    @ColumnInfo(name = "unit_id") val unitId: String,
    @ColumnInfo(name = "media_id") val mediaId: String,
)

@Entity(
    tableName = "consumption_target",
    foreignKeys = [
        ForeignKey(
            entity = MediaRow::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = MediaUnitRow::class,
            parentColumns = ["unit_id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            value = ["media_id"],
            unique = true,
        ), Index(value = ["unit_id"], unique = true),
    ],
)
internal data class TargetRow(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "target_row_id") val targetRowId: Long = 0,
    @ColumnInfo(name = "target_kind") val targetKind: String,
    @ColumnInfo(name = "media_id") val mediaId: String?,
    @ColumnInfo(name = "unit_id") val unitId: String?,
)

@Entity(
    tableName = "source",
    primaryKeys = ["source_id"],
    indices = [Index(value = ["kind"], unique = true)],
)
internal data class SourceRow(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "kind") val kind: String,
)

@Entity(
    tableName = "storage_root",
    primaryKeys = ["root_id"],
    indices = [Index(value = ["authority", "tree_locator"], unique = true)],
)
internal data class RootRow(
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "authority") val authority: String,
    @ColumnInfo(name = "tree_locator") val treeLocator: String,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "access") val access: String,
    @ColumnInfo(name = "observed_at") val observedAt: Long,
)

@Entity(
    tableName = "source_binding",
    primaryKeys = ["binding_id"],
    foreignKeys = [
        ForeignKey(
            entity = TargetRow::class,
            parentColumns = ["target_row_id"],
            childColumns = ["target_row_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = SourceRow::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            value = ["target_row_id", "source_id"],
            unique = true,
        ), Index(value = ["source_id"]),
    ],
)
internal data class BindingRow(
    @ColumnInfo(name = "binding_id") val bindingId: String,
    @ColumnInfo(name = "target_row_id") val targetRowId: Long,
    @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(
    tableName = "asset",
    primaryKeys = ["asset_id"],
    foreignKeys = [
        ForeignKey(
            entity = BindingRow::class,
            parentColumns = ["binding_id"],
            childColumns = ["binding_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["binding_id"])],
)
internal data class AssetRow(
    @ColumnInfo(name = "asset_id") val assetId: String,
    @ColumnInfo(name = "binding_id") val bindingId: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "family") val family: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long?,
    @ColumnInfo(name = "observed_at") val observedAt: Long,
    @ColumnInfo(name = "presence") val presence: String,
)

@Entity(
    tableName = "asset_locator",
    primaryKeys = ["locator_id"],
    foreignKeys = [
        ForeignKey(
            entity = AssetRow::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = RootRow::class,
            parentColumns = ["root_id"],
            childColumns = ["root_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(
            value = ["root_id", "authority", "document_locator"],
            unique = true,
        ), Index(value = ["asset_id"]),
    ],
)
internal data class LocatorRow(
    @ColumnInfo(name = "locator_id") val locatorId: String,
    @ColumnInfo(name = "asset_id") val assetId: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "authority") val authority: String,
    @ColumnInfo(name = "document_locator") val documentLocator: String,
    @ColumnInfo(name = "observed_at") val observedAt: Long,
)

@Entity(
    tableName = "library_entry",
    primaryKeys = ["media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaRow::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
internal data class LibraryRow(
    @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "membership") val membership: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(
    tableName = "progress_state",
    primaryKeys = ["target_row_id"],
    foreignKeys = [
        ForeignKey(
            entity = TargetRow::class,
            parentColumns = ["target_row_id"],
            childColumns = ["target_row_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
internal data class ProgressRow(
    @ColumnInfo(name = "target_row_id") val targetRowId: Long,
    @ColumnInfo(name = "completion") val completion: String,
    @ColumnInfo(name = "binding_id") val bindingId: String?,
    @ColumnInfo(name = "asset_id") val assetId: String?,
    @ColumnInfo(name = "asset_revision") val assetRevision: Long?,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "video_resume_anchor",
    primaryKeys = ["target_row_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProgressRow::class,
            parentColumns = ["target_row_id"],
            childColumns = ["target_row_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class AnchorRow(
    @ColumnInfo(name = "target_row_id") val targetRowId: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
)

@Entity(
    tableName = "scan_run",
    primaryKeys = ["run_id"],
)
internal data class RunRow(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "outcome") val outcome: String?,
    @ColumnInfo(name = "finalized_at") val finalizedAt: Long?,
)

@Entity(
    tableName = "scan_scope",
    primaryKeys = ["run_id"],
    foreignKeys = [
        ForeignKey(
            entity = RunRow::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RootRow::class,
            parentColumns = ["root_id"],
            childColumns = ["root_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["root_id"])],
)
internal data class ScopeRow(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "coverage") val coverage: String,
    @ColumnInfo(name = "gaps") val gaps: String,
    @ColumnInfo(name = "scope_kind") val scopeKind: String = "FULL_ROOT",
    @ColumnInfo(name = "mime_filter") val mimeFilter: String = "video/mp4",
)

@Entity(
    tableName = "scan_seen_asset",
    primaryKeys = ["run_id", "asset_id"],
    indices = [Index(value = ["asset_id"])],
    foreignKeys = [
        ForeignKey(
            entity = RunRow::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AssetRow::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
internal data class SeenRow(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "asset_id") val assetId: String,
    @ColumnInfo(name = "observed_at") val observedAt: Long,
)
