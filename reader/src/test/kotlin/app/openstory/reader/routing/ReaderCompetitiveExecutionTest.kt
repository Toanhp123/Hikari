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
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.HedgeDirective
import app.openstory.reader.engine.HedgeOmissionReason
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderCompetitiveExecutionTest {
    @Test
    fun `completion registry orders timestamp then primary role then stable attempt id`() {
        val registry = CompetitiveCompletionRegistry()
        val fallbackZ = completion("attempt-z", AttemptRole.FALLBACK, 700L)
        val hedge = completion("attempt-h", AttemptRole.HEDGE, 700L)
        val primary = completion("attempt-p", AttemptRole.PRIMARY, 700L)
        val fallbackA = completion("attempt-a", AttemptRole.FALLBACK, 700L)

        registry.record(fallbackZ)
        registry.record(hedge)
        registry.record(primary)
        registry.record(fallbackA)

        assertEquals(primary, registry.winner())

        val fallbackOnly = CompetitiveCompletionRegistry()
        fallbackOnly.record(fallbackZ)
        fallbackOnly.record(fallbackA)
        assertEquals(fallbackA, fallbackOnly.winner())
    }

    @Test
    fun `primary success before hedge delay prevents alternate launch`() = runTest {
        val primary = CompetitiveSource(PRIMARY_SOURCE, delayMillis = 500L, document("primary"))
        val alternate = CompetitiveSource(ALTERNATE_SOURCE, delayMillis = 1L, document("hedge"))
        val fixture = fixture(primary, alternate)

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceTimeBy(500L)
        runCurrent()

        val committed = assertIs<ReaderForegroundResult.Committed>(result.await())
        assertEquals(PRIMARY_SOURCE, committed.release.pluginId)
        assertEquals(0, alternate.fetchCount)
    }

    @Test
    fun `unresolved remote primary launches one hedge at the configured delay`() = runTest {
        val primary = CompetitiveSource(PRIMARY_SOURCE, delayMillis = 800L, document("primary"))
        val alternate = CompetitiveSource(ALTERNATE_SOURCE, delayMillis = 50L, document("hedge"))
        val fixture = fixture(primary, alternate)

        val result = async {
            fixture.session.execute(
                ReaderForegroundIntent(CHAPTER_ID, explicitReleaseId = PRIMARY_RELEASE),
            )
        }
        runCurrent()
        assertEquals(1, primary.fetchCount)
        assertEquals(0, alternate.fetchCount)

        advanceTimeBy(650L)
        runCurrent()
        assertEquals(1, alternate.fetchCount)
        val competing = assertIs<ReaderExecutionState.Competing>(fixture.session.executionState)
        assertEquals("attempt-0", competing.primary.attemptId)
        assertEquals("attempt-1", competing.hedge.attemptId)

        advanceTimeBy(50L)
        runCurrent()
        val committed = assertIs<ReaderForegroundResult.Committed>(result.await())
        assertEquals(ALTERNATE_SOURCE, committed.release.pluginId)
        assertEquals("hedge", committed.document.fingerprint)
    }

    @Test
    fun `primary failure before delay starts the planned alternate immediately`() = runTest {
        val primary = CompetitiveSource(
            PRIMARY_SOURCE,
            delayMillis = 100L,
            ReaderSourceResult.Failure("plugin.execution_timeout", true),
        )
        val alternate = CompetitiveSource(ALTERNATE_SOURCE, delayMillis = 50L, document("hedge"))
        val fixture = fixture(primary, alternate)

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceTimeBy(150L)
        runCurrent()

        val committed = assertIs<ReaderForegroundResult.Committed>(result.await())
        assertEquals(ALTERNATE_SOURCE, committed.release.pluginId)
        assertEquals(100L, alternate.startedAtMillis.single())
        assertTrue(testScheduler.currentTime < 650L)
    }

    @Test
    fun `equal logical completion time prefers primary`() = runTest {
        val primary = CompetitiveSource(PRIMARY_SOURCE, delayMillis = 700L, document("primary"))
        val alternate = CompetitiveSource(ALTERNATE_SOURCE, delayMillis = 50L, document("hedge"))
        val fixture = fixture(primary, alternate)

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceTimeBy(700L)
        runCurrent()

        val committed = assertIs<ReaderForegroundResult.Committed>(result.await())
        assertEquals(PRIMARY_SOURCE, committed.release.pluginId)
    }

    @Test
    fun `completion registry defeats reversed notification delivery`() = runTest {
        val primary = CompetitiveSource(PRIMARY_SOURCE, delayMillis = 700L, document("primary"))
        val alternate = CompetitiveSource(ALTERNATE_SOURCE, delayMillis = 50L, document("hedge"))
        val store = CompetitiveStore(writeDelayByRelease = mapOf(PRIMARY_RELEASE to 100L))
        val fixture = fixture(primary, alternate, store = store)

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceTimeBy(800L)
        runCurrent()

        val committed = assertIs<ReaderForegroundResult.Committed>(result.await())
        assertEquals(PRIMARY_SOURCE, committed.release.pluginId)
        assertEquals(listOf(ChapterReleaseId("release-1"), PRIMARY_RELEASE), store.finishedWrites)
    }

    @Test
    fun `sequential recovery waits until both competitive attempts fail`() = runTest {
        val primary = CompetitiveSource(
            PRIMARY_SOURCE,
            delayMillis = 800L,
            ReaderSourceResult.Failure("plugin.execution_timeout", true),
        )
        val alternate = CompetitiveSource(
            ALTERNATE_SOURCE,
            delayMillis = 100L,
            ReaderSourceResult.Failure("plugin.execution_timeout", true),
        )
        val recovery = CompetitiveSource(PluginId("recovery-source"), 50L, document("recovery"))
        val fixture = fixture(primary, alternate, recovery)

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceTimeBy(799L)
        runCurrent()
        assertEquals(0, recovery.fetchCount)

        advanceTimeBy(51L)
        runCurrent()
        val committed = assertIs<ReaderForegroundResult.Committed>(result.await())
        assertEquals(PluginId("recovery-source"), committed.release.pluginId)
        assertEquals(800L, recovery.startedAtMillis.single())
    }

    @Test
    fun `hedge winner cancellation does not penalize primary health`() = runTest {
        val primary = CompetitiveSource(PRIMARY_SOURCE, delayMillis = 1_000L, document("primary"))
        val alternate = CompetitiveSource(ALTERNATE_SOURCE, delayMillis = 50L, document("hedge"))
        val fixture = fixture(primary, alternate)

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceTimeBy(700L)
        runCurrent()
        assertIs<ReaderForegroundResult.Committed>(result.await())

        val primaryHealth = fixture.health.snapshot(SourceOperationKey(PRIMARY_SOURCE), 2_000L)
        assertEquals(10_000, primaryHealth.state.successEwmaBasisPoints.value)
        assertEquals(3, primaryHealth.state.recentLatencySamplesMillis.size)
    }

    @Test
    fun `foreground competition stays at two concurrent and four total remote attempts`() = runTest {
        val probe = CompetitiveConcurrencyProbe()
        val sources = (0 until 5).map { index ->
            CompetitiveSource(
                pluginId = if (index == 0) PRIMARY_SOURCE else PluginId("source-$index"),
                delayMillis = if (index == 0) 800L else 100L,
                result = ReaderSourceResult.Failure("plugin.execution_timeout", true),
                concurrencyProbe = probe,
            )
        }
        val fixture = fixture(*sources.toTypedArray())

        val result = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        advanceUntilIdle()

        assertIs<ReaderForegroundResult.Exhausted>(result.await())
        assertTrue(probe.maximumActive <= 2)
        assertEquals(4, sources.sumOf(CompetitiveSource::fetchCount))
        assertEquals(0, sources.last().fetchCount)
    }

    @Test
    fun `competitive executor rejects a malformed route over the remote attempt ceiling`() = runTest {
        val started = mutableListOf<String>()
        val primary = remoteAttempt("attempt-0", AttemptRole.PRIMARY)
        val recovery = (1..4).map { index ->
            remoteAttempt("attempt-$index", AttemptRole.FALLBACK)
        }
        val execution = ReaderCompetitiveExecution(
            scheduler = FakeReaderExecutionScheduler(testScheduler),
            executeAttempt = { _, _, _ -> error("Malformed route must fail before execution.") },
            onAttemptStarted = { attempt, _ -> started += attempt.attemptId },
            onCompetitionLoser = {},
        )

        assertFailsWith<IllegalArgumentException> {
            execution.execute(
                primary = primary,
                hedgeDirective = HedgeDirective.Omitted(HedgeOmissionReason.NOT_ELIGIBLE),
                recoveryChain = recovery,
            )
        }
        assertTrue(started.isEmpty())
    }

    @Test
    fun `competitive executor rejects duplicate attempt ids before execution`() = runTest {
        val primary = remoteAttempt("duplicate", AttemptRole.PRIMARY)
        val fallback = remoteAttempt("duplicate", AttemptRole.FALLBACK)
        val execution = ReaderCompetitiveExecution(
            scheduler = FakeReaderExecutionScheduler(testScheduler),
            executeAttempt = { _, _, _ -> error("Malformed route must fail before execution.") },
            onAttemptStarted = { _, _ -> error("Malformed route must fail before execution.") },
            onCompetitionLoser = {},
        )

        assertFailsWith<IllegalArgumentException> {
            execution.execute(
                primary = primary,
                hedgeDirective = HedgeDirective.Omitted(HedgeOmissionReason.NOT_ELIGIBLE),
                recoveryChain = listOf(fallback),
            )
        }
    }

    @Test
    fun `navigation cancellation blocks late success from health and cache effects`() = runTest {
        val source = CompetitiveSource(
            pluginId = PRIMARY_SOURCE,
            delayMillis = 1_000L,
            result = ReaderSourceResult.Success(document("late")),
            ignoreCancellation = true,
        )
        val store = CompetitiveStore()
        val fixture = fixture(source, store = store)

        val execution = async {
            fixture.session.execute(ReaderForegroundIntent(CHAPTER_ID, PRIMARY_RELEASE))
        }
        runCurrent()
        execution.cancelAndJoin()
        advanceUntilIdle()

        val health = fixture.health.snapshot(SourceOperationKey(PRIMARY_SOURCE), 2_000L)
        assertEquals(3, health.state.recentLatencySamplesMillis.size)
        assertTrue(store.finishedWrites.isEmpty())
    }

    private suspend fun kotlinx.coroutines.test.TestScope.fixture(
        vararg sources: CompetitiveSource,
        store: CompetitiveStore = CompetitiveStore(),
    ): CompetitiveFixture {
        val health = ReaderSourceHealthRegistry()
        repeat(3) {
            health.record(
                SourceOperationKey(PRIMARY_SOURCE),
                SourceObservation.Success.Remote(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT, 1_300L),
                nowEpochMillis = it.toLong(),
            )
        }
        val sourceIds = sources.mapTo(linkedSetOf(), CompetitiveSource::pluginId)
        sources.forEach { source -> source.nowMillis = { testScheduler.currentTime } }
        val coordinator = ReaderRouteCoordinator(
            store = store,
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> = sources.toList()
            },
            progress = EmptyCompetitiveProgress,
            sourceAvailability = ReaderSourceAvailability { sourceIds },
            healthRegistry = health,
            executionLimiter = ReaderSourceExecutionLimiter(),
            cacheFacts = ReaderCacheFactsPort { ids, _ -> ids.associateWith { ReaderLocalCacheFact.Miss } },
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNMETERED },
            executionScheduler = FakeReaderExecutionScheduler(testScheduler),
        )
        val session = ReaderRouteSessionFactory(coordinator).create(STORY_ID)
        session.updateChapterGraph(listOf(group(sources.map(CompetitiveSource::pluginId))))
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))
        return CompetitiveFixture(session, health)
    }

    private fun group(sourceIds: List<PluginId>): CanonicalChapterGroup {
        val releases = sourceIds.mapIndexed { index, sourceId -> release(index, sourceId) }
        return CanonicalChapterGroup(
            chapter = CanonicalChapter(
                id = CHAPTER_ID,
                storyId = STORY_ID,
                parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
                displayLabel = "chapter",
                tombstoned = false,
                releaseIds = releases.mapTo(linkedSetOf(), ChapterRelease::id),
            ),
            releases = releases,
        )
    }

    private fun release(index: Int, sourceId: PluginId) = ChapterRelease(
        id = if (index == 0) PRIMARY_RELEASE else ChapterReleaseId("release-$index"),
        storyId = STORY_ID,
        pluginId = sourceId,
        sourceStoryId = "source-story",
        sourceReleaseId = "source-release-$index",
        displayLabel = "release-$index",
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = CHAPTER_ID,
    )

    private fun document(fingerprint: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", fingerprint)),
        fingerprint = fingerprint,
    )

    private fun completion(
        attemptId: String,
        role: AttemptRole,
        completedAtNanos: Long,
    ): ReaderValidCompletion {
        val release = release(0, PRIMARY_SOURCE)
        val attempt = RouteAttempt(
            attemptId = attemptId,
            releaseId = release.id,
            sourceId = release.pluginId,
            accessMode = AccessMode.REMOTE,
            localFingerprint = null,
            role = role,
        )
        return ReaderValidCompletion(
            attempt = attempt,
            loaded = ReaderLoadResult.Success(
                release = release,
                document = document(attemptId),
                fromStore = false,
            ),
            completedAtNanos = completedAtNanos,
        )
    }

    private fun remoteAttempt(
        attemptId: String,
        role: AttemptRole,
    ) = RouteAttempt(
        attemptId = attemptId,
        releaseId = ChapterReleaseId("release-$attemptId"),
        sourceId = PluginId("source-$attemptId"),
        accessMode = AccessMode.REMOTE,
        localFingerprint = null,
        role = role,
    )

    private data class CompetitiveFixture(
        val session: ReaderRouteSession,
        val health: ReaderSourceHealthRegistry,
    )

    private companion object {
        val STORY_ID = StoryId("story")
        val CHAPTER_ID = CanonicalChapterId("chapter")
        val PRIMARY_RELEASE = ChapterReleaseId("primary-release")
        val PRIMARY_SOURCE = PluginId("primary-source")
        val ALTERNATE_SOURCE = PluginId("alternate-source")
    }
}

private class CompetitiveSource(
    override val pluginId: PluginId,
    private val delayMillis: Long,
    private val result: ReaderSourceResult,
    private val concurrencyProbe: CompetitiveConcurrencyProbe? = null,
    private val ignoreCancellation: Boolean = false,
) : ReaderDocumentSource {
    constructor(pluginId: PluginId, delayMillis: Long, document: ReaderDocument) : this(
        pluginId,
        delayMillis,
        ReaderSourceResult.Success(document),
        null,
        false,
    )

    var fetchCount: Int = 0
        private set
    var nowMillis: () -> Long = { 0L }
    val startedAtMillis = mutableListOf<Long>()

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetchCount += 1
        startedAtMillis += nowMillis()
        concurrencyProbe?.started()
        return try {
            try {
                delay(delayMillis)
            } catch (cancelled: CancellationException) {
                if (!ignoreCancellation) throw cancelled
            }
            result
        } finally {
            concurrencyProbe?.finished()
        }
    }
}

private class CompetitiveConcurrencyProbe {
    private var active: Int = 0
    var maximumActive: Int = 0
        private set

    fun started() {
        active += 1
        maximumActive = maxOf(maximumActive, active)
    }

    fun finished() {
        active -= 1
    }
}

private class CompetitiveStore(
    private val writeDelayByRelease: Map<ChapterReleaseId, Long> = emptyMap(),
) : ReaderDocumentStore {
    val finishedWrites = mutableListOf<ChapterReleaseId>()

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = null
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null
    override suspend fun write(
        releaseId: ChapterReleaseId,
        fingerprint: String,
        document: ReaderDocument,
    ) {
        delay(writeDelayByRelease[releaseId] ?: 0L)
        finishedWrites += releaseId
    }
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}

private object EmptyCompetitiveProgress : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(emptyList())
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(null)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = null
    override suspend fun save(progress: ReadingProgress) = Unit
}
