package app.openstory.storage.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RoomMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `library_entries` (" +
                    "`story_id` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`added_at_epoch_millis` INTEGER NOT NULL, " +
                    "`updated_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`story_id`), " +
                    "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_library_entries_status` " +
                    "ON `library_entries` (`status`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_library_entries_updated_at_epoch_millis` " +
                    "ON `library_entries` (`updated_at_epoch_millis`)",
            )
        }
    }
}
