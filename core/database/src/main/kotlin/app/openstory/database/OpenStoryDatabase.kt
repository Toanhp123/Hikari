package app.openstory.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.openstory.database.dao.ChapterDao
import app.openstory.database.dao.PluginDiagnosticDao
import app.openstory.database.dao.PluginDiagnosticEntity
import app.openstory.database.dao.PluginStateDao
import app.openstory.database.dao.ProgressDao
import app.openstory.database.dao.StoryDao
import app.openstory.database.dao.StoryPurgeDao
import app.openstory.database.entity.CanonicalChapterEntity
import app.openstory.database.entity.CanonicalChapterReleaseEntity
import app.openstory.database.entity.CanonicalStoryEntity
import app.openstory.database.entity.CatalogEntryEntity
import app.openstory.database.entity.ChapterReleaseEntity
import app.openstory.database.entity.ContentMappingEntity
import app.openstory.database.entity.LibraryEntryEntity
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity
import app.openstory.database.entity.ReadingProgressEntity
import app.openstory.database.entity.StoryCatalogEntryEntity
import app.openstory.database.entity.StoryContentMappingEntity

@Database(
    entities = [
        CanonicalStoryEntity::class,
        CatalogEntryEntity::class,
        StoryCatalogEntryEntity::class,
        LibraryEntryEntity::class,
        ContentMappingEntity::class,
        StoryContentMappingEntity::class,
        CanonicalChapterEntity::class,
        ChapterReleaseEntity::class,
        CanonicalChapterReleaseEntity::class,
        ReadingProgressEntity::class,
        PluginStateEntity::class,
        PluginVersionEntity::class,
        PluginDiagnosticEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    DatabaseConverters::class,
)
abstract class OpenStoryDatabase : RoomDatabase() {

    internal abstract fun storyDao(): StoryDao

    internal abstract fun storyPurgeDao(): StoryPurgeDao

    internal abstract fun chapterDao(): ChapterDao

    internal abstract fun progressDao(): ProgressDao

    internal abstract fun pluginStateDao(): PluginStateDao

    internal abstract fun pluginDiagnosticDao(): PluginDiagnosticDao

    companion object {
        private const val DATABASE_NAME =
            "openstory.db"

        fun open(
            context: Context,
        ): OpenStoryDatabase =
            open(
                context =
                    context,
                databaseName =
                    DATABASE_NAME,
            )

        internal fun open(
            context: Context,
            databaseName: String,
        ): OpenStoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OpenStoryDatabase::class.java,
                databaseName,
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
