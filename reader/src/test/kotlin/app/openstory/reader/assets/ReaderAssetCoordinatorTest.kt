package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAssetCoordinatorTest {
    @Test
    fun `register installs snapshot before asynchronous bounded inspection completes`() = runTest {
        val store = RecordingAssetStore().apply { inspectGate = CompletableDeferred() }
        val coordinator = coordinator(store, ReaderNetworkState.OFFLINE)
        val manifest = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 300)

        val revision = coordinator.registerCommitted(ReaderSessionId(1), 1, manifest)
        val snapshot = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.observeCommittedManifest(ReaderSessionId(1)).first()
        }.await()

        assertEquals(1L, revision)
        assertEquals(manifest, snapshot.manifest)
        assertEquals(1L, snapshot.manifestRevision)
        assertTrue(store.inspectionBatches.isEmpty())

        runCurrent()
        assertEquals(1, store.inspectionBatches.size)
        assertTrue(store.inspectionBatches.single().size < manifest.descriptors.size)
        store.inspectGate?.complete(Unit)
    }

    @Test
    fun `same chapter refresh advances manifest only while chapter change slides bounded history`() = runTest {
        val coordinator = coordinator(RecordingAssetStore(), ReaderNetworkState.OFFLINE)
        val first = assetManifest(sessionId = 1, chapter = "chapter-10", pageCount = 2)
        val refreshed = assetManifest(sessionId = 1, chapter = "chapter-10", pageCount = 3)
        val jumped = assetManifest(sessionId = 1, chapter = "chapter-50", pageCount = 2)

        assertEquals(1L, coordinator.registerCommitted(ReaderSessionId(1), 1, first))
        assertEquals(2L, coordinator.registerCommitted(ReaderSessionId(1), 2, refreshed))
        val sameChapter = coordinator.sessionSnapshot(ReaderSessionId(1))
        assertEquals(1L, sameChapter?.chapterWindowRevision)
        assertTrue(sameChapter?.recentCommittedChapterIds.orEmpty().isEmpty())

        assertEquals(3L, coordinator.registerCommitted(ReaderSessionId(1), 3, jumped))
        val changed = coordinator.sessionSnapshot(ReaderSessionId(1))
        assertEquals(2L, changed?.chapterWindowRevision)
        assertEquals(listOf(CanonicalChapterId("chapter-10")), changed?.recentCommittedChapterIds)
    }

    @Test
    fun `recent manifest history deduplicates chapters by canonical identity`() {
        val current = assetManifest(sessionId = 1, chapter = "chapter-a", pageCount = 2, locatorTag = "current")
        val olderSameChapter = assetManifest(
            sessionId = 1,
            chapter = "chapter-a",
            pageCount = 2,
            locatorTag = "older",
        )
        val olderOtherChapter = assetManifest(sessionId = 1, chapter = "chapter-c", pageCount = 2)
        val next = assetManifest(sessionId = 1, chapter = "chapter-b", pageCount = 2)
        val state = ReaderAssetSessionState(
            manifestRevision = 3,
            chapterWindowRevision = 3,
            committedChapterId = current.canonicalChapterId,
            committedManifest = current,
            recentCommittedManifests = listOf(olderSameChapter, olderOtherChapter),
        )

        val changed = state.acceptCommitted(
            effectiveManifestRevision = 4,
            chapterId = next.canonicalChapterId,
            manifest = next,
        )

        assertEquals(
            listOf(current.canonicalChapterId, olderOtherChapter.canonicalChapterId),
            changed.recentCommittedManifests.map { it.canonicalChapterId },
        )
    }

    @Test
    fun `two sessions publish union protections and releasing one preserves the other`() = runTest {
        val store = RecordingAssetStore()
        val coordinator = coordinator(store, ReaderNetworkState.OFFLINE)
        val first = assetManifest(sessionId = 1, chapter = "chapter-a", pageCount = 3)
        val second = assetManifest(sessionId = 2, chapter = "chapter-b", pageCount = 3)
        coordinator.registerCommitted(ReaderSessionId(1), 1, first)
        coordinator.registerCommitted(ReaderSessionId(2), 1, second)
        runCurrent()
        store.reconciliations.clear()

        coordinator.updateViewport(viewport(first, 0, 0))
        coordinator.updateViewport(viewport(second, 1, 1))
        runCurrent()

        assertTrue(first.descriptors[0].key.hash in store.reconciliations.last().byKey)
        assertTrue(second.descriptors[1].key.hash in store.reconciliations.last().byKey)

        coordinator.releaseSession(ReaderSessionId(1))
        runCurrent()

        assertTrue(first.descriptors[0].key.hash !in store.reconciliations.last().byKey)
        assertTrue(second.descriptors[1].key.hash in store.reconciliations.last().byKey)
        assertEquals(listOf(ReaderSessionId(1)), store.releasedSessions)
    }

    @Test
    fun `rapid distinct viewport frames coalesce storage publication`() = runTest {
        val store = RecordingAssetStore()
        val coordinator = coordinator(store, ReaderNetworkState.OFFLINE)
        val manifest = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 20)
        coordinator.registerCommitted(ReaderSessionId(1), 1, manifest)
        runCurrent()
        store.reconciliations.clear()

        coordinator.updateViewport(viewport(manifest, 1, 2))
        coordinator.updateViewport(viewport(manifest, 3, 4))
        coordinator.updateViewport(viewport(manifest, 5, 6))

        assertTrue(store.reconciliations.isEmpty())
        runCurrent()
        assertEquals(1, store.reconciliations.size)
        assertTrue(manifest.descriptors[5].key.hash in store.reconciliations.single().byKey)
        assertTrue(manifest.descriptors[1].key.hash !in store.reconciliations.single().byKey)
    }

    @Test
    fun `presentation promotes only a still visible current asset and marks it consumed once`() = runTest {
        val store = RecordingAssetStore()
        val coordinator = coordinator(store, ReaderNetworkState.OFFLINE)
        val manifest = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 4)
        coordinator.registerCommitted(ReaderSessionId(1), 1, manifest)
        coordinator.updateViewport(viewport(manifest, 1, 2))

        assertFalse(coordinator.assetPresented(request(manifest, revision = 2, ordinal = 1)))
        assertFalse(coordinator.assetPresented(request(manifest, revision = 1, ordinal = 0)))
        assertTrue(coordinator.assetPresented(request(manifest, revision = 1, ordinal = 1)))
        assertTrue(coordinator.assetPresented(request(manifest, revision = 1, ordinal = 1)))
        coordinator.updateViewport(viewport(manifest, 2, 2))
        runCurrent()

        assertEquals(listOf(manifest.descriptors[1].key), store.consumedKeys)
        assertEquals(
            ReaderAssetProtectionClass.ACTIVE_CONSUMED,
            coordinator.sessionSnapshot(ReaderSessionId(1))
                ?.activeProtections
                ?.byKey
                ?.get(manifest.descriptors[1].key.hash),
        )
    }

    @Test
    fun `delivery replacement is guarded and publishes a new revision without sliding history`() = runTest {
        val coordinator = coordinator(RecordingAssetStore(), ReaderNetworkState.OFFLINE)
        val original = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 2, locatorTag = "old")
        val refreshed = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 2, locatorTag = "new")
        val mismatch = assetManifest(sessionId = 1, chapter = "other", pageCount = 2)
        coordinator.registerCommitted(ReaderSessionId(1), 1, original)

        assertEquals(
            ReaderDeliveryManifestReplacement.Superseded,
            coordinator.replaceDeliveryManifest(ReaderSessionId(1), 9, refreshed),
        )
        assertEquals(
            ReaderDeliveryManifestReplacement.SemanticRouteMismatch,
            coordinator.replaceDeliveryManifest(ReaderSessionId(1), 1, mismatch),
        )
        val applied = assertIs<ReaderDeliveryManifestReplacement.Applied>(
            coordinator.replaceDeliveryManifest(ReaderSessionId(1), 1, refreshed),
        )

        assertEquals(2L, applied.snapshot.manifestRevision)
        assertEquals(refreshed, applied.snapshot.manifest)
        val state = coordinator.sessionSnapshot(ReaderSessionId(1))
        assertEquals(1L, state?.chapterWindowRevision)
        assertTrue(state?.recentCommittedChapterIds.orEmpty().isEmpty())
        assertTrue(state?.localPresence?.values?.all { it == ReaderAssetLocalPresence.UNKNOWN } == true)
    }

    @Test
    fun `stale viewport and stale prefetched artifacts cannot mutate current planning state`() = runTest {
        val store = RecordingAssetStore()
        val coordinator = coordinator(store, ReaderNetworkState.UNMETERED)
        val current = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 10)
        coordinator.registerCommitted(ReaderSessionId(1), 1, current)

        assertFalse(coordinator.updateViewport(viewport(current, 0, 1).copy(manifestRevision = 2)))
        coordinator.acceptPrefetchedArtifact(prefetchedArtifact(current, targetChapter = "chapter-2", token = 2))
        coordinator.acceptPrefetchedArtifact(prefetchedArtifact(current, targetChapter = "chapter-3", token = 1))
        coordinator.updateViewport(viewport(current, 7, 9, progress = 9_500))
        runCurrent()

        val state = coordinator.sessionSnapshot(ReaderSessionId(1))
        assertEquals(CanonicalChapterId("chapter-2"), state?.prefetchedManifest?.canonicalChapterId)
        assertEquals(listOf(0, 1, 2, 3), state?.plan?.transition?.map { it.imageOrdinal })
    }

    @Test
    fun `transition acquisition keeps prefetched manifest commit facts`() = runTest {
        val store = RecordingAssetStore()
        val coordinator = coordinator(store, ReaderNetworkState.UNMETERED, withLoader = true)
        val current = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 10)
        coordinator.registerCommitted(ReaderSessionId(1), 1, current)
        coordinator.acceptPrefetchedArtifact(prefetchedArtifact(current, targetChapter = "chapter-2", token = 1))
        val prefetched = requireNotNull(coordinator.sessionSnapshot(ReaderSessionId(1))?.prefetchedManifest)

        coordinator.updateViewport(viewport(current, 7, 9, progress = 9_500))
        runCurrent()

        val transitionFacts = store.capturedFacts.single { it.key == prefetched.descriptors.first().key }
        assertEquals(prefetched.canonicalChapterId, transitionFacts.canonicalChapterId)
        assertEquals(prefetched.selectedReleaseId, transitionFacts.releaseId)
    }

    @Test
    fun `offline request uses retained local bytes and never attempts missing remote work`() = runTest {
        val store = RecordingAssetStore()
        val manifest = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 2)
        store.presence[manifest.descriptors[0].key] = ReaderAssetLocalPresence.LOCAL_AVAILABLE
        store.presence[manifest.descriptors[1].key] = ReaderAssetLocalPresence.LOCAL_MISSING
        store.localBytes[manifest.descriptors[0].key] = byteArrayOf(1, 2, 3)
        val coordinator = coordinator(store, ReaderNetworkState.OFFLINE, withLoader = true)
        coordinator.registerCommitted(ReaderSessionId(1), 1, manifest)
        runCurrent()

        val retained = coordinator.requestPage(request(manifest, revision = 1, ordinal = 0))
        val missing = coordinator.requestPage(request(manifest, revision = 1, ordinal = 1))

        assertIs<ReaderAssetLoadOutcome.Local>(retained).lease.close()
        assertIs<ReaderAssetLoadOutcome.Failure>(missing)
        assertEquals(0, store.remoteFetches)
    }

    @Test
    fun `release is idempotent for unknown or already removed sessions`() = runTest {
        val store = RecordingAssetStore()
        val coordinator = coordinator(store, ReaderNetworkState.OFFLINE)
        val manifest = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 1)
        coordinator.registerCommitted(ReaderSessionId(1), 1, manifest)

        coordinator.releaseSession(ReaderSessionId(1))
        coordinator.releaseSession(ReaderSessionId(1))
        coordinator.releaseSession(ReaderSessionId(2))
        runCurrent()

        assertEquals(listOf(ReaderSessionId(1)), store.releasedSessions)
        assertNull(coordinator.sessionSnapshot(ReaderSessionId(1)))
    }

    @Test
    fun `release cancels outstanding visible consumer interest for only that session`() = runTest {
        val store = RecordingAssetStore().apply {
            deliveryGate = CompletableDeferred()
        }
        val coordinator = coordinator(store, ReaderNetworkState.UNMETERED, withLoader = true)
        val first = assetManifest(sessionId = 1, chapter = "chapter-a", pageCount = 1)
        val second = assetManifest(sessionId = 2, chapter = "chapter-b", pageCount = 1)
        coordinator.registerCommitted(ReaderSessionId(1), 1, first)
        coordinator.registerCommitted(ReaderSessionId(2), 1, second)
        runCurrent()

        val request = async { coordinator.requestPage(request(first, revision = 1, ordinal = 0)) }
        runCurrent()
        coordinator.releaseSession(ReaderSessionId(1))
        runCurrent()

        val requestWasCancelled = request.isCancelled
        store.deliveryGate?.complete(
            ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.Cancelled),
        )
        runCurrent()

        assertTrue(requestWasCancelled)
        assertTrue(coordinator.sessionSnapshot(ReaderSessionId(2)) != null)
    }

    private fun TestScope.coordinator(
        store: RecordingAssetStore,
        network: ReaderNetworkState,
        withLoader: Boolean = false,
    ): ReaderAssetCoordinator {
        val loader = if (withLoader) {
            ReaderAssetLoader(
                store = store,
                delivery = ReaderAssetDeliveryPort {
                    store.remoteFetches += 1
                    store.deliveryGate?.await() ?: ReaderAssetDeliveryResult.Success(
                        ReaderAssetPayload.verifiedBounded(byteArrayOf(9), "image/jpeg", null),
                    )
                },
                singleFlight = ReaderAssetSingleFlight(this),
                fetchArbiter = ContentFetchArbiter(),
                persistenceScope = this,
            )
        } else {
            null
        }
        return ReaderAssetCoordinator(
            store = store,
            networkFacts = ReaderNetworkFactsPort { network },
            coordinatorScope = this,
            loader = loader,
        )
    }
}

private class RecordingAssetStore : ReaderAssetStorePort {
    val presence = mutableMapOf<ReaderPageAssetKey, ReaderAssetLocalPresence>()
    val localBytes = mutableMapOf<ReaderPageAssetKey, ByteArray>()
    val inspectionBatches = mutableListOf<Set<ReaderPageAssetKey>>()
    val reconciliations = mutableListOf<ReaderAssetActiveProtections>()
    val consumedKeys = mutableListOf<ReaderPageAssetKey>()
    val releasedSessions = mutableListOf<ReaderSessionId>()
    val capturedFacts = mutableListOf<ReaderAssetCommitFacts>()
    var inspectGate: CompletableDeferred<Unit>? = null
    var deliveryGate: CompletableDeferred<ReaderAssetDeliveryResult>? = null
    var remoteFetches = 0

    override suspend fun inspect(keys: Set<ReaderPageAssetKey>): Map<ReaderPageAssetKey, ReaderAssetLocalPresence> {
        inspectionBatches += keys
        inspectGate?.await()
        return keys.associateWith { presence[it] ?: ReaderAssetLocalPresence.LOCAL_MISSING }
    }

    override suspend fun openLocal(key: ReaderPageAssetKey): ReaderAssetOpenResult = localBytes[key]?.let { bytes ->
        ReaderAssetOpenResult.Available(ByteArrayReadLease(bytes))
    } ?: ReaderAssetOpenResult.Missing

    override suspend fun captureDurableWriteAuthority(
        facts: ReaderAssetCommitFacts,
    ): ReaderAssetDurableWriteAuthority? {
        capturedFacts += facts
        return null
    }

    override suspend fun commit(
        facts: ReaderAssetCommitFacts,
        authority: ReaderAssetDurableWriteAuthority,
        payload: ReaderAssetPayload,
    ): ReaderAssetCommitResult = ReaderAssetCommitResult.Bypassed

    override suspend fun markConsumed(key: ReaderPageAssetKey) {
        consumedKeys += key
    }

    override suspend fun invalidate(key: ReaderPageAssetKey, reason: ReaderAssetInvalidationReason) = Unit

    override suspend fun cachePressure(): ReaderAssetCachePressure = ReaderAssetCachePressure.NORMAL

    override suspend fun reconcile(activeProtections: ReaderAssetActiveProtections) {
        reconciliations += activeProtections
    }

    override suspend fun releaseSession(sessionId: ReaderSessionId) {
        releasedSessions += sessionId
    }

    override suspend fun clearAutomatic(scope: ReaderAssetClearScope) = Unit
}

private class ByteArrayReadLease(private val bytes: ByteArray) : ReaderAssetReadLease {
    override val sizeBytes: Long = bytes.size.toLong()
    override fun openStream(): InputStream = ByteArrayInputStream(bytes)
    override fun close() = Unit
}
