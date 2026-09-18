package app.universalmedia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

internal data class CardRow(val mediaId: String, val kind: String, val fallbackDisplayName: String?)

@Dao
internal interface CatalogDao {
    @Insert fun insert(row: MediaRow)

    @Insert fun insert(row: TargetRow): Long

    @Insert fun insert(row: SourceRow)

    @Insert fun insert(row: RootRow)

    @Insert fun insert(row: BindingRow)

    @Insert fun insert(row: AssetRow)

    @Insert fun insert(row: LocatorRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun admit(row: LibraryRow)

    @Insert fun insert(row: RunRow)

    @Insert fun insert(row: ScopeRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun seen(row: SeenRow)

    @Insert fun insert(row: ProgressRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun anchor(row: AnchorRow)

    @Update fun update(row: RootRow)

    @Update fun update(row: AssetRow)

    @Update fun update(row: LocatorRow)

    @Update fun update(row: RunRow)

    @Update fun update(row: ScopeRow)

    @Update fun update(row: ProgressRow)

    @Query("SELECT * FROM storage_root WHERE root_id = :id")
    fun root(id: String): RootRow?

    @Query(
        """
        SELECT * FROM storage_root WHERE authority = :authority AND tree_locator =
        :locator
    """,
    )
    fun rootByDescriptor(authority: String, locator: String): RootRow?

    @Query("SELECT * FROM scan_run WHERE run_id = :id")
    fun run(id: String): RunRow?

    @Query("SELECT * FROM scan_scope WHERE run_id = :id")
    fun scope(id: String): ScopeRow?

    @Query(
        """
        SELECT scan_run.* FROM scan_run JOIN scan_scope USING(run_id) WHERE root_id =
        :root AND outcome IS NULL
    """,
    )
    fun activeRuns(root: String): List<RunRow>

    @Query("SELECT * FROM source WHERE kind = 'LOCAL'")
    fun localSource(): SourceRow?

    @Query(
        """
        SELECT * FROM asset_locator WHERE root_id = :root AND authority = :authority
        AND document_locator = :locator
    """,
    )
    fun locator(root: String, authority: String, locator: String): LocatorRow?

    @Query("SELECT * FROM asset WHERE asset_id = :id")
    fun asset(id: String): AssetRow?

    @Query("SELECT * FROM source_binding WHERE binding_id = :id")
    fun binding(id: String): BindingRow?

    @Query("SELECT * FROM consumption_target WHERE target_row_id = :id")
    fun target(id: Long): TargetRow?

    @Query("SELECT * FROM consumption_target WHERE media_id = :id")
    fun mediaTarget(id: String): TargetRow?

    @Query("SELECT * FROM consumption_target WHERE unit_id = :id")
    fun unitTarget(id: String): TargetRow?

    @Query("SELECT * FROM progress_state WHERE target_row_id = :id")
    fun progress(id: Long): ProgressRow?

    @Query("SELECT * FROM video_resume_anchor WHERE target_row_id = :id")
    fun anchor(id: Long): AnchorRow?

    @Query(
        """
        SELECT asset.* FROM asset JOIN source_binding USING(binding_id) JOIN source
        USING(source_id) WHERE target_row_id = :target AND source.kind = 'LOCAL'
        ORDER BY asset_id LIMIT 1
    """,
    )
    fun localAsset(target: Long): AssetRow?

    @Query(
        """
        SELECT * FROM asset_locator WHERE asset_id = :asset ORDER BY locator_id LIMIT
        1
    """,
    )
    fun assetLocator(asset: String): LocatorRow?

    @Query(
        """
        SELECT media.media_id AS mediaId, media.kind AS kind,
          (SELECT asset.display_name FROM asset
           JOIN source_binding USING(binding_id)
           JOIN consumption_target USING(target_row_id)
           WHERE consumption_target.media_id = media.media_id
           ORDER BY asset.observed_at DESC, asset.asset_id LIMIT 1) AS fallbackDisplayName
        FROM library_entry JOIN media USING(media_id)
        WHERE membership = 'ACTIVE' ORDER BY added_at, media.media_id
    """,
    )
    fun cards(): Flow<List<CardRow>>
}
