package app.openstory.catalog.runtime.discover

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.RecordingSource
import app.openstory.catalog.runtime.RuntimeFakeStorage
import app.openstory.catalog.runtime.RuntimeTestData
import app.openstory.catalog.runtime.TEST_BINDING
import app.openstory.catalog.runtime.TEST_SOURCE_KEY
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.testDiscoverAcquisition
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRefreshOwnershipTest {
    @Test
    fun publishedContentRefreshFailureRetainsTheSnapshotAndScopesTheIssue() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource(discover = { throw IllegalStateException("source detail") })
        val activation = available(storage, source)
        val session = activation.discoverSession(CatalogMediaType.MANGA)
        val states = mutableListOf<DiscoverSessionState>()
        session.states.onEach(states::add).launchIn(backgroundScope)
        val published = published(CatalogMediaType.MANGA)
        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(published)
        runCurrent()

        val result = session.refresh()
        runCurrent()

        assertEquals(
            CatalogAcquisitionResult.Failed(CatalogFailure.Acquisition(CatalogOperation.DISCOVER)),
            result,
        )
        assertEquals(published, states.last().persistence)
        assertEquals(
            CatalogAcquisitionStatus.Failed(CatalogFailure.Acquisition(CatalogOperation.DISCOVER)),
            states.last().acquisition,
        )
    }

    @Test
    fun publishedEmptyRefreshWriteFailureRemainsEmptyAndNeverBecomesAbsent() = runTest {
        val storage = RuntimeFakeStorage().apply {
            discoverPublishFailure = IllegalStateException("sqlite detail")
        }
        val activation = available(storage, RecordingSource())
        val session = activation.discoverSession(CatalogMediaType.MANGA)
        val states = mutableListOf<DiscoverSessionState>()
        session.states.onEach(states::add).launchIn(backgroundScope)
        val publishedEmpty = published(CatalogMediaType.MANGA, cards = emptyList())
        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(publishedEmpty)
        runCurrent()

        val result = session.refresh()
        runCurrent()

        assertEquals(
            CatalogAcquisitionResult.Failed(CatalogFailure.Storage(CatalogStorageOperation.PUBLISH_DISCOVER)),
            result,
        )
        assertEquals(publishedEmpty, states.last().persistence)
        assertTrue(states.last().persistence is DiscoverPersistenceState.Published)
    }

    @Test
    fun concurrentManualRefreshesJoinOneSourceExecutionAndRemoveTerminalWork() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val source = RecordingSource(discover = { mediaType ->
            entered.complete(Unit)
            release.await()
            testDiscoverAcquisition(mediaType)
        })
        val activation = available(storage, source)
        val session = activation.discoverSession(CatalogMediaType.MANGA)

        val first = async { session.refresh() }
        val second = async { session.refresh() }
        runCurrent()
        entered.await()

        assertEquals(listOf(CatalogMediaType.MANGA), source.discoverCalls)
        release.complete(Unit)
        assertEquals(CatalogAcquisitionResult.Success, first.await())
        assertEquals(CatalogAcquisitionResult.Success, second.await())
        advanceUntilIdle()
        assertEquals(0, activation.activeWorkCount())
    }

    @Test
    fun sourceCancellationPropagatesWithoutPublishingOrLeavingActiveWork() = runTest {
        val storage = RuntimeFakeStorage()
        val cancellation = CancellationException("caller cancelled")
        val activation = available(
            storage,
            RecordingSource(discover = { throw cancellation }),
        )
        val session = activation.discoverSession(CatalogMediaType.MANGA)

        val thrown = try {
            session.refresh()
            throw AssertionError("Expected cancellation")
        } catch (error: CancellationException) {
            error
        }
        advanceUntilIdle()

        assertEquals(cancellation.message, thrown.message)
        assertTrue(storage.discoverCommands.isEmpty())
        assertEquals(0, activation.activeWorkCount())
    }

    private suspend fun TestScope.available(
        storage: RuntimeFakeStorage,
        source: RecordingSource,
    ): CatalogCapabilityActivation.Available {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CatalogRuntimeFactory(
            binding = TEST_BINDING.copy(acquisitionSource = source),
            openStorage = { storage },
            wallClockEpochMs = { 77L },
            dispatchers = CatalogExecutionDispatchers(dispatcher, dispatcher),
        ).createSession().activate() as CatalogCapabilityActivation.Available
    }

    private fun published(
        mediaType: CatalogMediaType,
        cards: List<app.openstory.catalog.domain.read.DiscoverCard> = RuntimeTestData.publishedCards(mediaType),
    ) = DiscoverPersistenceState.Published(
        generation = 4L,
        provenance = AcquisitionProvenance(TEST_SOURCE_KEY, "persisted", 44L),
        cards = cards,
    )
}
