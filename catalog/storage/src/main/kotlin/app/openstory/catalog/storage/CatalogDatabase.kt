package app.openstory.catalog.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import app.openstory.catalog.storage.discover.CatalogSourceStateEntity
import app.openstory.catalog.storage.discover.DiscoverCardEntity
import app.openstory.catalog.storage.discover.DiscoverDao
import app.openstory.catalog.storage.story.StorySourceIdentityEntity
import app.openstory.catalog.storage.story.StorySourceSummaryEntity

@Database(
    entities = [
        CatalogSourceStateEntity::class,
        StorySourceIdentityEntity::class,
        StorySourceSummaryEntity::class,
        DiscoverCardEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class CatalogDatabase : RoomDatabase() {
    abstract fun discoverDao(): DiscoverDao

    companion object {
        const val NAME = "hikari-v2-catalog.db"
    }
}
