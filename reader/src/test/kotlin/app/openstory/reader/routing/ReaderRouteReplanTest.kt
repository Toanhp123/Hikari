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
import app.openstory.reader.content.ReaderDocumentReadResult
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderRouteReplanTest {
    @Test
    fun sourceBecomingUnavailableAfterPlanSnapshotHardReplansBeforeRemoteEffect() = runTest {
        var availabilityReads = 0
        val source = ReplanSource { ReaderSourceResult.Success(document("remote")) }
        val coordinator = coordinator(
            source = source,
            availability = ReaderSourceAvailability {
                availabilityReads += 1
                if (availabilityReads == 1) setOf(sourceId) else emptySet()
            },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Exhausted>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(0, source.fetchCount)
        assertEquals(ReaderPlanRevision(1), assertIs<ReaderExecutionState.Exhausted>(session.executionState).identity.planRevision)
    }

    @Test
    fun circuitOpeningAfterPlanSnapshotHardReplansBeforeRemoteEffect() = runTest {
        val health = ReaderSourceHealthRegistry()
        var availabilityReads = 0
        val source = ReplanSource { ReaderSourceResult.Success(document("remote")) }
        val coordinator = coordinator(
            source = source,
            health = health,
            availability = ReaderSourceAvailability {
                availabilityReads += 1
                if (availabilityReads == 2) {
                    repeat(3) {
                        health.record(
                            SourceOperationKey(sourceId),
                            SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
                            nowEpochMillis = 10L + it,
                        )
                    }
                }
                setOf(sourceId)
            },
            now = { 100L },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Exhausted>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(0, source.fetchCount)
        assertEquals(ReaderPlanRevision(1), assertIs<ReaderExecutionState.Exhausted>(session.executionState).identity.planRevision)
    }

    @Test
    fun softClosedHealthChangeDoesNotReplanActiveRoute() = runTest {
        val health = ReaderSourceHealthRegistry()
        var availabilityReads = 0
        val source = ReplanSource { ReaderSourceResult.Success(document("remote")) }
        val coordinator = coordinator(
            source = source,
            health = health,
            availability = ReaderSourceAvailability {
                availabilityReads += 1
                if (availabilityReads == 2) {
                    health.record(
                        SourceOperationKey(sourceId),
                        SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
                        nowEpochMillis = 10L,
                    )
                }
                setOf(sourceId)
            },
            now = { 100L },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(1, source.fetchCount)
        assertEquals(ReaderPlanRevision(0), assertIs<ReaderExecutionState.Committed>(session.executionState).identity.planRevision)
    }

    @Test
    fun connectionFailureThatResamplesDefinitelyOfflineHardReplansOnce() = runTest {
        var networkReads = 0
        val source = ReplanSource { ReaderSourceResult.Failure("plugin.http_request_failed", true) }
        val coordinator = coordinator(
            source = source,
            availability = ReaderSourceAvailability { setOf(sourceId) },
            network = ReaderNetworkFactsPort {
                networkReads += 1
                if (networkReads == 1) ReaderNetworkState.UNMETERED else ReaderNetworkState.OFFLINE
            },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Exhausted>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(1, source.fetchCount)
        assertEquals(ReaderPlanRevision(1), assertIs<ReaderExecutionState.Exhausted>(session.executionState).identity.planRevision)
    }

    @Test
    fun definitelyOfflineConnectionFailureAbortsStaleRemoteFallbacksBeforeReplan() = runTest {
        var networkReads = 0
        val sourceA = ReplanSource(PluginId("a")) {
            ReaderSourceResult.Failure("plugin.http_request_failed", true)
        }
        val sourceB = ReplanSource(PluginId("b")) {
            ReaderSourceResult.Success(document("remote-b"))
        }
        val coordinator = ReaderRouteCoordinator(
            store = ReplanStore(),
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> = listOf(sourceA, sourceB)
            },
            progress = NoopReplanProgress,
            sourceAvailability = ReaderSourceAvailability { setOf(PluginId("a"), PluginId("b")) },
            healthRegistry = ReaderSourceHealthRegistry(),
            executionLimiter = ReaderSourceExecutionLimiter(),
            cacheFacts = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Miss } },
            networkFacts = ReaderNetworkFactsPort {
                networkReads += 1
                if (networkReads == 1) ReaderNetworkState.UNMETERED else ReaderNetworkState.OFFLINE
            },
            nowEpochMillis = { 100L },
        )
        val session = ReaderRouteSessionFactory(coordinator).create(StoryId("story"))
        session.updateChapterGraph(
            listOf(
                groupOf(
                    listOf(
                        releaseFor("release-a", "a"),
                        releaseFor("release-b", "b"),
                    ),
                ),
            ),
        )
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))

        assertIs<ReaderForegroundResult.Exhausted>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(1, sourceA.fetchCount)
        assertEquals(0, sourceB.fetchCount)
        assertEquals(ReaderPlanRevision(1), assertIs<ReaderExecutionState.Exhausted>(session.executionState).identity.planRevision)
    }

    @Test
    fun confirmedLocalInvalidityUsesPlannedSameReleaseRemoteRecoveryWithoutBlindReplan() = runTest {
        val store = ReplanStore(document("different-fingerprint"))
        val source = ReplanSource { ReaderSourceResult.Success(document("remote-valid")) }
        val coordinator = coordinator(
            source = source,
            store = store,
            availability = ReaderSourceAvailability { setOf(sourceId) },
            cache = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Exact("expected-fingerprint") } },
        )
        val session = ready(coordinator)

        val result = assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals("remote-valid", result.document.fingerprint)
        assertEquals(1, source.fetchCount)
        assertEquals(ReaderPlanRevision(0), assertIs<ReaderExecutionState.Committed>(session.executionState).identity.planRevision)
    }

    @Test
    fun openRemoteSourceKeepsValidLocalPathUsableWithoutTransport() = runTest {
        val health = ReaderSourceHealthRegistry()
        repeat(3) { index ->
            health.record(
                SourceOperationKey(sourceId),
                SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
                nowEpochMillis = index.toLong(),
            )
        }
        val local = document("expected-fingerprint")
        val store = ReplanStore(exact = local)
        val source = ReplanSource { ReaderSourceResult.Success(document("remote-must-not-run")) }
        val coordinator = coordinator(
            source = source,
            store = store,
            availability = ReaderSourceAvailability { setOf(sourceId) },
            health = health,
            cache = ReaderCacheFactsPort { ids, _ ->
                ids.associateWith { ReaderLocalCacheFact.Exact("expected-fingerprint") }
            },
            now = { 100L },
        )
        val session = ready(coordinator)

        val result = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(chapterId)),
        )

        assertEquals(true, result.fromLocal)
        assertEquals("expected-fingerprint", result.document.fingerprint)
        assertEquals(0, source.fetchCount)
        assertEquals(
            ReaderPlanRevision(0),
            assertIs<ReaderExecutionState.Committed>(session.executionState).identity.planRevision,
        )
    }

    @Test
    fun typedMissingDoesNotBecomeKnownInvalidForLaterSnapshot() = runTest {
        val store = ReplanStore(typedResult = ReaderDocumentReadResult.Missing)
        val source = ReplanSource { ReaderSourceResult.Success(document("remote-valid")) }
        val coordinator = coordinator(
            source = source,
            store = store,
            availability = ReaderSourceAvailability { setOf(sourceId) },
            cache = ReaderCacheFactsPort { ids, _ ->
                ids.associateWith { ReaderLocalCacheFact.Exact("expected-fingerprint") }
            },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))
        assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(2, store.readResultCount)
        assertEquals(2, source.fetchCount)
    }

    @Test
    fun confirmedTypedCorruptionIsExcludedFromALaterSnapshot() = runTest {
        val store = ReplanStore(
            typedResult = ReaderDocumentReadResult.FingerprintOrDecodeMismatch,
        )
        val source = ReplanSource { ReaderSourceResult.Success(document("remote-valid")) }
        val coordinator = coordinator(
            source = source,
            store = store,
            availability = ReaderSourceAvailability { setOf(sourceId) },
            cache = ReaderCacheFactsPort { ids, _ ->
                ids.associateWith { ReaderLocalCacheFact.Exact("expected-fingerprint") }
            },
        )
        val session = ready(coordinator)

        assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))
        assertIs<ReaderForegroundResult.Committed>(session.execute(ReaderForegroundIntent(chapterId)))

        assertEquals(1, store.readResultCount)
        assertEquals(2, source.fetchCount)
    }

    private fun coordinator(
        source: ReplanSource,
        store: ReaderDocumentStore = ReplanStore(),
        availability: ReaderSourceAvailability,
        health: ReaderSourceHealthRegistry = ReaderSourceHealthRegistry(),
        network: ReaderNetworkFactsPort = ReaderNetworkFactsPort { ReaderNetworkState.UNMETERED },
        cache: ReaderCacheFactsPort = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Miss } },
        now: () -> Long = { 100L },
        limiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
    ) = ReaderRouteCoordinator(
        store = store,
        sources = object : ReaderDocumentSourceRegistry {
            override suspend fun enabled(): List<ReaderDocumentSource> = listOf(source)
        },
        progress = NoopReplanProgress,
        sourceAvailability = availability,
        healthRegistry = health,
        executionLimiter = limiter,
        cacheFacts = cache,
        networkFacts = network,
        nowEpochMillis = now,
    )

    private suspend fun ready(coordinator: ReaderRouteCoordinator): ReaderRouteSession {
        val session = ReaderRouteSessionFactory(coordinator).create(StoryId("story"))
        session.updateChapterGraph(listOf(group()))
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))
        return session
    }

    private fun groupOf(releases: List<ChapterRelease>) = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = chapterId,
            storyId = StoryId("story"),
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            displayLabel = "chapter",
            tombstoned = false,
            releaseIds = releases.mapTo(linkedSetOf()) { it.id },
        ),
        releases = releases,
    )

    private fun releaseFor(id: String, source: String) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = StoryId("story"),
        pluginId = PluginId(source),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$id",
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapterId,
    )

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

    private companion object {
        val chapterId = CanonicalChapterId("chapter")
        val releaseId = ChapterReleaseId("release")
        val sourceId = PluginId("source")
    }
}

private class ReplanSource(
    override val pluginId: PluginId = PluginId("source"),
    private val result: suspend () -> ReaderSourceResult,
) : ReaderDocumentSource {
    var fetchCount: Int = 0
    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetchCount += 1
        return result()
    }
}

private class ReplanStore(
    private val exact: ReaderDocument? = null,
    private val typedResult: ReaderDocumentReadResult? = null,
) : ReaderDocumentStore {
    var readResultCount: Int = 0

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = exact
    override suspend fun readResult(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): ReaderDocumentReadResult {
        readResultCount += 1
        return typedResult
            ?: exact?.let(ReaderDocumentReadResult::Hit)
            ?: ReaderDocumentReadResult.Missing
    }
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = exact
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}

private object NoopReplanProgress : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(emptyList())
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(null)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = null
    override suspend fun save(progress: ReadingProgress) = Unit
}

private fun document(fingerprint: String) = ReaderDocument(
    title = null,
    blocks = listOf(ReaderBlock.Paragraph("block", "text")),
    fingerprint = fingerprint,
)
