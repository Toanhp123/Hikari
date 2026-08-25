package app.openstory.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.openstory.storage.room.catalog.CatalogDao
import app.openstory.storage.room.catalog.CanonicalCatalogDao
import app.openstory.storage.room.catalog.CanonicalEngineMaintenanceDao
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.catalog.CatalogChangeOutboxEntity
import app.openstory.storage.room.catalog.CanonicalFieldProvenanceEntity
import app.openstory.storage.room.catalog.CanonicalGenerationEntity
import app.openstory.storage.room.catalog.CatalogEntryIdentifierEntity
import app.openstory.storage.room.catalog.ReconciliationCaseEntity
import app.openstory.storage.room.catalog.ReconciliationCaseRevisionEntity
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryMergeEventEntity
import app.openstory.storage.room.catalog.StoryMergeReversalEventEntity
import app.openstory.storage.room.catalog.StoryRedirectEntity
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.CatalogHomeItemEntity
import app.openstory.storage.room.catalog.CatalogHomeDao
import app.openstory.storage.room.catalog.CatalogHomeSectionEntity
import app.openstory.storage.room.catalog.CatalogHomeSnapshotEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.chapters.CanonicalChapterEntity
import app.openstory.storage.room.chapters.ChapterAggregationOverrideEntity
import app.openstory.storage.room.chapters.ChapterDao
import app.openstory.storage.room.chapters.ChapterChangeEventEntity
import app.openstory.storage.room.chapters.ChapterReleaseEntity
import app.openstory.storage.room.chapters.ChapterSyncStateEntity
import app.openstory.storage.room.chapters.ChapterSyncDao
import app.openstory.storage.room.chapters.NotificationDeliveryEntity
import app.openstory.storage.room.chapters.NotificationEventDao
import app.openstory.storage.room.downloads.ChapterStorageEntryEntity
import app.openstory.storage.room.downloads.DownloadDao
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
        CatalogEntryIdentifierEntity::class,
        StoryCanonicalStateEntity::class,
        CanonicalGenerationEntity::class,
        CanonicalFieldProvenanceEntity::class,
        ReconciliationCaseEntity::class,
        ReconciliationCaseRevisionEntity::class,
        StoryMergeEventEntity::class,
        StoryMergeReversalEventEntity::class,
        StoryRedirectEntity::class,
        CanonicalEngineWorkEntity::class,
        CatalogChangeOutboxEntity::class,
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
        ChapterChangeEventEntity::class,
        NotificationDeliveryEntity::class,
        ReadingProgressEntity::class,
        ChapterStorageEntryEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class OpenStoryDatabase : RoomDatabase() {
    internal abstract fun catalogDao(): CatalogDao
    internal abstract fun catalogHomeDao(): CatalogHomeDao
    internal abstract fun canonicalCatalogDao(): CanonicalCatalogDao
    internal abstract fun canonicalEngineMaintenanceDao(): CanonicalEngineMaintenanceDao
    internal abstract fun pluginStateDao(): PluginStateDao
    internal abstract fun pluginDiagnosticDao(): PluginDiagnosticDao
    internal abstract fun libraryDao(): LibraryDao
    internal abstract fun chapterDao(): ChapterDao
    internal abstract fun chapterSyncDao(): ChapterSyncDao
    internal abstract fun notificationEventDao(): NotificationEventDao
    internal abstract fun readingProgressDao(): ReadingProgressDao
    internal abstract fun downloadDao(): DownloadDao

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
            RoomMigrations.MIGRATION_5_6,
            RoomMigrations.MIGRATION_6_7,
            RoomMigrations.MIGRATION_7_8,
            RoomMigrations.MIGRATION_8_9,
            RoomMigrations.MIGRATION_9_10,
            RoomMigrations.MIGRATION_10_11,
        )
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
