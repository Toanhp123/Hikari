package app.openstory.storage.room.library

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentMappingMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationTwoToThreePreservesMembershipAndAddsMappingState() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO stories (story_id, content_type) VALUES (?, ?)",
                arrayOf(STORY_ID, "LIGHT_NOVEL"),
            )
            execSQL(
                "INSERT INTO library_entries " +
                    "(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(STORY_ID, LibraryStatus.READING.name, 10L, 20L),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            RoomMigrations.MIGRATION_2_3,
        ).use { database ->
            val status = database.query(
                "SELECT status FROM library_entries WHERE story_id = ?",
                arrayOf(STORY_ID),
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            assertEquals(LibraryStatus.READING.name, status)

            database.execSQL(
                "INSERT INTO content_mappings " +
                    "(story_id, plugin_id, source_story_id, origin, policy_version, updated_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(STORY_ID, PLUGIN_ID, "source-1", "AUTOMATED", 1, 30L),
            )
            val sourceStoryId = database.query(
                "SELECT source_story_id FROM content_mappings WHERE story_id = ? AND plugin_id = ?",
                arrayOf(STORY_ID, PLUGIN_ID),
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            assertEquals("source-1", sourceStoryId)
        }
    }

    @Test
    fun roomCompareAndWriteProtectsUserMappingAndKeepsApprovalIdempotent() = runTest {
        withRepository { repository ->
            val approved = mapping("chosen", ContentMappingOrigin.USER_APPROVED, updatedAt = 10L)
            val first = repository.compareAndWrite(approved, ContentMappingOrigin.entries.toSet())
            val automatic = repository.compareAndWrite(
                mapping("other", ContentMappingOrigin.AUTOMATED, updatedAt = 20L),
                setOf(ContentMappingOrigin.AUTOMATED),
            )
            val repeated = repository.compareAndWrite(
                approved.copy(updatedAt = 30L),
                ContentMappingOrigin.entries.toSet(),
            )

            assertTrue(assertIs<ContentMappingWriteResult.Written>(first).changed)
            assertEquals("chosen", assertIs<ContentMappingWriteResult.Protected>(automatic).mapping.sourceStoryId)
            val repeatedWrite = assertIs<ContentMappingWriteResult.Written>(repeated)
            assertFalse(repeatedWrite.changed)
            assertEquals(10L, repeatedWrite.mapping.updatedAt)
            assertEquals(listOf(approved), repository.observe(StoryId(STORY_ID)).first())
        }
    }

    @Test
    fun rejectionIsScopedToCandidateAndPolicyVersion() = runTest {
        withRepository { repository ->
            repository.reject(
                ContentMappingRejection(
                    storyId = StoryId(STORY_ID),
                    pluginId = PluginId(PLUGIN_ID),
                    sourceStoryId = "candidate",
                    policyVersion = 1,
                    rejectedAt = 40L,
                ),
            )

            assertTrue(repository.isRejected(StoryId(STORY_ID), PluginId(PLUGIN_ID), "candidate", 1))
            assertFalse(repository.isRejected(StoryId(STORY_ID), PluginId(PLUGIN_ID), "candidate", 2))
            assertFalse(repository.isRejected(StoryId(STORY_ID), PluginId(PLUGIN_ID), "other", 1))
        }
    }

    private suspend fun withRepository(block: suspend (RoomContentMappingRepository) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, OpenStoryDatabase::class.java).build()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO stories (story_id, content_type) VALUES (?, ?)",
                arrayOf(STORY_ID, "LIGHT_NOVEL"),
            )
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO library_entries " +
                    "(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(STORY_ID, LibraryStatus.READING.name, 10L, 10L),
            )
            block(RoomContentMappingRepository(database))
        } finally {
            database.close()
        }
    }

    private fun mapping(
        sourceStoryId: String,
        origin: ContentMappingOrigin,
        updatedAt: Long,
    ) = ContentMapping(
        storyId = StoryId(STORY_ID),
        pluginId = PluginId(PLUGIN_ID),
        sourceStoryId = sourceStoryId,
        origin = origin,
        policyVersion = 1,
        updatedAt = updatedAt,
    )

    private companion object {
        const val TEST_DATABASE = "content-mapping-migration-test"
        const val STORY_ID = "story:content-mapping"
        const val PLUGIN_ID = "org.example.reader"
    }
}
