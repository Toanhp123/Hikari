package app.openstory.catalog.feature.story

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun routeBecomesVisibleOnlyAfterPinFirstRuntimeActivationCompletes() = runTest(dispatcher.scheduler) {
        val events = mutableListOf<String>()
        val runtime = FakeStoryDetailRuntime(onActivate = { events += "pin-active" })
        val viewModel = StoryDetailViewModel(runtime)

        viewModel.open(REF, COVER_KEY) { events += "route-visible" }
        advanceUntilIdle()

        assertEquals(listOf("pin-active", "route-visible"), events)
        assertTrue(viewModel.state.value?.destinationActive == true)
        assertEquals(COVER_KEY, viewModel.state.value?.coverAssetKey)
        assertNull(viewModel.state.value?.coverLocator)
    }

    @Test
    fun summaryAndCoverRemainVisibleWhileMissingDetailLoads() = runTest(dispatcher.scheduler) {
        val runtime = FakeStoryDetailRuntime()
        val viewModel = StoryDetailViewModel(runtime)
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()

        runtime.emit(
            StoryDetailSessionState(
                projection = projection(detail = null),
                acquisition = CatalogAcquisitionStatus.Running,
            ),
        )
        advanceUntilIdle()

        val state = requireNotNull(viewModel.state.value)
        assertEquals("Story 17", state.summary?.title)
        assertEquals(COVER_LOCATOR, state.summary?.coverLocator)
        assertEquals(COVER_KEY, state.summary?.coverAssetKey)
        assertNull(state.detail)
        assertTrue(state.detailLoading)
        assertNull(state.issue)
        assertEquals(listOf(REF), runtime.activations)
    }

    @Test
    fun detailFailurePreservesCachedMetadataAndOffersInlineRetry() = runTest(dispatcher.scheduler) {
        val runtime = FakeStoryDetailRuntime()
        val viewModel = StoryDetailViewModel(runtime)
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()
        runtime.emit(
            StoryDetailSessionState(
                projection = projection(detail = DETAIL),
                acquisition = CatalogAcquisitionStatus.Success,
            ),
        )
        runtime.emit(
            StoryDetailSessionState(
                projection = projection(detail = DETAIL),
                acquisition = CatalogAcquisitionStatus.Failed(
                    CatalogFailure.Acquisition(CatalogOperation.STORY_DETAIL),
                ),
            ),
        )
        advanceUntilIdle()

        val failed = requireNotNull(viewModel.state.value)
        assertEquals("Cached description", failed.detail?.description)
        assertEquals(COVER_KEY, failed.summary?.coverAssetKey)
        assertEquals(CatalogIssueKind.ACQUISITION_FAILED, failed.issue?.kind)
        assertTrue(failed.issue?.retryable == true)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, runtime.retryCalls)
    }

    @Test
    fun metadataReadFailureDoesNotErasePreviouslyRenderedCoverOrDetail() = runTest(dispatcher.scheduler) {
        val runtime = FakeStoryDetailRuntime()
        val viewModel = StoryDetailViewModel(runtime)
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()
        runtime.emit(
            StoryDetailSessionState(projection(detail = DETAIL), CatalogAcquisitionStatus.Success),
        )
        runtime.emit(
            StoryDetailSessionState(
                projection = null,
                acquisition = CatalogAcquisitionStatus.Failed(
                    CatalogFailure.Storage(app.openstory.catalog.domain.failure.CatalogStorageOperation.READ_STORY),
                ),
            ),
        )
        advanceUntilIdle()

        val state = requireNotNull(viewModel.state.value)
        assertEquals(COVER_KEY, state.summary?.coverAssetKey)
        assertEquals("Cached description", state.detail?.description)
        assertEquals(CatalogIssueKind.STORAGE_FAILED, state.issue?.kind)
    }

    @Test
    fun cancellationDoesNotExposeInlineFailureOrRouteContent() = runTest(dispatcher.scheduler) {
        val runtime = FakeStoryDetailRuntime(activationFailure = CancellationException("cancelled"))
        val viewModel = StoryDetailViewModel(runtime)
        var routeVisible = false

        viewModel.open(REF, COVER_KEY) { routeVisible = true }
        advanceUntilIdle()

        assertFalse(routeVisible)
        assertNull(viewModel.state.value)
    }

    @Test
    fun releaseFailureDoesNotCrashBackOrKeepTheOldDemandActive() = runTest(dispatcher.scheduler) {
        val runtime = FakeStoryDetailRuntime(releaseFailure = IllegalStateException("release failed"))
        val viewModel = StoryDetailViewModel(runtime)
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()

        viewModel.closeDestination()
        advanceUntilIdle()
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()

        assertEquals(2, runtime.activations.size)
        assertTrue(viewModel.state.value?.destinationActive == true)
    }

    @Test
    fun immediateReopenAfterBackDoesNotReuseTheDemandBeingReleased() = runTest(dispatcher.scheduler) {
        val releaseStarted = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val runtime = FakeStoryDetailRuntime(
            releaseBlock = {
                releaseStarted.complete(Unit)
                releaseGate.await()
            },
        )
        val viewModel = StoryDetailViewModel(runtime)
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()

        viewModel.closeDestination()
        viewModel.open(REF, COVER_KEY) {}
        runCurrent()

        assertTrue(releaseStarted.isCompleted)
        assertEquals(1, runtime.activations.size)

        releaseGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, runtime.activations.size)
        assertEquals(1, runtime.releaseCalls)
        assertTrue(viewModel.state.value?.destinationActive == true)
    }

    @Test
    fun unexpectedObservationFailureReleasesThePinBeforeRejectingTheRoute() = runTest(dispatcher.scheduler) {
        val runtime = FakeStoryDetailRuntime(stateFailure = IllegalStateException("observer failed"))
        val viewModel = StoryDetailViewModel(runtime)
        var rejected = false

        viewModel.open(REF, COVER_KEY, onDestinationRejected = { rejected = true }) {}
        advanceUntilIdle()

        assertTrue(rejected)
        assertEquals(1, runtime.releaseCalls)
        assertNull(viewModel.state.value)
    }

    @Test
    fun reopenWaitsForStoryQuiescenceBeforeReplacingTheCollector() = runTest(dispatcher.scheduler) {
        val quiesceEntered = CompletableDeferred<Unit>()
        val finishQuiesce = CompletableDeferred<Unit>()
        val runtime = FakeStoryDetailRuntime(
            quiesceBlock = {
                quiesceEntered.complete(Unit)
                finishQuiesce.await()
            },
        )
        val viewModel = StoryDetailViewModel(runtime)
        viewModel.open(REF, COVER_KEY) {}
        advanceUntilIdle()
        assertEquals(1, runtime.activeCollectors)

        viewModel.quiesce()
        runCurrent()
        quiesceEntered.await()
        viewModel.open(REF, COVER_KEY) {}
        runCurrent()

        assertEquals(0, runtime.activeCollectors)
        finishQuiesce.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, runtime.activeCollectors)
        assertEquals(2, runtime.activations.size)
    }

    private class FakeStoryDetailRuntime(
        private val onActivate: () -> Unit = {},
        private val activationFailure: Throwable? = null,
        private val releaseFailure: Throwable? = null,
        private val stateFailure: Throwable? = null,
        private val releaseBlock: suspend () -> Unit = {},
        private val quiesceBlock: suspend () -> Unit = {},
    ) : StoryDetailRuntime {
        private val states = MutableSharedFlow<StoryDetailSessionState>(replay = 1)
        val activations = mutableListOf<StorySourceRef>()
        var retryCalls = 0
        var releaseCalls = 0
        var activeCollectors = 0

        override suspend fun activate(ref: StorySourceRef): StoryDetailRuntimeActivation {
            activationFailure?.let { throw it }
            activations += ref
            onActivate()
            return StoryDetailRuntimeActivation.Available(
                states = (stateFailure?.let { failure -> flow { throw failure } } ?: states)
                    .onStart { activeCollectors += 1 }
                    .onCompletion { activeCollectors -= 1 },
                retry = {
                    retryCalls += 1
                    CatalogAcquisitionResult.Success
                },
                quiesce = quiesceBlock,
                release = {
                    releaseCalls += 1
                    releaseBlock()
                    releaseFailure?.let { throw it }
                },
            )
        }

        suspend fun emit(state: StoryDetailSessionState) {
            states.emit(state)
        }
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("story-view-model-test")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
        val COVER_KEY = CoverAssetKey(
            storyId = REF.storyId,
            coverRevision = CoverRevisionV1.local("debug:manga:cover-a", "1"),
        )
        val COVER_LOCATOR = CoverLocator.TrustedLocalResource("debug:manga:cover-a", "1")
        val DETAIL = StoryRichDetailProjection(
            description = "Cached description",
            authors = listOf("Author"),
            artists = listOf("Artist"),
            genres = listOf("Drama"),
            publicationStatus = "Ongoing",
            language = "English",
        )

        fun projection(detail: StoryRichDetailProjection?) = StoryDetailProjection(
            ref = REF,
            summary = StorySummaryProjection(
                ref = REF,
                title = "Story 17",
                contentType = CatalogMediaType.MANGA,
                sourceVersion = "fixture-v1",
                coverLocator = COVER_LOCATOR,
                coverAssetKey = COVER_KEY,
                rating = CatalogRating(8.5, 10.0),
                publicationStatusSummary = "Ongoing",
                latestUpdateEpochMs = 17L,
            ),
            detail = detail,
            detailProvenance = detail?.let { AcquisitionProvenance(SOURCE_KEY, "fixture-v1", 18L) },
        )
    }
}
