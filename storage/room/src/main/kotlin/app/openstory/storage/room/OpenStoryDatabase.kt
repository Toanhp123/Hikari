package app.openstory.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.openstory.storage.room.catalog.CatalogDao
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.CatalogHomeItemEntity
import app.openstory.storage.room.catalog.CatalogHomeSectionEntity
import app.openstory.storage.room.catalog.CatalogHomeSnapshotEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.plugins.PluginDiagnosticDao
import app.openstory.storage.room.plugins.PluginDiagnosticEntity
import app.openstory.storage.room.plugins.PluginStateDao
import app.openstory.storage.room.plugins.PluginStateEntity
import app.openstory.storage.room.plugins.PluginVersionEntity

@Database(
    entities = [
        StoryEntity::class,
        CatalogEntryEntity::class,
        CatalogHomeSnapshotEntity::class,
        CatalogHomeSectionEntity::class,
        CatalogHomeItemEntity::class,
        PluginStateEntity::class,
        PluginVersionEntity::class,
        PluginDiagnosticEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class OpenStoryDatabase : RoomDatabase() {
    internal abstract fun catalogDao(): CatalogDao
    internal abstract fun pluginStateDao(): PluginStateDao
    internal abstract fun pluginDiagnosticDao(): PluginDiagnosticDao

    companion object {
        private const val DATABASE_NAME = "openstory.db"

        fun open(context: Context): OpenStoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            OpenStoryDatabase::class.java,
            DATABASE_NAME,
        ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build()
    }
}
