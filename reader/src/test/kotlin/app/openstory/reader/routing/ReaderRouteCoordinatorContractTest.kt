package app.openstory.reader.routing

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ReaderRouteCoordinatorContractTest {
    @Test
    fun committedResultCarriesRealTargetAndSafeExactRestoration() = runTest {
        val target = CanonicalChapterId("chapter-b")
        val release = release("release-b", target)
        val document = document("fingerprint-b")
        val progress = ReadingProgress(
            storyId = StoryId("story"),
            canonicalChapterId = target,
            releaseId = release.id,
            contentFingerprint = document.fingerprint,
            position = ReadingPosition("block", 4, 0.5f),
            completedAtEpochMillis = null,
            updatedAtEpochMillis = 1L,
        )
        val coordinator = coordinator(document, progress)
        val session = ReaderRouteSessionFactory(coordinator).create(StoryId("story"))
        session.updateChapterGraph(
            listOf(group("chapter-a"), group(target, release), group("chapter-c")),
        )
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))

        val committed = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(targetChapterId = target, explicitReleaseId = release.id)),
        )

        assertEquals(target, committed.identity.targetChapterId)
        assertEquals(target, committed.chapterGroup.chapter.id)
        assertEquals(release.id, committed.release.id)
        assertEquals(ReaderExactRestoration("block", 4, 0.5f), committed.restoration)
    }

    @Test
    fun changedFingerprintDoesNotReuseExactRestoration() = runTest {
        val target = CanonicalChapterId("chapter")
        val release = release("release", target)
        val progress = ReadingProgress(
            storyId = StoryId("story"),
            canonicalChapterId = target,
            releaseId = release.id,
            contentFingerprint = "old-fingerprint",
            position = ReadingPosition("block", 4, 0.5f),
            completedAtEpochMillis = null,
            updatedAtEpochMillis = 1L,
        )
        val coordinator = coordinator(document("new-fingerprint"), progress)
        val session = ReaderRouteSessionFactory(coordinator).create(StoryId("story"))
        session.updateChapterGraph(listOf(group(target, release)))
        session.updateRoutingPreferences(ReaderPreferences())

        val committed = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(target)),
        )

        assertNull(committed.restoration)
    }

    @Test
    fun executionRegistryFailureBecomesTypedSourceUnavailable() = runTest {
        val target = CanonicalChapterId("chapter")
        val release = release("release", target)
        val coordinator = ReaderRouteCoordinator(
            store = CoordinatorStore(),
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> {
                    error("registry unavailable")
                }
            },
            progress = CoordinatorProgressRepository(null),
            sourceAvailability = ReaderSourceAvailability { setOf(release.pluginId) },
            healthRegistry = ReaderSourceHealthRegistry(),
            sourceLane = ContentSourceExecutionLane(),
            fetchArbiter = app.openstory.reader.assets.ContentFetchArbiter(),
            halfOpenProbeRegistry = ReaderHalfOpenProbeRegistry(),
            cacheFacts = ReaderCacheFactsPort { ids, _ ->
                ids.associateWith { ReaderLocalCacheFact.Miss }
            },
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNMETERED },
        )
        val session = ReaderRouteSessionFactory(coordinator).create(StoryId("story"))
        session.updateChapterGraph(listOf(group(target, release)))
        session.updateRoutingPreferences(ReaderPreferences())

        val exhausted = assertIs<ReaderForegroundResult.Exhausted>(
            session.execute(ReaderForegroundIntent(target)),
        )

        assertEquals("reader.source_unavailable", exhausted.code)
        assertEquals(false, exhausted.retryable)
        assertEquals("reader.source_unavailable", exhausted.attempts.single().code)
    }

    @Test
    fun independentlyCreatedSessionsDoNotShareGenerationOrCancellationState() = runTest {
        val target = CanonicalChapterId("chapter")
        val release = release("release", target)
        val factory = ReaderRouteSessionFactory(coordinator(document("fingerprint"), null))
        val first = factory.create(StoryId("story"))
        val second = factory.create(StoryId("story"))
        val groups = listOf(group(target, release))
        first.updateChapterGraph(groups)
        second.updateChapterGraph(groups)
        first.updateRoutingPreferences(ReaderPreferences())
        second.updateRoutingPreferences(ReaderPreferences())

        val firstResult = assertIs<ReaderForegroundResult.Committed>(first.execute(ReaderForegroundIntent(target)))
        val secondResult = assertIs<ReaderForegroundResult.Committed>(second.execute(ReaderForegroundIntent(target)))

        assertEquals(ReaderGenerationId(1), firstResult.identity.generationId)
        assertEquals(ReaderGenerationId(1), secondResult.identity.generationId)
        assertNotEquals(firstResult.identity.sessionId, secondResult.identity.sessionId)
    }

    private fun coordinator(document: ReaderDocument, progress: ReadingProgress?): ReaderRouteCoordinator =
        ReaderRouteCoordinator(
            store = CoordinatorStore(),
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> = listOf(
                    object : ReaderDocumentSource {
                        override val pluginId = PluginId("plugin")
                        override suspend fun fetch(release: ChapterRelease) = ReaderSourceResult.Success(document)
                    },
                )
            },
            progress = CoordinatorProgressRepository(progress),
            healthRegistry = ReaderSourceHealthRegistry(),
            sourceLane = ContentSourceExecutionLane(),
            fetchArbiter = app.openstory.reader.assets.ContentFetchArbiter(),
            halfOpenProbeRegistry = ReaderHalfOpenProbeRegistry(),
        )

    private fun group(id: String) = group(CanonicalChapterId(id))

    private fun group(id: CanonicalChapterId, vararg releases: ChapterRelease): CanonicalChapterGroup =
        CanonicalChapterGroup(
            chapter = CanonicalChapter(
                id = id,
                storyId = StoryId("story"),
                parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
                displayLabel = id.value,
                tombstoned = false,
                releaseIds = releases.map { it.id }.toSet(),
            ),
            releases = releases.toList(),
        )

    private fun release(
        id: String,
        chapterId: CanonicalChapterId,
    ) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = StoryId("story"),
        pluginId = PluginId("plugin"),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$id",
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapterId,
    )

    private fun document(fingerprint: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", "text")),
        fingerprint = fingerprint,
    )
}

private class CoordinatorStore : ReaderDocumentStore {
    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = null
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}

private class CoordinatorProgressRepository(
    private val progress: ReadingProgress?,
) : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(listOfNotNull(progress))
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(progress)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = progress
    override suspend fun save(progress: ReadingProgress) = Unit
}
