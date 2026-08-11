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

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `content_mappings` (" +
                    "`story_id` TEXT NOT NULL, " +
                    "`plugin_id` TEXT NOT NULL, " +
                    "`source_story_id` TEXT NOT NULL, " +
                    "`origin` TEXT NOT NULL, " +
                    "`policy_version` INTEGER NOT NULL, " +
                    "`updated_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`story_id`, `plugin_id`), " +
                    "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_content_mappings_story_id` " +
                    "ON `content_mappings` (`story_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_content_mappings_plugin_id` " +
                    "ON `content_mappings` (`plugin_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_content_mappings_origin` " +
                    "ON `content_mappings` (`origin`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `content_mapping_rejections` (" +
                    "`story_id` TEXT NOT NULL, " +
                    "`plugin_id` TEXT NOT NULL, " +
                    "`source_story_id` TEXT NOT NULL, " +
                    "`policy_version` INTEGER NOT NULL, " +
                    "`rejected_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`story_id`, `plugin_id`, `source_story_id`, `policy_version`), " +
                    "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_content_mapping_rejections_story_id` " +
                    "ON `content_mapping_rejections` (`story_id`)",
            )
        }
    }
}
