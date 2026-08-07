package app.openstory.database

import android.content.Context
import kotlin.test.assertEquals

internal suspend fun assertPluginRemovalAfterReopen(
    context: Context,
) {
    withCheckpointDatabase(
        context = context,
        databaseName = PLUGIN_REMOVAL_DATABASE_NAME,
    ) { database ->
        assertEquals(
            1,
            rowCount(database, "SELECT COUNT(*) FROM canonical_stories"),
        )
        assertEquals(
            2,
            rowCount(database, "SELECT COUNT(*) FROM content_mappings"),
        )
        assertEquals(
            1,
            rowCount(database, "SELECT COUNT(*) FROM chapter_releases"),
        )
        assertEquals(
            1,
            rowCount(database, "SELECT COUNT(*) FROM reading_progress"),
        )
        assertNoForeignKeyViolations(database)
    }
}
