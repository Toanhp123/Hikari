package app.openstory.story.feature

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycle
import app.openstory.common.navigation.RouteLifecycleChange
import app.openstory.common.navigation.RouteLifecycleSource
import app.openstory.story.feature.state.StoryIssueKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryPresentationOwnerTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialProgressiveStateUsesRouteArgsImmediately() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet()
        val args = StoryRouteArgs(
            ref = REF,
            originMediaContext = CatalogMediaType.MANGA,
            preview = StoryRoutePreview(
                title = "Preview Title",
                coverLocator = COVER_LOCATOR,
                coverAssetKey = COVER_KEY,
            ),
        )

        val owner = StoryPresentationOwner(args, facet, coroutineScope = CoroutineScope(dispatcher))
        val state = owner.state.value

        assertEquals("Preview Title", state.summary?.title)
        assertEquals(CatalogMediaType.MANGA, state.summary?.contentType)
        assertEquals(COVER_KEY, state.artwork.assetKey)
        assertEquals(COVER_LOCATOR, state.artwork.locator)
        assertTrue(state.detailLoading)
        assertNull(state.detail)
    }

    @Test
    fun catalogFacetStateEmissionEnrichesSummaryAndDetail() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet()
        val args = StoryRouteArgs(
            ref = REF,
            originMediaContext = CatalogMediaType.MANGA,
            preview = StoryRoutePreview(coverAssetKey = COVER_KEY, coverLocator = COVER_LOCATOR),
        )

        var uiPublished = false
        val owner = StoryPresentationOwner(
            args = args,
            catalogFacet = facet,
            onUiPublished = { uiPublished = true },
            coroutineScope = CoroutineScope(dispatcher),
        )
        owner.activate()
        advanceUntilIdle()

        facet.emit(
            StoryDetailSessionState(
                projection = projection(),
                acquisition = CatalogAcquisitionStatus.Success,
            ),
        )
        advanceUntilIdle()

        val state = owner.state.value
        assertEquals("Story 17", state.summary?.title)
        assertNotNull(state.detail)
        assertEquals("Full synopsis", state.detail?.description)
        assertFalse(state.detailLoading)
        assertTrue(uiPublished)
    }

    @Test
    fun routeLifecycleStoreQuiescesReactivatesAndRemovesReleasedOwner() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet()
        val args = StoryRouteArgs(
            ref = REF,
            originMediaContext = CatalogMediaType.MANGA,
        )
        val lifecycleFlow = MutableSharedFlow<RouteLifecycleChange>()
        val lifecycleSource = object : RouteLifecycleSource {
            override val changes = lifecycleFlow
        }
        val entryId = RouteEntryId.from("story-entry-1")
        val store = StoryPresentationStore(CoroutineScope(dispatcher), lifecycleSource)
        val owner = store.ownerFor(entryId, args, facet)
        advanceUntilIdle()

        lifecycleFlow.emit(RouteLifecycleChange(entryId, RouteLifecycle.RETAINED))
        advanceUntilIdle()
        assertEquals(1, facet.quiesceCalls)

        lifecycleFlow.emit(RouteLifecycleChange(entryId, RouteLifecycle.ACTIVE))
        advanceUntilIdle()
        assertEquals(2, facet.activationCalls)

        lifecycleFlow.emit(RouteLifecycleChange(entryId, RouteLifecycle.RELEASED))
        advanceUntilIdle()
        assertEquals(2, facet.releaseCalls)
    }


    @Test
    fun releaseCleanupRemainsChildOfOwnerScopeUntilTerminalCleanupCompletes() = runTest(dispatcher.scheduler) {
        val releaseEntered = CompletableDeferred<Unit>()
        val finishRelease = CompletableDeferred<Unit>()
        val ownerJob = Job()
        val facet = FakeStoryCatalogFacet(
            releaseBlock = {
                releaseEntered.complete(Unit)
                finishRelease.await()
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(ownerJob + dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        owner.release()
        runCurrent()
        releaseEntered.await()

        ownerJob.cancel()
        runCurrent()
        assertFalse(ownerJob.isCompleted)

        finishRelease.complete(Unit)
        advanceUntilIdle()
        assertTrue(ownerJob.isCompleted)
        assertEquals(1, facet.releaseCalls)
    }

    @Test
    fun retainedThenReleasedQuiescesAndReleasesTheSameDemandExactlyOnce() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet()
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        owner.quiesce()
        advanceUntilIdle()
        owner.release()
        advanceUntilIdle()

        assertEquals(1, facet.quiesceCalls)
        assertEquals(1, facet.releaseCalls)
    }

    @Test
    fun reactivationWaitsForQuiescenceAndDoesNotAccumulateRetainedDemands() = runTest(dispatcher.scheduler) {
        val quiesceEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finishQuiesce = kotlinx.coroutines.CompletableDeferred<Unit>()
        val facet = FakeStoryCatalogFacet(
            quiesceBlock = {
                quiesceEntered.complete(Unit)
                finishQuiesce.await()
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        owner.quiesce()
        runCurrent()
        quiesceEntered.await()
        owner.activate()
        runCurrent()

        assertEquals(1, facet.activationCalls)
        finishQuiesce.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, facet.activationCalls)
        assertEquals(1, facet.releaseCalls)
    }


    @Test
    fun pendingActivationDoesNotPublishRuntimeContentBeforeActivationCompletes() = runTest(dispatcher.scheduler) {
        val activationEntered = CompletableDeferred<Unit>()
        val finishActivation = CompletableDeferred<Unit>()
        val facet = FakeStoryCatalogFacet(
            activationBlock = {
                activationEntered.complete(Unit)
                finishActivation.await()
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        runCurrent()
        activationEntered.await()
        assertNull(owner.state.value.summary)
        assertNull(owner.state.value.detail)

        finishActivation.complete(Unit)
        advanceUntilIdle()
        facet.emit(
            StoryDetailSessionState(
                projection = projection(),
                acquisition = CatalogAcquisitionStatus.Success,
            ),
        )
        advanceUntilIdle()

        assertEquals("Story 17", owner.state.value.summary?.title)
        assertEquals("Full synopsis", owner.state.value.detail?.description)
    }

    @Test
    fun retainingWhileActivationIsPendingCancelsActivationAndKeepsRouteInactive() = runTest(dispatcher.scheduler) {
        val activationEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finishActivation = kotlinx.coroutines.CompletableDeferred<Unit>()
        val facet = FakeStoryCatalogFacet(
            activationBlock = {
                activationEntered.complete(Unit)
                finishActivation.await()
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        runCurrent()
        activationEntered.await()
        owner.quiesce()
        owner.retry()
        finishActivation.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, facet.activationCalls)
        assertEquals(0, facet.quiesceCalls)
        assertEquals(0, facet.releaseCalls)
    }

    @Test
    fun detailFailurePreservesPublishedDetailAndRetryUsesActiveDemand() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet()
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        facet.emit(
            StoryDetailSessionState(
                projection = projection(),
                acquisition = CatalogAcquisitionStatus.Success,
            ),
        )
        advanceUntilIdle()
        facet.emit(
            StoryDetailSessionState(
                projection = null,
                acquisition = CatalogAcquisitionStatus.Failed(
                    CatalogFailure.Acquisition(CatalogOperation.STORY_DETAIL),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("Full synopsis", owner.state.value.detail?.description)
        assertEquals(StoryIssueKind.ACQUISITION_FAILED, owner.state.value.issue?.kind)
        assertTrue(owner.state.value.issue?.retryable == true)

        owner.retry()
        advanceUntilIdle()

        assertEquals(1, facet.retryCalls)
        assertNull(owner.state.value.issue)
    }

    @Test
    fun observationFailureReleasesDemandAndSurfacesInternalIssue() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet(
            stateFailure = CatalogFailureException(CatalogFailure.InternalInvariant("observation_failed")),
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()

        assertEquals(1, facet.activationCalls)
        assertEquals(1, facet.releaseCalls)
        assertEquals(StoryIssueKind.INTERNAL_FAILURE, owner.state.value.issue?.kind)
    }

    @Test
    fun releaseFailureDoesNotPreventRetainedRouteFromReactivating() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet(
            releaseFailure = CatalogFailureException(CatalogFailure.InternalInvariant("release_failed")),
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        owner.quiesce()
        advanceUntilIdle()
        owner.activate()
        advanceUntilIdle()

        assertEquals(2, facet.activationCalls)
        assertEquals(1, facet.releaseCalls)
    }


    @Test
    fun staleActivationFailureCannotOverwriteAReactivatedRoute() = runTest(dispatcher.scheduler) {
        val firstActivationEntered = CompletableDeferred<Unit>()
        val releaseStaleActivation = CompletableDeferred<Unit>()
        var attempt = 0
        val facet = FakeStoryCatalogFacet(
            activationBlock = {
                attempt++
                if (attempt == 1) {
                    firstActivationEntered.complete(Unit)
                    try {
                        releaseStaleActivation.await()
                    } catch (_: java.util.concurrent.CancellationException) {
                        withContext(NonCancellable) { releaseStaleActivation.await() }
                        throw CatalogFailureException(CatalogFailure.InternalInvariant("stale_activation_failure"))
                    }
                }
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        runCurrent()
        firstActivationEntered.await()
        owner.quiesce()
        owner.activate()
        runCurrent()
        assertEquals(2, facet.activationCalls)

        releaseStaleActivation.complete(Unit)
        advanceUntilIdle()

        assertNull(owner.state.value.issue)
        assertTrue(owner.state.value.detailLoading)
    }

    @Test
    fun retryExceptionSurfacesIssueWithoutEscapingOwnerScope() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet(
            retryBlock = { throw CatalogFailureException(CatalogFailure.InternalInvariant("retry_failed")) },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        owner.retry()
        advanceUntilIdle()

        assertEquals(1, facet.retryCalls)
        assertEquals(StoryIssueKind.INTERNAL_FAILURE, owner.state.value.issue?.kind)
        assertFalse(owner.state.value.detailLoading)
    }

    @Test
    fun retainingRouteCancelsInFlightRetryAndIgnoresLateFailure() = runTest(dispatcher.scheduler) {
        val retryEntered = CompletableDeferred<Unit>()
        val finishRetry = CompletableDeferred<Unit>()
        val facet = FakeStoryCatalogFacet(
            retryBlock = {
                retryEntered.complete(Unit)
                finishRetry.await()
                CatalogAcquisitionResult.Failed(
                    CatalogFailure.Acquisition(CatalogOperation.STORY_DETAIL),
                )
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA),
            catalogFacet = facet,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        advanceUntilIdle()
        owner.retry()
        runCurrent()
        retryEntered.await()

        owner.quiesce()
        finishRetry.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, facet.retryCalls)
        assertNull(owner.state.value.issue)
    }

    @Test
    fun lookingUpExistingOwnerDoesNotReactivateRetainedRouteFromComposition() = runTest(dispatcher.scheduler) {
        val facet = FakeStoryCatalogFacet()
        val args = StoryRouteArgs(ref = REF, originMediaContext = CatalogMediaType.MANGA)
        val lifecycleFlow = MutableSharedFlow<RouteLifecycleChange>()
        val lifecycleSource = object : RouteLifecycleSource {
            override val changes = lifecycleFlow
        }
        val entryId = RouteEntryId.from("story-entry-retained")
        val store = StoryPresentationStore(CoroutineScope(dispatcher), lifecycleSource)
        val owner = store.ownerFor(entryId, args, facet)
        advanceUntilIdle()
        lifecycleFlow.emit(RouteLifecycleChange(entryId, RouteLifecycle.RETAINED))
        advanceUntilIdle()

        val sameOwner = store.ownerFor(entryId, args, facet)
        advanceUntilIdle()

        assertTrue(owner === sameOwner)
        assertEquals(1, facet.activationCalls)
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("test.source")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
        val COVER_LOCATOR = CoverLocator.TrustedLocalResource("cover-1", "1.0")
        val COVER_KEY = CoverAssetKey(REF.storyId, CoverRevisionV1.local("cover-1", "1.0"))

        fun projection() = StoryDetailProjection(
            ref = REF,
            summary = StorySummaryProjection(
                ref = REF,
                title = "Story 17",
                contentType = CatalogMediaType.MANGA,
                sourceVersion = "1.0",
                coverLocator = COVER_LOCATOR,
                coverAssetKey = COVER_KEY,
                rating = null,
                publicationStatusSummary = "Ongoing",
                latestUpdateEpochMs = 1000L,
            ),
            detail = StoryRichDetailProjection(
                description = "Full synopsis",
                authors = listOf("Author 1"),
                artists = emptyList(),
                genres = listOf("Action"),
                publicationStatus = "Ongoing",
                language = "en",
            ),
            detailProvenance = AcquisitionProvenance(
                catalogSourceKey = SOURCE_KEY,
                sourceVersion = "1.0",
                acquiredAtEpochMs = 1000L,
            ),
        )
    }

    private class FakeStoryCatalogFacet(
        private val activationBlock: suspend () -> Unit = {},
        private val quiesceBlock: suspend () -> Unit = {},
        private val retryBlock: suspend () -> CatalogAcquisitionResult = { CatalogAcquisitionResult.Success },
        private val releaseBlock: suspend () -> Unit = {},
        private val stateFailure: Throwable? = null,
        private val releaseFailure: Throwable? = null,
    ) : StoryCatalogFacet {
        private val statesFlow = MutableSharedFlow<StoryDetailSessionState>()
        var activationCalls = 0
        var retryCalls = 0
        var quiesceCalls = 0
        var releaseCalls = 0

        override suspend fun activate(ref: StorySourceRef): StoryCatalogFacetActivation {
            activationCalls++
            activationBlock()
            return StoryCatalogFacetActivation.Available(
                states = states(),
                retry = {
                    retryCalls++
                    retryBlock()
                },
                quiesce = {
                    quiesceCalls++
                    quiesceBlock()
                },
                release = {
                    releaseCalls++
                    releaseBlock()
                    releaseFailure?.let { throw it }
                },
            )
        }

        suspend fun emit(state: StoryDetailSessionState) {
            statesFlow.emit(state)
        }

        private fun states(): Flow<StoryDetailSessionState> = stateFailure?.let { failure ->
            flow { throw failure }
        } ?: statesFlow
    }
}
