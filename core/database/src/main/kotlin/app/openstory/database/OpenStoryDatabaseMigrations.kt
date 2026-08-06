package app.openstory.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration1To2 =
    object : Migration(
        1,
        2,
    ) {
        override fun migrate(
            db: SupportSQLiteDatabase,
        ) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `plugin_versions` (
                    `plugin_id` TEXT NOT NULL,
                    `version` TEXT NOT NULL,
                    `package_sha256` TEXT NOT NULL,
                    `location` TEXT NOT NULL,
                    `trust_signature_state` TEXT NOT NULL,
                    `signer_key_id` TEXT,
                    `signer_fingerprint_sha256` TEXT,
                    `install_source` TEXT NOT NULL,
                    `source_reference` TEXT NOT NULL,
                    `unsigned_warning_acknowledged` INTEGER NOT NULL,
                    `accepted_capabilities` TEXT NOT NULL,
                    `installed_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(
                        `plugin_id`,
                        `version`
                    )
                )
                """.trimIndent(),
            )
        }
    }

internal val migration2To3 =
    object : Migration(
        2,
        3,
    ) {
        override fun migrate(
            db: SupportSQLiteDatabase,
        ) {
            db.execSQL(
                """
                ALTER TABLE `catalog_entries`
                ADD COLUMN `external_story_id` TEXT NOT NULL DEFAULT ''
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE `catalog_entries`
                SET `external_story_id` = `catalog_entry_id`
                WHERE `external_story_id` = ''
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `catalog_entries`
                ADD COLUMN `source_url` TEXT
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `catalog_entries`
                ADD COLUMN `authors_json` TEXT NOT NULL DEFAULT '[]'
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `catalog_entries`
                ADD COLUMN `genres_json` TEXT NOT NULL DEFAULT '[]'
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `catalog_entries`
                ADD COLUMN `cover_reference` TEXT
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `catalog_entries`
                ADD COLUMN `publication_status` TEXT
                """.trimIndent(),
            )
        }
    }
