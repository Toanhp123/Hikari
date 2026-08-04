package app.openstory.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.openstory.database.entity.CanonicalChapterEntity
import app.openstory.database.entity.CanonicalChapterReleaseEntity
import app.openstory.database.entity.CanonicalStoryEntity
import app.openstory.database.entity.CatalogEntryEntity
import app.openstory.database.entity.ChapterReleaseEntity
import app.openstory.database.entity.ContentMappingEntity
import app.openstory.database.entity.LibraryEntryEntity
import app.openstory.database.entity.PluginStateEntity
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    DatabaseConverters::class,
)
abstract class OpenStoryDatabase : RoomDatabase() {

    companion object {
        private const val DATABASE_NAME =
            "openstory.db"

        fun open(
            context: Context,
        ): OpenStoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OpenStoryDatabase::class.java,
                DATABASE_NAME,
            )
                .setJournalMode(
                    JournalMode.WRITE_AHEAD_LOGGING,
                )
                .build()
    }
}
