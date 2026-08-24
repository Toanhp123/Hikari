package app.openstory.storage.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RoomMigrations {
    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chapter_change_events` (" +
                    "`event_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `event_key` TEXT NOT NULL, " +
                    "`story_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL, `release_id` TEXT, " +
                    "`change_kind` TEXT NOT NULL, `chapter_commit_fingerprint` TEXT NOT NULL, " +
                    "`occurred_at_epoch_millis` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_change_events_event_key` " +
                    "ON `chapter_change_events` (`event_key`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chapter_change_events_story_id_chapter_id_change_kind` " +
                    "ON `chapter_change_events` (`story_id`, `chapter_id`, `change_kind`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chapter_change_events_occurred_at_epoch_millis_event_id` " +
                    "ON `chapter_change_events` (`occurred_at_epoch_millis`, `event_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notification_deliveries` (" +
                    "`event_id` INTEGER NOT NULL, `status` TEXT NOT NULL, `claim_token` TEXT, " +
                    "`claim_expires_at_epoch_millis` INTEGER, `attempt_count` INTEGER NOT NULL, " +
                    "`next_attempt_at_epoch_millis` INTEGER NOT NULL, `notification_id` INTEGER, " +
                    "`reason_code` TEXT, `last_error_code` TEXT, `updated_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`event_id`), FOREIGN KEY(`event_id`) REFERENCES `chapter_change_events`(`event_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_notification_deliveries_status_next_attempt_at_epoch_millis_event_id` " +
                    "ON `notification_deliveries` (`status`, `next_attempt_at_epoch_millis`, `event_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_deliveries_claim_expires_at_epoch_millis` " +
                    "ON `notification_deliveries` (`claim_expires_at_epoch_millis`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_deliveries_claim_token` " +
                    "ON `notification_deliveries` (`claim_token`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_deliveries_notification_id` " +
                    "ON `notification_deliveries` (`notification_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_deliveries_updated_at_epoch_millis_status` " +
                    "ON `notification_deliveries` (`updated_at_epoch_millis`, `status`)",
            )
        }
    }

    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `canonical_engine_work` ADD COLUMN `lease_token` TEXT")
            db.execSQL(
                "ALTER TABLE `canonical_engine_work` ADD COLUMN `lease_expires_at_epoch_millis` INTEGER",
            )
            db.execSQL(
                "DROP INDEX IF EXISTS `index_canonical_engine_work_next_attempt_at_epoch_millis`",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_canonical_engine_work_lease_expires_at_epoch_millis_" +
                    "next_attempt_at_epoch_millis_work_type_story_id` " +
                    "ON `canonical_engine_work` (`lease_expires_at_epoch_millis`, " +
                    "`next_attempt_at_epoch_millis`, `work_type`, `story_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalog_change_outbox` (" +
                    "`event_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `story_id` TEXT NOT NULL, " +
                    "`plugin_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
                    "`identity_fingerprint_changed` INTEGER NOT NULL, " +
                    "`fusion_fingerprint_changed` INTEGER NOT NULL, `availability_changed` INTEGER NOT NULL, " +
                    "`evidence_level` TEXT NOT NULL, `reason` TEXT NOT NULL, " +
                    "`created_at_epoch_millis` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_change_outbox_story_id` " +
                    "ON `catalog_change_outbox` (`story_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_change_outbox_created_at_epoch_millis_event_id` " +
                    "ON `catalog_change_outbox` (`created_at_epoch_millis`, `event_id`)",
            )
        }
    }

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

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chapter_storage_entries` (" +
                    "`namespace` TEXT NOT NULL, `chapter_release_id` TEXT NOT NULL, " +
                    "`content_fingerprint` TEXT NOT NULL, `checksum` TEXT, " +
                    "`size_bytes` INTEGER NOT NULL, `last_accessed_at_epoch_millis` INTEGER NOT NULL, " +
                    "`pinned` INTEGER NOT NULL, `current` INTEGER NOT NULL, " +
                    "`download_state` TEXT, `failure_reason` TEXT, `attempt` INTEGER NOT NULL, " +
                    "`updated_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`namespace`, `chapter_release_id`, `content_fingerprint`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chapter_storage_entries_chapter_release_id` " +
                    "ON `chapter_storage_entries` (`chapter_release_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chapter_storage_entries_last_accessed_at_epoch_millis` " +
                    "ON `chapter_storage_entries` (`last_accessed_at_epoch_millis`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chapter_storage_entries_download_state` " +
                    "ON `chapter_storage_entries` (`download_state`)",
            )
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `catalog_home_sections` " +
                    "ADD COLUMN `feed_kind` TEXT NOT NULL DEFAULT 'OTHER'",
            )
            db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `publication_status` TEXT")
            db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `latest_update_at_epoch_millis` INTEGER")
            db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `latest_update_release_label` TEXT")
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `full_plugin_version` TEXT")
            db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `full_resolved_at_epoch_millis` INTEGER")
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalog_entry_identifiers` (" +
                    "`plugin_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `namespace` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, `scope` TEXT NOT NULL, " +
                    "PRIMARY KEY(`plugin_id`, `source_id`, `namespace`, `value`, `scope`), " +
                    "FOREIGN KEY(`plugin_id`, `source_id`) REFERENCES `catalog_entries`(`plugin_id`, `source_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_entry_identifiers_namespace_value_scope` " +
                    "ON `catalog_entry_identifiers` (`namespace`, `value`, `scope`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_catalog_entry_identifiers_plugin_id_source_id` " +
                    "ON `catalog_entry_identifiers` (`plugin_id`, `source_id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `story_canonical_state` (" +
                    "`story_id` TEXT NOT NULL, `active_generation_id` TEXT, `health` TEXT NOT NULL, " +
                    "`preference_mode` TEXT NOT NULL, `pinned_plugin_id` TEXT, `pinned_source_id` TEXT, " +
                    "`preference_revision` INTEGER NOT NULL, `identity_revision` INTEGER NOT NULL, " +
                    "`created_at_epoch_millis` INTEGER, PRIMARY KEY(`story_id`), " +
                    "FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "CHECK((`preference_mode` = 'AUTO' AND `pinned_plugin_id` IS NULL " +
                    "AND `pinned_source_id` IS NULL) OR (`preference_mode` = 'PINNED' " +
                    "AND `pinned_plugin_id` IS NOT NULL AND `pinned_source_id` IS NOT NULL)))",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `canonical_generations` (" +
                    "`generation_id` TEXT NOT NULL, `story_id` TEXT NOT NULL, " +
                    "`fusion_policy_version` INTEGER NOT NULL, `primary_policy_version` INTEGER NOT NULL, " +
                    "`fusion_fingerprint` TEXT NOT NULL, `primary_plugin_id` TEXT NOT NULL, " +
                    "`primary_source_id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, " +
                    "`cover_url` TEXT, `source_url` TEXT, `popularity_rank` INTEGER, `aliases` TEXT NOT NULL, " +
                    "`authors` TEXT NOT NULL, `genres` TEXT NOT NULL, `language_tags` TEXT NOT NULL, " +
                    "`publication_status` TEXT, `latest_update_at_epoch_millis` INTEGER, " +
                    "`latest_update_release_label` TEXT, `score_normalized_value` REAL, " +
                    "`score_contributor_count` INTEGER, `health` TEXT NOT NULL, " +
                    "`created_at_epoch_millis` INTEGER NOT NULL, `valid` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`generation_id`), FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_canonical_generations_story_id_created_at_epoch_millis` " +
                    "ON `canonical_generations` (`story_id`, `created_at_epoch_millis`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `canonical_field_provenance` (" +
                    "`generation_id` TEXT NOT NULL, `field_key` TEXT NOT NULL, " +
                    "`contributor_plugin_id` TEXT NOT NULL, `contributor_source_id` TEXT NOT NULL, " +
                    "`strategy` TEXT NOT NULL, `contributor_fusion_fingerprint` TEXT NOT NULL, " +
                    "`metadata_level` TEXT NOT NULL, `reason_codes` TEXT NOT NULL, " +
                    "`policy_version` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`generation_id`, `field_key`, `contributor_plugin_id`, `contributor_source_id`), " +
                    "FOREIGN KEY(`generation_id`) REFERENCES `canonical_generations`(`generation_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_canonical_field_provenance_generation_id` " +
                    "ON `canonical_field_provenance` (`generation_id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reconciliation_cases` (" +
                    "`case_id` TEXT NOT NULL, `left_story_id` TEXT NOT NULL, `right_story_id` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, `current_revision_id` TEXT, " +
                    "`contextual_deferred_at_epoch_millis` INTEGER, `created_at_epoch_millis` INTEGER NOT NULL, " +
                    "`updated_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`case_id`), " +
                    "CHECK(`left_story_id` < `right_story_id`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_reconciliation_cases_left_story_id_right_story_id` " +
                    "ON `reconciliation_cases` (`left_story_id`, `right_story_id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reconciliation_case_revisions` (" +
                    "`revision_id` TEXT NOT NULL, `case_id` TEXT NOT NULL, `left_story_id` TEXT NOT NULL, " +
                    "`right_story_id` TEXT NOT NULL, `decision` TEXT NOT NULL, `identity_fingerprint` TEXT NOT NULL, " +
                    "`policy_version` INTEGER NOT NULL, `score` REAL NOT NULL, `title_similarity` REAL, " +
                    "`author_similarity` REAL, `reason_codes` TEXT NOT NULL, `hard_conflicts` TEXT NOT NULL, " +
                    "`resolution_origin` TEXT, `evaluated_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`revision_id`), FOREIGN KEY(`case_id`) REFERENCES `reconciliation_cases`(`case_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reconciliation_case_revisions_case_id` " +
                    "ON `reconciliation_case_revisions` (`case_id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `story_merge_events` (" +
                    "`merge_event_id` TEXT NOT NULL, `survivor_story_id` TEXT NOT NULL, " +
                    "`retired_story_id` TEXT NOT NULL, " +
                    "`origin` TEXT NOT NULL, `reconciliation_case_id` TEXT, `evidence_fingerprint` TEXT NOT NULL, " +
                    "`policy_version` INTEGER NOT NULL, `merged_at_epoch_millis` INTEGER NOT NULL, " +
                    "`reversibility_state` TEXT NOT NULL, `reversal_payload_version` INTEGER NOT NULL, " +
                    "`reversal_payload` TEXT NOT NULL, PRIMARY KEY(`merge_event_id`))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_story_merge_events_survivor_story_id_retired_story_id_" +
                    "evidence_fingerprint_policy_version` ON `story_merge_events` " +
                    "(`survivor_story_id`, `retired_story_id`, `evidence_fingerprint`, `policy_version`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `story_merge_reversal_events` (" +
                    "`reversal_event_id` TEXT NOT NULL, `merge_event_id` TEXT NOT NULL, " +
                    "`restored_story_id` TEXT NOT NULL, `surviving_story_id` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                    "`reason_codes` TEXT NOT NULL, `reversed_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`reversal_event_id`), FOREIGN KEY(`merge_event_id`) " +
                    "REFERENCES `story_merge_events`(`merge_event_id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_story_merge_reversal_events_merge_event_id` " +
                    "ON `story_merge_reversal_events` (`merge_event_id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `story_redirects` (" +
                    "`retired_story_id` TEXT NOT NULL, `canonical_story_id` TEXT NOT NULL, " +
                    "`merge_event_id` TEXT NOT NULL, " +
                    "`created_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`retired_story_id`), " +
                    "FOREIGN KEY(`canonical_story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`merge_event_id`) " +
                    "REFERENCES `story_merge_events`(`merge_event_id`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "CHECK(`retired_story_id` != `canonical_story_id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_story_redirects_canonical_story_id` " +
                    "ON `story_redirects` (`canonical_story_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_story_redirects_merge_event_id` " +
                    "ON `story_redirects` (`merge_event_id`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `canonical_engine_work` (" +
                    "`story_id` TEXT NOT NULL, `work_type` TEXT NOT NULL, `reason` TEXT NOT NULL, " +
                    "`attempt_count` INTEGER NOT NULL, `next_attempt_at_epoch_millis` INTEGER NOT NULL, " +
                    "`last_error_code` TEXT, `required_policy_version` INTEGER, " +
                    "PRIMARY KEY(`story_id`, `work_type`), FOREIGN KEY(`story_id`) REFERENCES `stories`(`story_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_canonical_engine_work_next_attempt_at_epoch_millis` " +
                    "ON `canonical_engine_work` (`next_attempt_at_epoch_millis`)",
            )

            db.execSQL(
                "INSERT INTO `story_canonical_state` (" +
                    "`story_id`, `active_generation_id`, `health`, `preference_mode`, `pinned_plugin_id`, " +
                    "`pinned_source_id`, `preference_revision`, `identity_revision`, `created_at_epoch_millis`) " +
                    "SELECT `story_id`, NULL, 'REEVALUATING', 'AUTO', NULL, NULL, 0, 0, NULL FROM `stories`",
            )
            db.execSQL(
                "INSERT INTO `canonical_engine_work` (" +
                    "`story_id`, `work_type`, `reason`, `attempt_count`, `next_attempt_at_epoch_millis`, " +
                    "`last_error_code`, `required_policy_version`) " +
                    "SELECT `story_id`, 'FUSION_REBUILD', 'schema-9-bootstrap', 0, 0, NULL, NULL FROM `stories`",
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
