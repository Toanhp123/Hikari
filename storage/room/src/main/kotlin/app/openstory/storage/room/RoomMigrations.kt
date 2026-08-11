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

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createCanonicalChapters(db)
            createChapterReleases(db)
            createChapterOverrides(db)
            createChapterSyncStates(db)
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_progress` (" +
                    "`story_id` TEXT NOT NULL, `canonical_chapter_id` TEXT NOT NULL, " +
                    "`chapter_release_id` TEXT NOT NULL, `content_fingerprint` TEXT NOT NULL, " +
                    "`block_id` TEXT NOT NULL, `character_offset` INTEGER NOT NULL, `fraction` REAL NOT NULL, " +
                    "`completed_at_epoch_millis` INTEGER, `updated_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`story_id`, `canonical_chapter_id`), " +
                    "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`canonical_chapter_id`) REFERENCES `canonical_chapters`(`canonical_chapter_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reading_progress_canonical_chapter_id` " +
                    "ON `reading_progress` (`canonical_chapter_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reading_progress_chapter_release_id` " +
                    "ON `reading_progress` (`chapter_release_id`)",
            )
        }
    }

    private fun createCanonicalChapters(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `canonical_chapters` (" +
                "`canonical_chapter_id` TEXT NOT NULL, `story_id` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `volume` TEXT, `chapter` TEXT, `part` INTEGER, " +
                "`normalized_title` TEXT, `display_label` TEXT NOT NULL, `tombstoned` INTEGER NOT NULL, " +
                "PRIMARY KEY(`canonical_chapter_id`), FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_canonical_chapters_story_id` " +
                "ON `canonical_chapters` (`story_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_canonical_chapters_story_id_tombstoned` " +
                "ON `canonical_chapters` (`story_id`, `tombstoned`)",
        )
    }

    private fun createChapterReleases(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chapter_releases` (" +
                "`chapter_release_id` TEXT NOT NULL, `story_id` TEXT NOT NULL, `plugin_id` TEXT NOT NULL, " +
                "`source_story_id` TEXT NOT NULL, `source_release_id` TEXT NOT NULL, `display_label` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `volume` TEXT, `chapter` TEXT, `part` INTEGER, `normalized_title` TEXT, " +
                "`language_tag` TEXT NOT NULL, `published_at_epoch_millis` INTEGER, `canonical_chapter_id` TEXT, " +
                "PRIMARY KEY(`chapter_release_id`), FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`canonical_chapter_id`) " +
                "REFERENCES `canonical_chapters`(`canonical_chapter_id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_releases_story_id` ON `chapter_releases` (`story_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapter_releases_canonical_chapter_id` " +
                "ON `chapter_releases` (`canonical_chapter_id`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_releases_plugin_id_source_story_id_source_release_id` " +
                "ON `chapter_releases` (`plugin_id`, `source_story_id`, `source_release_id`)",
        )
    }

    private fun createChapterOverrides(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chapter_aggregation_overrides` (" +
                "`story_id` TEXT NOT NULL, `chapter_release_id` TEXT NOT NULL, `canonical_chapter_id` TEXT, " +
                "`kind` TEXT NOT NULL, PRIMARY KEY(`story_id`, `chapter_release_id`), " +
                "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`chapter_release_id`) REFERENCES `chapter_releases`(`chapter_release_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`canonical_chapter_id`) " +
                "REFERENCES `canonical_chapters`(`canonical_chapter_id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapter_aggregation_overrides_chapter_release_id` " +
                "ON `chapter_aggregation_overrides` (`chapter_release_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapter_aggregation_overrides_canonical_chapter_id` " +
                "ON `chapter_aggregation_overrides` (`canonical_chapter_id`)",
        )
    }

    private fun createChapterSyncStates(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chapter_sync_states` (" +
                "`story_id` TEXT NOT NULL, `plugin_id` TEXT NOT NULL, `source_story_id` TEXT NOT NULL, " +
                "`phase` TEXT NOT NULL, `cursor` TEXT, `checkpoint` TEXT, `fingerprint` TEXT, " +
                "`updated_at_epoch_millis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`story_id`, `plugin_id`, `source_story_id`), " +
                "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chapter_sync_states_story_id` " +
                "ON `chapter_sync_states` (`story_id`)",
        )
    }
}
