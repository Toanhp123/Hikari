package app.openstory.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.openstory.storage.room.catalog.CatalogDao
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.CatalogHomeItemEntity
import app.openstory.storage.room.catalog.CatalogHomeDao
import app.openstory.storage.room.catalog.CatalogHomeSectionEntity
import app.openstory.storage.room.catalog.CatalogHomeSnapshotEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.chapters.CanonicalChapterEntity
import app.openstory.storage.room.chapters.ChapterAggregationOverrideEntity
import app.openstory.storage.room.chapters.ChapterDao
import app.openstory.storage.room.chapters.ChapterReleaseEntity
import app.openstory.storage.room.chapters.ChapterSyncStateEntity
import app.openstory.storage.room.chapters.ChapterSyncDao
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.ContentMappingRejectionEntity
import app.openstory.storage.room.library.LibraryDao
import app.openstory.storage.room.library.LibraryEntity
import app.openstory.storage.room.plugins.PluginDiagnosticDao
import app.openstory.storage.room.plugins.PluginDiagnosticEntity
import app.openstory.storage.room.plugins.PluginStateDao
import app.openstory.storage.room.plugins.PluginStateEntity
import app.openstory.storage.room.plugins.PluginVersionEntity
import app.openstory.storage.room.reader.ReadingProgressDao
import app.openstory.storage.room.reader.ReadingProgressEntity

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
        LibraryEntity::class,
        ContentMappingEntity::class,
        ContentMappingRejectionEntity::class,
        CanonicalChapterEntity::class,
        ChapterReleaseEntity::class,
        ChapterAggregationOverrideEntity::class,
        ChapterSyncStateEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class OpenStoryDatabase : RoomDatabase() {
    internal abstract fun catalogDao(): CatalogDao
    internal abstract fun catalogHomeDao(): CatalogHomeDao
    internal abstract fun pluginStateDao(): PluginStateDao
    internal abstract fun pluginDiagnosticDao(): PluginDiagnosticDao
    internal abstract fun libraryDao(): LibraryDao
    internal abstract fun chapterDao(): ChapterDao
    internal abstract fun chapterSyncDao(): ChapterSyncDao
    internal abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        private const val DATABASE_NAME = "openstory-baseline-2.db"

        fun open(context: Context): OpenStoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            OpenStoryDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(
            RoomMigrations.MIGRATION_1_2,
            RoomMigrations.MIGRATION_2_3,
            RoomMigrations.MIGRATION_3_4,
            RoomMigrations.MIGRATION_4_5,
        )
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
