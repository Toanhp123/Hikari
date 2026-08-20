package app.openstory.storage.room.reader

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.progress.ReadingProgress
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.first
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

    @Test
    fun storyScopedObservationExcludesUnrelatedProgress() = runTest {
        seedGraph("story-a", "chapter-a", "release-a")
        seedGraph("story-b", "chapter-b", "release-b")
        val repository = RoomReadingProgressRepository(database)
        repository.save(progress("story-a", "chapter-a", "release-a", updatedAt = 100))
        repository.save(progress("story-b", "chapter-b", "release-b", updatedAt = 200))

        val observed = repository.observeForStories(setOf(StoryId("story-a"))).first()

        assertEquals(listOf("story-a"), observed.map { it.storyId.value })
        assertEquals(emptyList(), repository.observeForStories(emptySet()).first())
    }

    @Test
    fun observeAllOrdersByUpdateDescendingThenStoryAndChapterIdentity() = runTest {
        seedGraph("story-b", "chapter-b", "release-b")
        seedGraph("story-a", "chapter-z", "release-z")
        seedGraph("story-a", "chapter-a", "release-a")
        seedGraph()
        val repository = RoomReadingProgressRepository(database)
        repository.save(progress("story-b", "chapter-b", "release-b", updatedAt = 200))
        repository.save(progress("story-a", "chapter-z", "release-z", updatedAt = 200))
        repository.save(progress("story-a", "chapter-a", "release-a", updatedAt = 200))
        repository.save(progress("story", "chapter", "release", updatedAt = 100))

        val observed = repository.observeAll().first()

        assertEquals(
            listOf(
                "story-a/chapter-a",
                "story-a/chapter-z",
                "story-b/chapter-b",
                "story/chapter",
            ),
            observed.map { "${it.storyId.value}/${it.canonicalChapterId.value}" },
        )
    }

    private fun seedGraph() {
        seedGraph("story", "chapter", "release")
    }

    private fun seedGraph(storyId: String, chapterId: String, releaseId: String) {
        database.openHelper.writableDatabase.apply {
            execSQL("INSERT OR IGNORE INTO stories (story_id, content_type) VALUES (?, 'MANGA')", arrayOf(storyId))
            execSQL(
                "INSERT INTO canonical_chapters " +
                    "(canonical_chapter_id, story_id, kind, display_label, tombstoned) " +
                    "VALUES (?, ?, 'NUMBERED', 'Chapter', 0)",
                arrayOf(chapterId, storyId),
            )
            execSQL(
                "INSERT INTO chapter_releases " +
                    "(chapter_release_id, story_id, plugin_id, source_story_id, source_release_id, " +
                    "display_label, kind, language_tag, canonical_chapter_id) " +
                    "VALUES (?, ?, 'org.example.content', ?, ?, " +
                    "'Chapter', 'NUMBERED', 'en', ?)",
                arrayOf(releaseId, storyId, "source-$storyId", "source-$releaseId", chapterId),
            )
        }
    }

    private fun progress() = ReadingProgress(
        StoryId("story"), CanonicalChapterId("chapter"), ChapterReleaseId("release"),
        "fingerprint", app.openstory.reader.progress.ReadingPosition("block", 12, 0.75f), null, 100,
    )

    private fun progress(storyId: String, chapterId: String, releaseId: String, updatedAt: Long) =
        ReadingProgress(
            StoryId(storyId), CanonicalChapterId(chapterId), ChapterReleaseId(releaseId),
            "fingerprint-$releaseId",
            app.openstory.reader.progress.ReadingPosition("block", 0, 0.5f),
            null,
            updatedAt,
        )
}
