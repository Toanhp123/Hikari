package app.openstory.catalog.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import app.openstory.catalog.storage.discover.CatalogSourceStateEntity
import app.openstory.catalog.storage.discover.DiscoverCardEntity
import app.openstory.catalog.storage.discover.DiscoverDao
import app.openstory.catalog.storage.retention.StoryRetentionDao
import app.openstory.catalog.storage.retention.StoryRetentionEntity
import app.openstory.catalog.storage.story.StoryArtistEntity
import app.openstory.catalog.storage.story.StoryAuthorEntity
import app.openstory.catalog.storage.story.StoryDetailDao
import app.openstory.catalog.storage.story.StoryDetailEntity
import app.openstory.catalog.storage.story.StoryGenreEntity
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
        StoryRetentionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class CatalogDatabase : RoomDatabase() {
    abstract fun discoverDao(): DiscoverDao

    abstract fun storyDetailDao(): StoryDetailDao

    abstract fun storyRetentionDao(): StoryRetentionDao

    companion object {
        const val NAME = "hikari-v2-catalog.db"
    }
}
