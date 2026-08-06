package app.openstory.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity

@Dao
internal abstract class PluginStateDao {

    @Query(
        """
        SELECT *
        FROM plugin_states
        WHERE plugin_id = :pluginId
        """,
    )
    abstract suspend fun find(
        pluginId: String,
    ): PluginStateEntity?

    @Query(
        """
        SELECT *
        FROM plugin_versions
        WHERE plugin_id = :pluginId
            AND version = :version
        """,
    )
    abstract suspend fun findVersion(
        pluginId: String,
        version: String,
    ): PluginVersionEntity?

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
    abstract suspend fun insertVersionIfMissing(
        version: PluginVersionEntity,
    ): Long

    @Upsert
    abstract suspend fun upsert(
        state: PluginStateEntity,
    )

    @Query(
        """
        UPDATE plugin_states
        SET
            enabled = :enabled,
            updated_at_epoch_millis =
                :updatedAtEpochMillis
        WHERE plugin_id = :pluginId
        """,
    )
    abstract suspend fun updateEnabled(
        pluginId: String,
        enabled: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun activate(
        version: PluginVersionEntity,
        updatedAtEpochMillis: Long,
    ): PluginStateEntity {
        val insertedRowId =
            insertVersionIfMissing(
                version,
            )

        if (insertedRowId == INSERT_CONFLICT) {
            val existingVersion =
                checkNotNull(
                    findVersion(
                        pluginId =
                            version.pluginId,
                        version =
                            version.version,
                    ),
                ) {
                    "Expected the conflicting plugin version to exist."
                }

            val comparableVersion =
                version.copy(
                    installedAtEpochMillis =
                        existingVersion
                            .installedAtEpochMillis,
                )

            check(
                existingVersion ==
                    comparableVersion,
            ) {
                "Installed plugin version metadata is immutable."
            }
        }

        val existingState =
            find(
                version.pluginId,
            )

        val activatedState =
            PluginStateEntity(
                pluginId =
                    version.pluginId,
                enabled =
                    existingState?.enabled
                        ?: true,
                activeVersion =
                    version.version,
                previousVersion =
                    existingState?.activeVersion,
                updatedAtEpochMillis =
                    updatedAtEpochMillis,
            )

        upsert(
            activatedState,
        )

        return activatedState
    }

    private companion object {
        const val INSERT_CONFLICT =
            -1L
    }
}
