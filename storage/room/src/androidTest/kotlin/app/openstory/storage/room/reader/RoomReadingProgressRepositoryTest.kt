package app.openstory.storage.room.reader

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.progress.ReadingProgress
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReadingProgressRepositoryTest {
    private lateinit var database: OpenStoryDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenStoryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun saveRestoresExactReleasePositionAfterRepositoryRecreation() = runTest {
        seedGraph()
        val progress = progress()
        RoomReadingProgressRepository(database).save(progress)

        val restored = RoomReadingProgressRepository(database).find(progress.storyId, progress.canonicalChapterId)

        assertEquals(progress, restored)
    }

    @Test
    fun failedWriteRollsBackAndSourceRemovalKeepsExactReleaseIdentity() = runTest {
        seedGraph()
        val repository = RoomReadingProgressRepository(database)
        val original = progress()
        repository.save(original)

        assertFails {
            repository.save(
                original.copy(canonicalChapterId = CanonicalChapterId("missing-chapter")),
            )
        }
        assertEquals(original, repository.find(original.storyId, original.canonicalChapterId))

        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM chapter_releases WHERE chapter_release_id = 'release'",
        )
        assertEquals(original, repository.find(original.storyId, original.canonicalChapterId))
    }

    private fun seedGraph() {
        database.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story', 'MANGA')")
            execSQL(
                "INSERT INTO canonical_chapters " +
                    "(canonical_chapter_id, story_id, kind, display_label, tombstoned) " +
                    "VALUES ('chapter', 'story', 'NUMBERED', 'Chapter', 0)",
            )
            execSQL(
                "INSERT INTO chapter_releases " +
                    "(chapter_release_id, story_id, plugin_id, source_story_id, source_release_id, " +
                    "display_label, kind, language_tag, canonical_chapter_id) " +
                    "VALUES ('release', 'story', 'org.example.content', 'source-story', 'source-release', " +
                    "'Chapter', 'NUMBERED', 'en', 'chapter')",
            )
        }
    }

    private fun progress() = ReadingProgress(
        StoryId("story"), CanonicalChapterId("chapter"), ChapterReleaseId("release"),
        "fingerprint", app.openstory.reader.progress.ReadingPosition("block", 12, 0.75f), null, 100,
    )
}
