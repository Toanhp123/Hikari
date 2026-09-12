package app.openstory.catalog.runtime.fixture

import android.content.Context
import android.database.sqlite.SQLiteDatabase

public object BenchmarkAgedCatalogFixture {
    @JvmStatic
    public fun seedUnrelatedRows(context: Context, count: Int = DEFAULT_ROW_COUNT) {
        require(count in 0..MAX_ROW_COUNT)
        val path = context.applicationContext.getDatabasePath(DATABASE_NAME)
        val database = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.beginTransaction()
            try {
                database.compileStatement(IDENTITY_INSERT).use { identity ->
                    database.compileStatement(SUMMARY_INSERT).use { summary ->
                        repeat(count) { index ->
                            val row = agedStoryIdentity(index)
                            identity.clearBindings()
                            identity.bindString(1, row.storyId)
                            identity.bindString(2, row.sourceKey)
                            identity.bindString(3, row.sourceStoryId)
                            identity.executeInsert()

                            summary.clearBindings()
                            summary.bindString(1, row.storyId)
                            summary.bindString(2, row.sourceKey)
                            summary.bindString(3, SOURCE_VERSION)
                            summary.bindString(4, "Aged unrelated story $index")
                            summary.bindString(5, "MANGA")
                            summary.bindLong(6, index.toLong())
                            summary.executeInsert()
                        }
                    }
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } finally {
            database.close()
        }
    }

    @JvmStatic
    public fun orphanRetentionCount(context: Context): Int {
        val path = context.applicationContext.getDatabasePath(DATABASE_NAME)
        val database = SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            database.rawQuery("SELECT COUNT(*) FROM story_orphan_retention", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            database.close()
        }
    }

    private const val DEFAULT_ROW_COUNT = 5_000
    private const val MAX_ROW_COUNT = 5_000
    private const val DATABASE_NAME = "hikari-v2-catalog.db"
    private const val SOURCE_VERSION = "aged-fixture-v1"
    private const val IDENTITY_INSERT =
        "INSERT INTO story_source_identity(story_id, source_key, source_story_id) VALUES (?, ?, ?)"
    private const val SUMMARY_INSERT =
        "INSERT INTO story_source_summary(" +
            "story_id, source_key, source_version, title, content_type, last_seen_epoch_ms" +
            ") VALUES (?, ?, ?, ?, ?, ?)"
}

internal data class AgedStoryIdentity(
    val storyId: String,
    val sourceKey: String,
    val sourceStoryId: String,
)

internal fun agedStoryIdentity(index: Int): AgedStoryIdentity {
    require(index >= 0)
    val suffix = index.toString().padStart(5, '0')
    return AgedStoryIdentity(
        storyId = "aged-story-$suffix",
        sourceKey = "aged-source-$suffix",
        sourceStoryId = "historical-$suffix",
    )
}
