package app.openstory.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.chapters.aggregation.AggregationPlan
import app.openstory.chapters.aggregation.ChapterReleaseLink
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Outcome
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryStatus
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BenchmarkFixtureActivity : ComponentActivity() {
    @Inject lateinit var catalog: CatalogRepository
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var chapters: ChapterRepository
    @Inject lateinit var documents: ReaderDocumentStore
    @Inject lateinit var progress: ReadingProgressRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = BENCHMARK_SEEDING_TEXT }
        setContentView(status)
        lifecycleScope.launch {
            runCatching { seedFixture() }
                .onSuccess { status.text = BENCHMARK_READY_TEXT }
                .onFailure { error -> status.text = "$BENCHMARK_FAILED_PREFIX${error::class.java.simpleName}" }
        }
    }

    private suspend fun seedFixture() {
        seedBrowseFixtures()

        val storyId = StoryId(BENCHMARK_STORY_ID)
        val catalogResult = catalog.commitDetails(
            CatalogDetailsMutation(
                storyId = storyId,
                entry = CatalogEntry(
                    storyId = storyId,
                    pluginId = PluginId(BENCHMARK_PLUGIN_ID),
                    sourceId = BENCHMARK_SOURCE_ID,
                    title = BENCHMARK_STORY_TITLE,
                    authors = setOf("Hikari"),
                    description = "Deterministic local story used only by benchmarkRelease.",
                    contentType = ContentType.MANGA,
                    languageTags = setOf("en"),
                ),
                pluginVersion = BENCHMARK_PLUGIN_VERSION,
                resolvedAtEpochMillis = BENCHMARK_EPOCH_MILLIS,
            ),
        )
        check(catalogResult is Outcome.Success)
        library.add(storyId, LibraryStatus.READING, BENCHMARK_EPOCH_MILLIS)
        check(
            library.changeStatus(
                storyId,
                LibraryStatus.READING,
                BENCHMARK_EPOCH_MILLIS + BENCHMARK_PRIMARY_ACTIVITY_OFFSET,
            ) != null,
        )

        val chapterFixtures = (1..BENCHMARK_CHAPTER_COUNT).map { index -> chapterFixture(storyId, index) }
        val commit = chapters.commit(
            ChapterMutation(
                storyId = storyId,
                releases = chapterFixtures.map(ChapterFixture::release),
                plan = AggregationPlan(
                    creates = chapterFixtures.map(ChapterFixture::chapter),
                    links = chapterFixtures.map { fixture ->
                        ChapterReleaseLink(fixture.release.id, fixture.chapter.id)
                    },
                    unlinks = emptySet(),
                    tombstones = emptySet(),
                    reviewCandidates = emptyList(),
                ),
            ),
        )
        check(commit == ChapterCommitResult.Success)

        chapterFixtures.forEach { fixture ->
            val document = benchmarkDocument(fixture.index)
            documents.write(fixture.release.id, document.fingerprint, document)
            check(documents.read(fixture.release.id, document.fingerprint) == document) {
                "Benchmark Reader document was not persisted for chapter ${fixture.index}."
            }
            progress.save(
                ReadingProgress(
                    storyId = storyId,
                    canonicalChapterId = fixture.chapter.id,
                    releaseId = fixture.release.id,
                    contentFingerprint = document.fingerprint,
                    position = ReadingPosition(document.blocks.first().id, 0, 0f),
                    completedAtEpochMillis = if (fixture.index == BENCHMARK_RESUME_CHAPTER_INDEX) {
                        null
                    } else {
                        BENCHMARK_EPOCH_MILLIS
                    },
                    updatedAtEpochMillis =
                        BENCHMARK_EPOCH_MILLIS + (BENCHMARK_CHAPTER_COUNT - fixture.index),
                ),
            )
        }
    }


    private suspend fun seedBrowseFixtures() {
        val pluginId = PluginId(BENCHMARK_PLUGIN_ID)
        val entries = List(BENCHMARK_BROWSE_STORY_COUNT) { index ->
            val storyId = StoryId("benchmark-browse-story-$index")
            CatalogEntry(
                storyId = storyId,
                pluginId = pluginId,
                sourceId = "benchmark-browse-source-$index",
                title = "Benchmark Browse Story ${index + 1}",
                authors = setOf("Hikari"),
                description = "Deterministic browse fixture ${index + 1} for scroll macrobenchmarks.",
                genres = setOf("Fantasy", "Adventure"),
                contentType = ContentType.MANGA,
                languageTags = setOf("en"),
                score = Score(10.0 - index * 0.1, 10.0),
                popularityRank = (index + 1).toLong(),
                publicationStatus = if (index % 5 == 0) {
                    PublicationStatus.COMPLETED
                } else {
                    PublicationStatus.ONGOING
                },
                latestUpdate = CatalogLatestUpdate(
                    atEpochMillis = BENCHMARK_EPOCH_MILLIS - index,
                    releaseLabel = (BENCHMARK_BROWSE_STORY_COUNT - index).toString(),
                ),
            )
        }
        val sections = listOf(
            CatalogHomeSection(
                sourceId = "benchmark-popular",
                title = "Popular",
                items = entries.take(5),
                kind = CatalogFeedKind.POPULAR,
            ),
            CatalogHomeSection(
                sourceId = "benchmark-latest",
                title = "Latest Updates",
                items = entries.take(9),
                kind = CatalogFeedKind.LATEST_UPDATES,
            ),
            CatalogHomeSection(
                sourceId = "benchmark-top-rated",
                title = "Top Rated",
                items = entries.take(5),
                kind = CatalogFeedKind.TOP_RATED,
            ),
            CatalogHomeSection(
                sourceId = "benchmark-other",
                title = "Benchmark Remainder",
                items = entries.drop(9),
                kind = CatalogFeedKind.OTHER,
            ),
        )
        val homeResult = catalog.commitHomeRefresh(
            CatalogHomeMutation(
                pluginId = pluginId,
                pluginVersion = BENCHMARK_PLUGIN_VERSION,
                refreshedAtEpochMillis = BENCHMARK_EPOCH_MILLIS,
                stories = entries.map { entry -> Story(entry.storyId, entry.contentType) },
                entries = entries,
                sections = sections,
                orderedSourceItemIds = sections.associate { section ->
                    section.sourceId to section.items.map(CatalogEntry::sourceId)
                },
            ),
        )
        check(homeResult is Outcome.Success)
        entries.forEachIndexed { index, entry ->
            val activityAt = BENCHMARK_EPOCH_MILLIS - index - 1
            library.add(entry.storyId, LibraryStatus.WANT_TO_READ, activityAt)
            check(
                library.changeStatus(entry.storyId, LibraryStatus.WANT_TO_READ, activityAt) != null,
            )
        }
    }

    private fun chapterFixture(storyId: StoryId, index: Int): ChapterFixture {
        val chapterId = CanonicalChapterId("$BENCHMARK_STORY_ID:chapter:$index")
        val releaseId = ChapterReleaseId("$BENCHMARK_STORY_ID:release:$index")
        val parsed = ParsedChapterLabel(
            kind = ChapterKind.NUMBERED,
            volume = null,
            chapter = BigDecimal.valueOf(index.toLong()),
            part = null,
            normalizedTitle = null,
        )
        return ChapterFixture(
            index = index,
            chapter = CanonicalChapter(
                id = chapterId,
                storyId = storyId,
                parsedLabel = parsed,
                displayLabel = "Chapter $index",
                tombstoned = false,
                releaseIds = setOf(releaseId),
            ),
            release = ChapterRelease(
                id = releaseId,
                storyId = storyId,
                pluginId = PluginId(BENCHMARK_PLUGIN_ID),
                sourceStoryId = BENCHMARK_SOURCE_ID,
                sourceReleaseId = "chapter-$index",
                displayLabel = "Chapter $index",
                parsedLabel = parsed,
                languageTag = "en",
                publishedAtEpochMillis = BENCHMARK_EPOCH_MILLIS + index,
                canonicalChapterId = chapterId,
            ),
        )
    }

    private fun benchmarkDocument(index: Int): ReaderDocument {
        val blocks = List(BENCHMARK_PARAGRAPH_COUNT) { blockIndex ->
            ReaderBlock.Paragraph(
                id = "benchmark-$index-block-$blockIndex",
                text = "Benchmark chapter $index paragraph $blockIndex. " + BENCHMARK_PARAGRAPH_BODY,
            )
        }
        return ReaderDocument(
            title = "Benchmark Chapter $index",
            blocks = blocks,
            fingerprint = "benchmark-fixture-fingerprint-$index",
        )
    }

    private data class ChapterFixture(
        val index: Int,
        val chapter: CanonicalChapter,
        val release: ChapterRelease,
    )

    private companion object {
        const val BENCHMARK_STORY_ID = "benchmark-fixture-story"
        const val BENCHMARK_PLUGIN_ID = "benchmark-fixture"
        const val BENCHMARK_SOURCE_ID = "benchmark-fixture-source"
        const val BENCHMARK_PLUGIN_VERSION = "1.0.0"
        const val BENCHMARK_STORY_TITLE = "Hikari Benchmark Fixture"
        const val BENCHMARK_CHAPTER_COUNT = 12
        const val BENCHMARK_BROWSE_STORY_COUNT = 30
        const val BENCHMARK_RESUME_CHAPTER_INDEX = 1
        const val BENCHMARK_PARAGRAPH_COUNT = 24
        const val BENCHMARK_EPOCH_MILLIS = 1_700_000_000_000L
        const val BENCHMARK_PRIMARY_ACTIVITY_OFFSET = 1_000L
        const val BENCHMARK_SEEDING_TEXT = "HIKARI_BENCHMARK_SEEDING"
        const val BENCHMARK_READY_TEXT = "HIKARI_BENCHMARK_READY"
        const val BENCHMARK_FAILED_PREFIX = "HIKARI_BENCHMARK_FAILED:"
        const val BENCHMARK_PARAGRAPH_BODY =
            "This deterministic local content keeps Reader measurement independent from network and plugin state."
    }
}
