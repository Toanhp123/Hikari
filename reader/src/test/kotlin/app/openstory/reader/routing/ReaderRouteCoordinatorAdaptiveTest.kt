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

class ReaderRouteCoordinatorAdaptiveTest {
    @Test
    fun exactLocalWinnerCommitsWithoutTouchingRemoteTransport() = runTest {
        val document = document("local-fp")
        val store = AdaptiveCoordinatorStore(mapOf(releaseId to document))
        val source = AdaptiveCoordinatorSource(document("remote-fp"))
        val coordinator = coordinator(
            store = store,
            source = source,
            cacheFacts = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Exact("local-fp") } },
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNMETERED },
        )
        val session = ready(coordinator)

        val result = assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(true, result.fromLocal)
        assertEquals("local-fp", result.document.fingerprint)
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun offlineWithCacheMissRejectsRemoteBeforeTransport() = runTest {
        val source = AdaptiveCoordinatorSource(document("remote-fp"))
        val coordinator = coordinator(
            store = AdaptiveCoordinatorStore(),
            source = source,
            cacheFacts = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Miss } },
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.OFFLINE },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Exhausted>(session.execute(ReaderForegroundIntent(chapterId)))
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun resumeFingerprintIsNotRemoteIntegrityExpectation() = runTest {
        val source = AdaptiveCoordinatorSource(document("new-remote-fp"))
        val progress = ReadingProgress(
            storyId = StoryId("story"),
            canonicalChapterId = chapterId,
            releaseId = releaseId,
            contentFingerprint = "old-resume-fp",
            position = ReadingPosition("block", 0, 0.4f),
            completedAtEpochMillis = null,
            updatedAtEpochMillis = 1L,
        )
        val coordinator = coordinator(
            store = AdaptiveCoordinatorStore(),
            source = source,
            cacheFacts = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Miss } },
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNMETERED },
            progress = progress,
        )
        val session = ready(coordinator)

        val result = assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals("new-remote-fp", result.document.fingerprint)
        assertEquals(null, result.restoration)
        assertEquals(1, source.fetchCount)
    }

    private fun coordinator(
        store: ReaderDocumentStore,
        source: AdaptiveCoordinatorSource,
        cacheFacts: ReaderCacheFactsPort,
        networkFacts: ReaderNetworkFactsPort,
        progress: ReadingProgress? = null,
    ) = ReaderRouteCoordinator(
        store = store,
        sources = object : ReaderDocumentSourceRegistry {
            override suspend fun enabled(): List<ReaderDocumentSource> = listOf(source)
        },
        progress = AdaptiveCoordinatorProgress(progress),
        sourceAvailability = ReaderSourceAvailability { setOf(sourceId) },
        healthRegistry = ReaderSourceHealthRegistry(),
        executionLimiter = ReaderSourceExecutionLimiter(),
        cacheFacts = cacheFacts,
        networkFacts = networkFacts,
    )

    private suspend fun ready(coordinator: ReaderRouteCoordinator): ReaderRouteSession {
        val session = ReaderRouteSessionFactory(coordinator).create(StoryId("story"))
        session.updateChapterGraph(listOf(group()))
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))
        return session
    }

    private fun group() = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = chapterId,
            storyId = StoryId("story"),
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            displayLabel = "chapter",
            tombstoned = false,
            releaseIds = setOf(releaseId),
        ),
        releases = listOf(release()),
    )

    private fun release() = ChapterRelease(
        id = releaseId,
        storyId = StoryId("story"),
        pluginId = sourceId,
        sourceStoryId = "source-story",
        sourceReleaseId = "source-release",
        displayLabel = "release",
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapterId,
    )

    private fun document(fp: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", "text")),
        fingerprint = fp,
    )

    private companion object {
        val chapterId = CanonicalChapterId("chapter")
        val releaseId = ChapterReleaseId("release")
        val sourceId = PluginId("source")
    }
}

private class AdaptiveCoordinatorStore(
    private val exact: Map<ChapterReleaseId, ReaderDocument> = emptyMap(),
) : ReaderDocumentStore {
    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? =
        exact[releaseId]?.takeIf { it.fingerprint == fingerprint }
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = exact[releaseId]
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}

private class AdaptiveCoordinatorSource(
    private val document: ReaderDocument,
) : ReaderDocumentSource {
    override val pluginId = PluginId("source")
    var fetchCount: Int = 0
    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetchCount += 1
        return ReaderSourceResult.Success(document)
    }
}

private class AdaptiveCoordinatorProgress(
    private val value: ReadingProgress?,
) : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(listOfNotNull(value))
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(value)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = value
    override suspend fun save(progress: ReadingProgress) = Unit
}
