package app.openstory.storage.room.chapters

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.chapters.aggregation.AggregationPlan
import app.openstory.chapters.aggregation.ChapterReleaseLink
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomChapterRepositoryTest {
    @Test
    fun aggregationCommitIsAtomicAndRollsBackInvalidLinks() = runTest {
        withRepository { database, repository ->
            val release = release("release-1", "1")
            val invalid = mutation(
                releases = listOf(release),
                creates = emptyList(),
                links = listOf(ChapterReleaseLink(release.id, CanonicalChapterId("missing"))),
            )

            assertIs<ChapterCommitResult.Failure>(repository.commit(invalid))
            assertTrue(repository.snapshot(STORY_ID).releases.isEmpty())
            assertEquals(0, database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM chapter_releases").use {
                it.moveToFirst()
                it.getInt(0)
            })
        }
    }

    @Test
    fun canonicalGroupsUseNumericOrderingAndExpandReleases() = runTest {
        withRepository { _, repository ->
            val chapter10 = chapter("chapter-10", "10")
            val chapter2 = chapter("chapter-2", "2")
            val release10 = release("release-10", "10")
            val release2 = release("release-2", "2")

            assertIs<ChapterCommitResult.Success>(
                repository.commit(
                    mutation(
                        releases = listOf(release10, release2),
                        creates = listOf(chapter10, chapter2),
                        links = listOf(
                            ChapterReleaseLink(release10.id, chapter10.id),
                            ChapterReleaseLink(release2.id, chapter2.id),
                        ),
                    ),
                ),
            )

            val groups = repository.observe(STORY_ID).first()
            assertEquals(listOf("2", "10"), groups.map { it.chapter.parsedLabel.chapter!!.toPlainString() })
            assertEquals(listOf("release-2"), groups.first().releases.map { it.id.value })
        }
    }

    @Test
    fun tombstonesAndProtectedOverridesSurviveLaterCommits() = runTest {
        withRepository { _, repository ->
            val chapter = chapter("chapter-1", "1")
            val release = release("release-1", "1")
            repository.commit(
                mutation(
                    releases = listOf(release),
                    creates = listOf(chapter),
                    links = listOf(ChapterReleaseLink(release.id, chapter.id)),
                ),
            )
            val override = ChapterAggregationOverride(release.id, chapter.id, ChapterOverrideKind.FORCE_LINK)
            repository.saveOverride(STORY_ID, override)

            repository.commit(
                mutation(
                    releases = emptyList(),
                    tombstones = setOf(chapter.id),
                ),
            )

            val snapshot = repository.snapshot(STORY_ID)
            assertTrue(snapshot.chapters.single().tombstoned)
            assertEquals(listOf(override), snapshot.overrides)
            assertFalse(snapshot.releases.isEmpty())
        }
    }

    private suspend fun withRepository(
        block: suspend (OpenStoryDatabase, RoomChapterRepository) -> Unit,
    ) {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO stories (story_id, content_type) VALUES (?, ?)",
                arrayOf(STORY_ID.value, "MANGA"),
            )
            block(database, RoomChapterRepository(database))
        } finally {
            database.close()
        }
    }

    private fun mutation(
        releases: List<ChapterRelease>,
        creates: List<CanonicalChapter> = emptyList(),
        links: List<ChapterReleaseLink> = emptyList(),
        tombstones: Set<CanonicalChapterId> = emptySet(),
    ) = ChapterMutation(
        storyId = STORY_ID,
        releases = releases,
        plan = AggregationPlan(creates, links, emptySet(), tombstones, emptyList()),
    )

    private fun chapter(id: String, number: String) = CanonicalChapter(
        id = CanonicalChapterId(id),
        storyId = STORY_ID,
        parsedLabel = numbered(number),
        displayLabel = "Chapter $number",
        tombstoned = false,
    )

    private fun release(id: String, number: String) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = STORY_ID,
        pluginId = PluginId(PLUGIN_ID),
        sourceStoryId = SOURCE_STORY_ID,
        sourceReleaseId = id,
        displayLabel = "Chapter $number",
        parsedLabel = numbered(number),
        languageTag = "en",
        publishedAtEpochMillis = null,
        canonicalChapterId = null,
    )

    private fun numbered(number: String) = ParsedChapterLabel(
        kind = ChapterKind.NUMBERED,
        volume = null,
        chapter = BigDecimal(number),
        part = null,
        normalizedTitle = null,
    )

    private companion object {
        val STORY_ID = StoryId("story:chapters")
        const val PLUGIN_ID = "org.example.content"
        const val SOURCE_STORY_ID = "source-story"
    }
}
