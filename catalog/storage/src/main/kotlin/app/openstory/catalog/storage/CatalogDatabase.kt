package app.openstory.catalog.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.openstory.catalog.storage.discover.CatalogSourceStateEntity
import app.openstory.catalog.storage.discover.DiscoverCardEntity
import app.openstory.catalog.storage.discover.DiscoverDao
import app.openstory.catalog.storage.retention.StoryRetentionDao
import app.openstory.catalog.storage.retention.StoryRetentionEntity
import app.openstory.catalog.storage.story.StoryArtistEntity
import app.openstory.catalog.storage.story.StoryAliasEntity
import app.openstory.catalog.storage.story.StoryAuthorEntity
import app.openstory.catalog.storage.story.StoryDetailDao
import app.openstory.catalog.storage.story.StoryDetailEntity
import app.openstory.catalog.storage.story.StoryGenreEntity
import app.openstory.catalog.storage.story.StoryLanguageEntity
import app.openstory.catalog.storage.story.StorySourceIdentityEntity
import app.openstory.catalog.storage.story.StorySourceSummaryEntity

@Database(
    entities = [
        CatalogSourceStateEntity::class,
        StorySourceIdentityEntity::class,
        StorySourceSummaryEntity::class,
        DiscoverCardEntity::class,
        StoryDetailEntity::class,
        StoryAuthorEntity::class,
        StoryArtistEntity::class,
        StoryGenreEntity::class,
        StoryAliasEntity::class,
        StoryLanguageEntity::class,
        StoryRetentionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
internal abstract class CatalogDatabase : RoomDatabase() {
    abstract fun discoverDao(): DiscoverDao

    abstract fun storyDetailDao(): StoryDetailDao

    abstract fun storyRetentionDao(): StoryRetentionDao

    companion object {
        const val NAME = "hikari-v2-catalog.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `story_alias` (
                        `story_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `value` TEXT NOT NULL,
                        PRIMARY KEY(`story_id`, `position`),
                        FOREIGN KEY(`story_id`) REFERENCES `story_detail`(`story_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `story_language` (
                        `story_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `value` TEXT NOT NULL,
                        PRIMARY KEY(`story_id`, `position`),
                        FOREIGN KEY(`story_id`) REFERENCES `story_detail`(`story_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
