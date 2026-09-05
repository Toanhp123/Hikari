package app.openstory.reader.assets

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAssetCoordinatorLifecycleDiagnosticsTest {
    @Test
    fun `transition planning records speculative prefetch count`() = runTest {
        val store = RecordingAssetStore()
        val diagnostics = RecordingReaderAssetDiagnostics()
        val coordinator = coordinator(
            store = store,
            network = ReaderNetworkState.UNMETERED,
            diagnostics = diagnostics,
        )
        val current = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 10)
        coordinator.registerCommitted(ReaderSessionId(1), 1, current)
        coordinator.acceptPrefetchedArtifact(prefetchedArtifact(current, targetChapter = "chapter-2", token = 1))

        coordinator.updateViewport(viewport(current, 7, 9, progress = 9_500))
        runCurrent()

        val prefetch = diagnostics.events.filterIsInstance<ReaderAssetDiagnosticEvent.Prefetch>()
        assertEquals(listOf(4), prefetch.map { it.count })
    }

    @Test
    fun `one joined locator refresh records one aggregate refresh event`() = runTest {
        val store = RecordingAssetStore()
        val diagnostics = RecordingReaderAssetDiagnostics()
        val original = assetManifest(sessionId = 1, chapter = "chapter", pageCount = 2)
        val release = releaseFor(original)
        val refreshGate = CompletableDeferred<Unit>()
        val coordinator = refreshingCoordinator(store, diagnostics = diagnostics) {
            ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.DeliveryRejected(403))
        }
        coordinator.registerSelectedReleaseRefreshPort(
            original.sessionId,
            object : ReaderSelectedReleaseRefreshPort {
                override suspend fun refreshSelectedRelease(
                    expectedManifestRevision: Long,
                    expectedReleaseId: ChapterReleaseId,
                ): ReaderSelectedReleaseRefreshResult {
                    refreshGate.await()
                    return ReaderSelectedReleaseRefreshResult.Refreshed(
                        selectedRelease = release,
                        document = documentFor(original, locatorTag = "same"),
                        imageSourcePolicy = LOCATOR_BOUND_PUBLIC_POLICY,
                    )
                }
            },
        )
        coordinator.registerCommitted(original.sessionId, 1L, original)
        runCurrent()

        val first = async { coordinator.requestPage(request(original, 1L, 0)) }
        val second = async { coordinator.requestPage(request(original, 1L, 1)) }
        runCurrent()
        refreshGate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, diagnostics.events.count { it == ReaderAssetDiagnosticEvent.LocatorRefresh })
    }

    @Test
    fun `security invalidation during locator refresh returns route invalidated instead of cancellation`() = runTest {
        val store = RecordingAssetStore()
        val manifest = assetManifest(
            sessionId = 77,
            chapter = "private-refresh",
            pageCount = 1,
            persistenceContract = ReaderImagePersistenceContract.NON_PERSISTENT,
        )
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        val coordinator = refreshingCoordinator(store) {
            ReaderAssetDeliveryResult.Failure(ReaderAssetFailure.DeliveryRejected(403))
        }
        coordinator.registerSelectedReleaseRefreshPort(
            manifest.sessionId,
            object : ReaderSelectedReleaseRefreshPort {
                override suspend fun refreshSelectedRelease(
                    expectedManifestRevision: Long,
                    expectedReleaseId: ChapterReleaseId,
                ): ReaderSelectedReleaseRefreshResult {
                    refreshStarted.complete(Unit)
                    refreshGate.await()
                    return ReaderSelectedReleaseRefreshResult.Superseded
                }
            },
        )
        val revision = coordinator.registerCommitted(manifest.sessionId, 1L, manifest)
        runCurrent()

        val inFlight = async { coordinator.requestPage(request(manifest, revision, 0)) }
        refreshStarted.await()

        coordinator.invalidateSecurityScopedSource(manifest.sourceNamespace)
        runCurrent()

        assertEquals(
            ReaderAssetFailure.RouteInvalidated,
            assertIs<ReaderAssetLoadOutcome.Failure>(inFlight.await()).failure,
        )
    }

    @Test
    fun `security invalidation hard cancels private flight and invalidated revision returns route invalidated`() = runTest {
        val store = RecordingAssetStore().apply { deliveryGate = CompletableDeferred() }
        val manifest = assetManifest(
            sessionId = 1,
            chapter = "private-chapter",
            pageCount = 1,
            persistenceContract = ReaderImagePersistenceContract.NON_PERSISTENT,
        )
        val coordinator = coordinator(store, ReaderNetworkState.UNMETERED, withLoader = true)
        val revision = coordinator.registerCommitted(manifest.sessionId, 1L, manifest)
        runCurrent()

        val inFlight = async { coordinator.requestPage(request(manifest, revision, 0)) }
        runCurrent()
        assertEquals(1, store.remoteFetches)

        coordinator.invalidateSecurityScopedSource(manifest.sourceNamespace)
        runCurrent()

        assertEquals(
            ReaderAssetFailure.RouteInvalidated,
            assertIs<ReaderAssetLoadOutcome.Failure>(inFlight.await()).failure,
        )
        assertNull(coordinator.sessionSnapshot(manifest.sessionId)?.committedManifest)
        assertEquals(
            ReaderAssetFailure.RouteInvalidated,
            assertIs<ReaderAssetLoadOutcome.Failure>(
                coordinator.requestPage(request(manifest, revision, 0)),
            ).failure,
        )
    }

    @Test
    fun `security invalidation leaves public manifest for same source usable`() = runTest {
        val store = RecordingAssetStore()
        val manifest = assetManifest(sessionId = 1, chapter = "public-chapter", pageCount = 1)
        val coordinator = coordinator(store, ReaderNetworkState.UNMETERED, withLoader = true)
        val revision = coordinator.registerCommitted(manifest.sessionId, 1L, manifest)
        runCurrent()

        coordinator.invalidateSecurityScopedSource(manifest.sourceNamespace)
        runCurrent()

        assertEquals(manifest, coordinator.sessionSnapshot(manifest.sessionId)?.committedManifest)
        assertIs<ReaderAssetLoadOutcome.Remote>(coordinator.requestPage(request(manifest, revision, 0)))
    }

    private fun TestScope.refreshingCoordinator(
        store: RecordingAssetStore,
        diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
        delivery: suspend (ReaderAssetDeliveryRequest) -> ReaderAssetDeliveryResult,
    ): ReaderAssetCoordinator = ReaderAssetCoordinator(
        store = store,
        networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.UNMETERED },
        coordinatorScope = this,
        loader = ReaderAssetLoader(
            store = store,
            delivery = ReaderAssetDeliveryPort { request ->
                store.remoteFetches += 1
                delivery(request)
            },
            singleFlight = ReaderAssetSingleFlight(this, diagnostics),
            fetchArbiter = ContentFetchArbiter(),
            persistenceScope = this,
            diagnostics = diagnostics,
        ),
        diagnostics = diagnostics,
    )

    private fun TestScope.coordinator(
        store: RecordingAssetStore,
        network: ReaderNetworkState,
        withLoader: Boolean = false,
        diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
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
                singleFlight = ReaderAssetSingleFlight(this, diagnostics),
                fetchArbiter = ContentFetchArbiter(),
                persistenceScope = this,
                diagnostics = diagnostics,
            )
        } else {
            null
        }
        return ReaderAssetCoordinator(
            store = store,
            networkFacts = ReaderNetworkFactsPort { network },
            coordinatorScope = this,
            loader = loader,
            diagnostics = diagnostics,
        )
    }

    private fun releaseFor(manifest: ReaderAssetChapterManifest) = ChapterRelease(
        id = manifest.selectedReleaseId,
        storyId = manifest.storyId,
        pluginId = PluginId(manifest.sourceNamespace.value),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-${manifest.canonicalChapterId.value}",
        displayLabel = manifest.canonicalChapterId.value,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = manifest.canonicalChapterId,
    )

    private fun documentFor(
        manifest: ReaderAssetChapterManifest,
        locatorTag: String,
    ) = ReaderDocument(
        title = manifest.canonicalChapterId.value,
        blocks = manifest.descriptors.map { descriptor ->
            ReaderBlock.ImagePage(
                id = descriptor.uiBlockId,
                stableAssetId = descriptor.stableAssetId,
                imageUrl = buildString {
                    append("https://cdn.example/")
                    append(locatorTag)
                    append('/')
                    append(manifest.canonicalChapterId.value)
                    append('/')
                    append(descriptor.imageOrdinal)
                    append(".jpg")
                },
            )
        },
        fingerprint = "semantic-${manifest.selectedReleaseId.value}",
    )

    private companion object {
        val LOCATOR_BOUND_PUBLIC_POLICY = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.DELIVERY_STABLE_ONLY,
            locatorContract = ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        )
    }
}
