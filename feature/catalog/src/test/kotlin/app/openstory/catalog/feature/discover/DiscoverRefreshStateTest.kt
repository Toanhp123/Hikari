package app.openstory.catalog.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.discover.DiscoverSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRefreshStateTest {
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
    fun refreshAndRetryJoinOneRefreshAndRetainPublishedContentOnFailure() = runTest(dispatcher.scheduler) {
        val refreshResult = CompletableDeferred<CatalogAcquisitionResult>()
        val runtime = RecordingDiscoverRuntime { refreshResult.await() }
        val owner = TestOwner(runtime)
        advanceUntilIdle()
        runtime.emit(published(content = true), CatalogAcquisitionStatus.Idle)
        advanceUntilIdle()

        owner.viewModel.refresh()
        owner.viewModel.retry()
        runCurrent()

        assertEquals(1, runtime.refreshCalls)
        val refreshing = owner.viewModel.state.value.content as DiscoverContentState.Content
        assertTrue(refreshing.refreshing)
        refreshResult.complete(
            CatalogAcquisitionResult.Failed(CatalogFailure.Acquisition(CatalogOperation.DISCOVER)),
        )
        advanceUntilIdle()

        val failed = owner.viewModel.state.value.content as DiscoverContentState.Content
        assertEquals("Story One", failed.sections.single().cards.single().title)
        assertFalse(failed.refreshing)
        assertEquals(CatalogIssueKind.ACQUISITION_FAILED, failed.issue?.kind)
        owner.clear()
    }

    @Test
    fun publishedEmptyRefreshFailureRemainsEmptyRatherThanReturningToAbsentLoading() = runTest(dispatcher.scheduler) {
        val runtime = RecordingDiscoverRuntime {
            CatalogAcquisitionResult.Failed(CatalogFailure.Acquisition(CatalogOperation.DISCOVER))
        }
        val owner = TestOwner(runtime)
        advanceUntilIdle()
        runtime.emit(published(content = false), CatalogAcquisitionStatus.Idle)
        advanceUntilIdle()

        owner.viewModel.refresh()
        advanceUntilIdle()

        val failed = owner.viewModel.state.value.content as DiscoverContentState.Empty
        assertFalse(failed.refreshing)
        assertEquals(CatalogIssueKind.ACQUISITION_FAILED, failed.issue?.kind)
        owner.clear()
    }

    @Test
    fun resumeWaitsForQuiescenceBeforeReplacingTheCollector() = runTest(dispatcher.scheduler) {
        val quiesceEntered = CompletableDeferred<Unit>()
        val finishQuiesce = CompletableDeferred<Unit>()
        val runtime = RecordingDiscoverRuntime(
            refreshResult = { CatalogAcquisitionResult.Success },
            quiesceBlock = {
                quiesceEntered.complete(Unit)
                finishQuiesce.await()
            },
        )
        val owner = TestOwner(runtime)
        advanceUntilIdle()
        assertEquals(1, runtime.activeCollectors)

        owner.viewModel.quiesce()
        runCurrent()
        quiesceEntered.await()
        owner.viewModel.resume()
        runCurrent()

        assertEquals(0, runtime.activeCollectors)
        finishQuiesce.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, runtime.activeCollectors)
        assertEquals(2, runtime.observeCalls)
        owner.clear()
    }

    @Test
    fun resumeRestartsActivationCancelledByQuiescence() = runTest(dispatcher.scheduler) {
        val firstActivationEntered = CompletableDeferred<Unit>()
        var activationCalls = 0
        val states = MutableSharedFlow<DiscoverSessionState>(replay = 1)
        val runtime = object : DiscoverRuntime {
            override suspend fun activate(): DiscoverRuntimeActivation {
                activationCalls += 1
                if (activationCalls == 1) {
                    firstActivationEntered.complete(Unit)
                    awaitCancellation()
                }
                return DiscoverRuntimeActivation.Available(
                    observe = { states },
                    refresh = { CatalogAcquisitionResult.Success },
                )
            }

            override fun close() = Unit
        }
        val owner = TestOwner(runtime)
        runCurrent()
        firstActivationEntered.await()

        owner.viewModel.quiesce()
        runCurrent()
        owner.viewModel.resume()
        advanceUntilIdle()

        assertEquals(2, activationCalls)
        owner.clear()
    }

    private class TestOwner(
        runtime: DiscoverRuntime,
        override val viewModelStore: ViewModelStore = ViewModelStore(),
    ) : ViewModelStoreOwner {
        val viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DiscoverViewModel(runtime) as T
            },
        )[DiscoverViewModel::class.java]

        fun clear() = viewModelStore.clear()
    }

    private class RecordingDiscoverRuntime(
        private val quiesceBlock: suspend () -> Unit = {},
        private val refreshResult: suspend () -> CatalogAcquisitionResult,
    ) : DiscoverRuntime {
        private val states = MutableSharedFlow<DiscoverSessionState>(replay = 1)
        var refreshCalls = 0
        var observeCalls = 0
        var activeCollectors = 0

        override suspend fun activate(): DiscoverRuntimeActivation = DiscoverRuntimeActivation.Available(
            observe = {
                states
                    .onStart {
                        observeCalls += 1
                        activeCollectors += 1
                    }
                    .onCompletion { activeCollectors -= 1 }
            },
            refresh = {
                refreshCalls += 1
                refreshResult()
            },
            quiesce = { quiesceBlock() },
        )

        suspend fun emit(
            persistence: DiscoverPersistenceState,
            acquisition: CatalogAcquisitionStatus,
        ) {
            states.emit(DiscoverSessionState(persistence, acquisition))
        }

        override fun close() = Unit
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("refresh-state-test")

        fun published(content: Boolean) = DiscoverPersistenceState.Published(
            generation = 3L,
            provenance = AcquisitionProvenance(SOURCE_KEY, "persisted", 3L),
            cards = if (content) listOf(card()) else emptyList(),
        )

        fun card(): DiscoverCard {
            val sourceStoryId = "story-one"
            val ref = StorySourceRef(
                storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
                catalogSourceKey = SOURCE_KEY,
                sourceStoryId = sourceStoryId,
            )
            return DiscoverCard(
                ref = ref,
                sectionKind = CatalogSectionKind.POPULAR,
                itemPosition = 0,
                title = "Story One",
                contentType = CatalogMediaType.MANGA,
                sourceVersion = "persisted",
                coverLocator = null,
                coverAssetKey = null,
                rating = null,
                publicationStatusSummary = null,
                latestUpdateEpochMs = null,
            )
        }
    }
}
