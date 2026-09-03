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
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.assets.ReaderAssetChapterManifest
import app.openstory.reader.assets.ReaderAssetSessionPort
import app.openstory.reader.assets.ReaderPrefetchedDocumentArtifact
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchCoordinatorTest {
    @Test
    fun committedChapterSchedulesOnlyImmediateNextChapterPrefetch() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
            enqueueSuccess("release-b", textDocument("b"))
            enqueueSuccess("release-c", textDocument("c"))
        }
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        session.updateChapterGraph(
            groups(
                chapter("chapter-1", "release-a"),
                chapter("chapter-2", "release-b"),
                chapter("chapter-3", "release-c"),
            ),
        )

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()

        assertEquals(listOf("release-a", "release-b"), source.fetches)
        assertTrue("release-c" !in source.fetches)
    }

    @Test
    fun successfulNextChapterPrefetchPublishesCompleteArtifactWithoutSemanticCommit() = runTest {
        val policy = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        )
        val prefetchedDocument = ReaderDocument(
            title = "images",
            blocks = listOf(
                ReaderBlock.ImagePage("image", "stable/page", "https://cdn.example/page.jpg"),
            ),
            fingerprint = "image-fingerprint",
        )
        val source = ControlledPrefetchSource().apply {
            imageSourcePolicy = policy
            enqueueSuccess("release-a", textDocument("a"))
            enqueueSuccess("release-b", prefetchedDocument)
        }
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        session.updateChapterGraph(groups(chapter("chapter-1", "release-a"), chapter("chapter-2", "release-b")))

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()

        val artifact = fixture.assetPort.artifacts.single()
        assertEquals(session.sessionId, artifact.sessionId)
        assertEquals(2L, artifact.prefetchToken)
        assertEquals(CanonicalChapterId("chapter-2"), artifact.targetChapterId)
        assertEquals(ChapterReleaseId("release-b"), artifact.selectedRelease.id)
        assertEquals(prefetchedDocument, artifact.document)
        assertEquals(policy, artifact.imageSourcePolicy)
        assertEquals(source.pluginId, artifact.sourcePluginId)
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(CanonicalChapterId("chapter-1"), state.committed.chapterId)
    }

    @Test
    fun stalePrefetchArtifactIsRejectedAfterGraphAndTokenChange() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val stale = source.enqueuePending("release-b")
        source.enqueueSuccess("release-c", textDocument("c"))
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        val a = chapter("chapter-1", "release-a")
        val b = chapter("chapter-2", "release-b")
        val c = chapter("chapter-3", "release-c")
        session.updateChapterGraph(groups(a, b, c))
        session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1")))
        runCurrent()

        session.updateChapterGraph(groups(a, c, b))
        runCurrent()
        stale.complete(ReaderSourceResult.Success(textDocument("stale")))
        runCurrent()

        assertEquals(
            listOf(CanonicalChapterId("chapter-3")),
            fixture.assetPort.artifacts.map { it.targetChapterId },
        )
    }

    @Test
    fun ownerJobCompletionClosesAssetSessionIdempotently() = runTest {
        val source = ControlledPrefetchSource()
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val ownerJob = Job()
        val session = fixture.factory.create(StoryId("story"), CoroutineScope(coroutineContext + ownerJob))

        ownerJob.complete()
        runCurrent()
        session.close()

        assertEquals(listOf(session.sessionId), fixture.assetPort.released)
    }

    @Test
    fun meteredAndUnknownDisableRemotePrefetchButForegroundRemoteStillRuns() = runTest {
        for (network in listOf(ReaderNetworkState.METERED, ReaderNetworkState.UNKNOWN)) {
            val source = ControlledPrefetchSource().apply {
                enqueueSuccess("release-a", textDocument("a"))
                enqueueSuccess("release-b", textDocument("b"))
            }
            val fixture = fixture(source = source, network = network)
            val session = fixture.factory.create(StoryId("story"), this)
            session.updateRoutingPreferences(ReaderPreferences())
            session.updateChapterGraph(groups(chapter("chapter-1", "release-a"), chapter("chapter-2", "release-b")))

            assertIs<ReaderForegroundResult.Committed>(
                session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
            )
            runCurrent()

            assertEquals(listOf("release-a"), source.fetches, "network=$network")
        }
    }

    @Test
    fun localNextChapterPrefetchRemainsAllowedOnMeteredNetwork() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val store = PrefetchRecordingStore().apply {
            exact[ChapterReleaseId("release-b") to "fp-b"] = textDocument("b", "fp-b")
        }
        val fixture = fixture(
            source = source,
            store = store,
            network = ReaderNetworkState.METERED,
            cacheFacts = ReaderCacheFactsPort { releaseIds, _ ->
                releaseIds.associateWith { id ->
                    if (id.value == "release-b") ReaderLocalCacheFact.Exact("fp-b") else ReaderLocalCacheFact.Miss
                }
            },
        )
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        session.updateChapterGraph(groups(chapter("chapter-1", "release-a"), chapter("chapter-2", "release-b")))

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()

        assertEquals(listOf("release-a"), source.fetches)
        assertEquals(listOf(ChapterReleaseId("release-b") to "fp-b"), store.exactReads)
    }

    @Test
    fun remotePrefetchRevalidatesUnmeteredNetworkBeforeStartingFallbackEffect() = runTest {
        var network = ReaderNetworkState.UNMETERED
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val store = PrefetchRecordingStore(
            onExactRead = { releaseId ->
                if (releaseId == ChapterReleaseId("release-b")) network = ReaderNetworkState.METERED
            },
        ).apply {
            exact[ChapterReleaseId("release-b") to "fp-b"] = textDocument("stale", "fp-stale")
        }
        val fixture = fixture(
            source = source,
            store = store,
            network = ReaderNetworkState.UNMETERED,
            networkFacts = ReaderNetworkFactsPort { network },
            cacheFacts = ReaderCacheFactsPort { releaseIds, _ ->
                releaseIds.associateWith { id ->
                    if (id == ChapterReleaseId("release-b")) {
                        ReaderLocalCacheFact.Exact("fp-b")
                    } else {
                        ReaderLocalCacheFact.Miss
                    }
                }
            },
        )
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        session.updateChapterGraph(
            groups(chapter("chapter-1", "release-a"), chapter("chapter-2", "release-b")),
        )

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()

        assertEquals(listOf("release-a"), source.fetches)
        assertEquals(listOf(ChapterReleaseId("release-b") to "fp-b"), store.exactReads)
    }

    @Test
    fun foregroundNavigationCancelsObsoletePrefetchAndBuildsFreshForegroundPlan() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val obsoletePrefetch = source.enqueuePending("release-b")
        source.enqueueSuccess("release-b", textDocument("b"))
        source.enqueueSuccess("release-c", textDocument("c"))
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        session.updateChapterGraph(
            groups(
                chapter("chapter-1", "release-a"),
                chapter("chapter-2", "release-b"),
                chapter("chapter-3", "release-c"),
            ),
        )
        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()
        assertEquals(listOf("release-a", "release-b"), source.fetches)

        val foreground = async {
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-2")))
        }
        runCurrent()

        assertIs<ReaderForegroundResult.Committed>(foreground.await())
        runCurrent()
        assertEquals(listOf("release-a", "release-b", "release-b", "release-c"), source.fetches)
        obsoletePrefetch.complete(ReaderSourceResult.Success(textDocument("stale")))
        runCurrent()
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(CanonicalChapterId("chapter-2"), state.committed.chapterId)
    }

    @Test
    fun graphChangeThatMovesActualNextChapterReplacesObsoletePrefetchWithoutChangingCommit() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val obsolete = source.enqueuePending("release-b")
        source.enqueueSuccess("release-c", textDocument("c"))
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        val a = chapter("chapter-1", "release-a")
        val b = chapter("chapter-2", "release-b")
        val c = chapter("chapter-3", "release-c")
        session.updateChapterGraph(groups(a, b, c))
        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()
        assertEquals(listOf("release-a", "release-b"), source.fetches)

        session.updateChapterGraph(groups(a, c, b))
        runCurrent()

        assertEquals(listOf("release-a", "release-b", "release-c"), source.fetches)
        obsolete.complete(ReaderSourceResult.Success(textDocument("stale")))
        runCurrent()
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(CanonicalChapterId("chapter-1"), state.committed.chapterId)
    }

    @Test
    fun graphChangeWithinSameNextChapterReplacesPrefetchWhenReleaseSetChanges() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val obsolete = source.enqueuePending("release-b1")
        source.enqueueSuccess("release-b2", textDocument("b2"))
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        val a = chapter("chapter-1", "release-a")
        val oldNext = chapter("chapter-2", "release-b1")
        val revisedNext = chapter("chapter-2", "release-b2")
        session.updateChapterGraph(groups(a, oldNext))
        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()
        assertEquals(listOf("release-a", "release-b1"), source.fetches)

        session.updateChapterGraph(groups(a, revisedNext))
        runCurrent()

        assertEquals(listOf("release-a", "release-b1", "release-b2"), source.fetches)
        obsolete.complete(ReaderSourceResult.Success(textDocument("stale")))
        runCurrent()
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(CanonicalChapterId("chapter-1"), state.committed.chapterId)
    }

    @Test
    fun graphRemovalOfCommittedChapterCancelsPrefetchInsteadOfWrappingToFirstChapter() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
        }
        val obsolete = source.enqueuePending("release-b")
        val fixture = fixture(source = source, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        val a = chapter("chapter-1", "release-a")
        val b = chapter("chapter-2", "release-b")
        session.updateChapterGraph(groups(a, b))
        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()
        assertEquals(listOf("release-a", "release-b"), source.fetches)

        session.updateChapterGraph(groups(b))
        runCurrent()

        assertEquals(listOf("release-a", "release-b"), source.fetches)
        obsolete.complete(ReaderSourceResult.Success(textDocument("stale")))
        runCurrent()
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(CanonicalChapterId("chapter-1"), state.committed.chapterId)
    }

    @Test
    fun validPersistablePrefetchUsesExistingStoreRulesButImagePrefetchDoesNotCreateCacheBytes() = runTest {
        val source = ControlledPrefetchSource().apply {
            enqueueSuccess("release-a", textDocument("a"))
            enqueueSuccess("release-b", textDocument("b"))
            enqueueSuccess(
                "release-c",
                ReaderDocument(
                    title = "images",
                    blocks = listOf(
                        ReaderBlock.ImagePage("p", "hash/page.jpg", "https://example.test/page.jpg"),
                    ),
                    fingerprint = "fp-image",
                ),
            )
        }
        val store = PrefetchRecordingStore()
        val fixture = fixture(source = source, store = store, network = ReaderNetworkState.UNMETERED)
        val session = fixture.factory.create(StoryId("story"), this)
        session.updateRoutingPreferences(ReaderPreferences())
        val a = chapter("chapter-1", "release-a")
        val b = chapter("chapter-2", "release-b")
        val c = chapter("chapter-3", "release-c")
        session.updateChapterGraph(groups(a, b, c))

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-1"))),
        )
        runCurrent()
        assertTrue(store.writes.any { it.first == ChapterReleaseId("release-b") })

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CanonicalChapterId("chapter-2"))),
        )
        runCurrent()
        assertTrue(store.writes.none { it.first == ChapterReleaseId("release-c") })
    }

    private fun fixture(
        source: ControlledPrefetchSource,
        store: PrefetchRecordingStore = PrefetchRecordingStore(),
        network: ReaderNetworkState,
        cacheFacts: ReaderCacheFactsPort? = null,
        networkFacts: ReaderNetworkFactsPort? = null,
        assetPort: RecordingPrefetchAssetPort = RecordingPrefetchAssetPort(),
    ): Fixture {
        val progress = EmptyProgressRepository()
        val limiter = ReaderExecutionTestOwners()
        val coordinator = ReaderRouteCoordinator(
            store = store,
            sources = object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> = listOf(source)
            },
            progress = progress,
            healthRegistry = ReaderSourceHealthRegistry(),
            sourceLane = limiter.sourceLane,
            fetchArbiter = limiter.fetchArbiter,
            halfOpenProbeRegistry = limiter.halfOpenProbeRegistry,
            cacheFacts = cacheFacts ?: ReaderCacheFactsPort { releaseIds, _ ->
                releaseIds.associateWith { id ->
                    store.currentFingerprint(id)?.let(ReaderLocalCacheFact::Exact)
                        ?: ReaderLocalCacheFact.Miss
                }
            },
            networkFacts = networkFacts ?: ReaderNetworkFactsPort { network },
        )
        val prefetch = PrefetchCoordinator(coordinator)
        return Fixture(
            ReaderRouteSessionFactory(coordinator, prefetch, assetPort),
            assetPort,
        )
    }

    private data class Fixture(
        val factory: ReaderRouteSessionFactory,
        val assetPort: RecordingPrefetchAssetPort,
    )
}

private class ControlledPrefetchSource : ReaderDocumentSource {
    override val pluginId = PluginId("plugin")
    override var imageSourcePolicy = ReaderImageSourcePolicy.FAIL_CLOSED
    private val responses = mutableMapOf<String, ArrayDeque<CompletableDeferred<ReaderSourceResult>>>()
    val fetches = mutableListOf<String>()

    fun enqueueSuccess(releaseId: String, document: ReaderDocument) {
        enqueue(releaseId, CompletableDeferred(ReaderSourceResult.Success(document)))
    }

    fun enqueuePending(releaseId: String): CompletableDeferred<ReaderSourceResult> =
        CompletableDeferred<ReaderSourceResult>().also { enqueue(releaseId, it) }

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetches += release.id.value
        return checkNotNull(responses[release.id.value]?.pollFirst()) {
            "No queued prefetch response for ${release.id.value}"
        }.await()
    }

    private fun enqueue(releaseId: String, response: CompletableDeferred<ReaderSourceResult>) {
        responses.getOrPut(releaseId, ::ArrayDeque).addLast(response)
    }
}

private class RecordingPrefetchAssetPort : ReaderAssetSessionPort {
    val artifacts = mutableListOf<ReaderPrefetchedDocumentArtifact>()
    val released = mutableListOf<ReaderSessionId>()

    override fun registerCommitted(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ): Long = proposedManifestRevision

    override fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact) {
        artifacts += artifact
    }

    override fun releaseSession(sessionId: ReaderSessionId) {
        released += sessionId
    }
}

private class PrefetchRecordingStore(
    private val onExactRead: (ChapterReleaseId) -> Unit = {},
) : ReaderDocumentStore {
    val exact = mutableMapOf<Pair<ChapterReleaseId, String>, ReaderDocument>()
    val exactReads = mutableListOf<Pair<ChapterReleaseId, String>>()
    val writes = mutableListOf<Triple<ChapterReleaseId, String, ReaderDocument>>()

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? {
        exactReads += releaseId to fingerprint
        onExactRead(releaseId)
        return exact[releaseId to fingerprint]
    }

    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null

    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) {
        writes += Triple(releaseId, fingerprint, document)
        exact[releaseId to fingerprint] = document
    }

    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit

    fun currentFingerprint(releaseId: ChapterReleaseId): String? =
        exact.keys.lastOrNull { (id, _) -> id == releaseId }?.second
}

private class EmptyProgressRepository : ReadingProgressRepository {
    private val all = MutableStateFlow<List<ReadingProgress>>(emptyList())
    override fun observeAll(): Flow<List<ReadingProgress>> = all
    override fun observe(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
    ): Flow<ReadingProgress?> = MutableStateFlow(null)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = null
    override suspend fun save(progress: ReadingProgress) = Unit
}

private fun groups(vararg chapters: Pair<CanonicalChapter, ChapterRelease>): List<CanonicalChapterGroup> =
    chapters.map { (chapter, release) -> CanonicalChapterGroup(chapter, listOf(release)) }

private fun chapter(chapterId: String, releaseId: String): Pair<CanonicalChapter, ChapterRelease> {
    val id = CanonicalChapterId(chapterId)
    val label = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null)
    val release = ChapterRelease(
        ChapterReleaseId(releaseId), StoryId("story"), PluginId("plugin"), "source-story", releaseId,
        chapterId, label, "en", 1, id,
    )
    return CanonicalChapter(id, StoryId("story"), label, chapterId, false, setOf(release.id)) to release
}

private fun textDocument(title: String, fingerprint: String = "fp-$title") = ReaderDocument(
    title = title,
    blocks = listOf(ReaderBlock.Paragraph("block", "Text")),
    fingerprint = fingerprint,
)
