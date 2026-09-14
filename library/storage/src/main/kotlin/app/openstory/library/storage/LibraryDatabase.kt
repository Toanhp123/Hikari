package app.openstory.library.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LibraryEntryEntity::class, LibrarySearchFts::class],
    version = 1,
    exportSchema = true,
)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "hikari-v2-library.db"
    }
}
