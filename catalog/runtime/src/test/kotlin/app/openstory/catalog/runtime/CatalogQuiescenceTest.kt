package app.openstory.catalog.runtime

import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogQuiescenceTest {
    @Test
    fun quiesceCancelsScreenOwnedRefreshAndObservationWithoutStartingContinuation() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val source = RecordingSource(discover = { mediaType ->
            entered.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                testDiscoverAcquisition(mediaType)
            } catch (error: CancellationException) {
                cancelled.complete(Unit)
                throw error
            }
        })
        val activation = available(storage, source)
        val session = activation.discoverSession(CatalogMediaType.MANGA)
        val observation = session.states.launchIn(backgroundScope)
        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(
            DiscoverPersistenceState.Published(
                generation = 1L,
                provenance = app.openstory.catalog.domain.source.AcquisitionProvenance(TEST_SOURCE_KEY, "v1", 1L),
                cards = RuntimeTestData.publishedCards(CatalogMediaType.MANGA),
            ),
        )
        runCurrent()
        val refresh = async { session.refresh() }
        runCurrent()
        entered.await()

        observation.cancelAndJoin()
        session.quiesce()
        runCurrent()

        cancelled.await()
        assertTrue(refresh.isCancelled)
        assertEquals(0, storage.activeDiscoverObservers)
        assertEquals(1, source.discoverCalls.size)
        advanceUntilIdle()
        assertEquals(0, activation.activeWorkCount())
    }

    @Test
    fun quiesceDoesNotRestartATerminalFailedBootstrapOnResume() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource(discover = { throw IllegalStateException("source failed") })
        val activation = available(storage, source)
        val session = activation.discoverSession(CatalogMediaType.MANGA)
        val firstObservation = session.states.launchIn(backgroundScope)
        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(DiscoverPersistenceState.Absent)
        advanceUntilIdle()
        assertEquals(1, source.discoverCalls.size)

        firstObservation.cancelAndJoin()
        session.quiesce()
        val resumedObservation = session.states.launchIn(backgroundScope)
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, source.discoverCalls.size)
        resumedObservation.cancelAndJoin()
    }

    @Test
    fun repeatedDiscoverStoryDiscoverDemandLeavesNoWorkOrPins() = runTest {
        val storage = RuntimeFakeStorage()
        val activation = available(storage, RecordingSource())
        val discover = activation.discoverSession(CatalogMediaType.MANGA)
        val firstDiscover = discover.states.launchIn(backgroundScope)
        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(
            DiscoverPersistenceState.Published(
                generation = 1L,
                provenance = app.openstory.catalog.domain.source.AcquisitionProvenance(TEST_SOURCE_KEY, "v1", 1L),
                cards = RuntimeTestData.publishedCards(CatalogMediaType.MANGA),
            ),
        )
        runCurrent()
        firstDiscover.cancelAndJoin()

        val ref = testRef()
        val story = activation.storyDetailSession(ref)
        val storyObservation = story.activate().launchIn(backgroundScope)
        storage.storyFlow(ref).emit(null)
        runCurrent()
        storyObservation.cancelAndJoin()
        story.release()

        val secondDiscover = discover.states.launchIn(backgroundScope)
        runCurrent()
        secondDiscover.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(0, activation.activeWorkCount())
        assertEquals(0, activation.activeStoryPinCount())
        assertEquals(0, storage.activeDiscoverObservers)
        assertEquals(0, storage.activeStoryObservers)
    }

    @Test
    fun quiescingStoryDemandCancelsItsActiveAcquisitionAndClearsThePin() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val source = RecordingSource(story = { ref ->
            entered.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                testStoryAcquisition(ref.sourceStoryId)
            } catch (error: CancellationException) {
                cancelled.complete(Unit)
                throw error
            }
        })
        val activation = available(storage, source)
        val ref = testRef()
        val story = activation.storyDetailSession(ref)
        val observation = story.activate().launchIn(backgroundScope)
        storage.storyFlow(ref).emit(null)
        runCurrent()
        entered.await()

        observation.cancelAndJoin()
        story.quiesce()
        runCurrent()

        cancelled.await()
        assertEquals(0, activation.activeWorkCount())
        assertEquals(0, activation.activeStoryPinCount())
    }

    @Test
    fun finalCloseCancelsChildrenAndClosesOwnedStorageExactlyOnce() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val source = RecordingSource(discover = { mediaType ->
            entered.complete(Unit)
            CompletableDeferred<Unit>().await()
            testDiscoverAcquisition(mediaType)
        })
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = CatalogRuntimeFactory(
            binding = TEST_BINDING.copy(acquisitionSource = source),
            openStorage = { storage },
            wallClockEpochMs = { 10L },
            dispatchers = CatalogExecutionDispatchers(dispatcher, dispatcher),
        ).createSession()
        val activation = session.activate() as CatalogCapabilityActivation.Available
        val refresh = async { activation.discoverSession(CatalogMediaType.MANGA).refresh() }
        runCurrent()
        entered.await()

        session.close()
        session.close()
        runCurrent()

        assertTrue(refresh.isCancelled)
        assertEquals(1, storage.closeCount)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.available(
        storage: RuntimeFakeStorage,
        source: RecordingSource,
    ): CatalogCapabilityActivation.Available {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CatalogRuntimeFactory(
            binding = TEST_BINDING.copy(acquisitionSource = source),
            openStorage = { storage },
            wallClockEpochMs = { 9L },
            dispatchers = CatalogExecutionDispatchers(dispatcher, dispatcher),
        ).createSession().activate() as CatalogCapabilityActivation.Available
    }
}
