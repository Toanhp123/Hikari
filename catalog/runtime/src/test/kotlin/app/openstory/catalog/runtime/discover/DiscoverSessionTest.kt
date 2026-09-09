package app.openstory.catalog.runtime.discover

import app.openstory.catalog.domain.failure.CatalogFailure
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
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.testDiscoverAcquisition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DiscoverSessionTest {
    @Test
    fun repeatedAbsentEmissionsStartOneBootstrap() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val source = RecordingSource(discover = { mediaType ->
            entered.complete(Unit)
            release.await()
            testDiscoverAcquisition(mediaType)
        })
        val session = available(storage, TEST_BINDING.copy(acquisitionSource = source))
            .discoverSession(CatalogMediaType.MANGA)
        val states = mutableListOf<DiscoverSessionState>()
        session.states.onEach(states::add).launchIn(backgroundScope)
        runCurrent()

        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(DiscoverPersistenceState.Absent)
        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(DiscoverPersistenceState.Absent)
        runCurrent()
        entered.await()

        assertEquals(1, source.discoverCalls.size)
        assertTrue(states.any { it.acquisition == CatalogAcquisitionStatus.Running })
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, source.discoverCalls.size)
    }

    @Test
    fun multipleStateCollectorsShareOnePersistenceObserver() = runTest {
        val storage = RuntimeFakeStorage()
        val session = available(storage, TEST_BINDING).discoverSession(CatalogMediaType.MANGA)

        session.states.launchIn(backgroundScope)
        session.states.launchIn(backgroundScope)
        runCurrent()

        assertEquals(1, storage.discoverObserveCount)
    }

    @Test
    fun publishedEmptyAndPublishedContentNeverBootstrap() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource()
        val activation = available(storage, TEST_BINDING.copy(acquisitionSource = source))
        activation.discoverSession(CatalogMediaType.MANGA).states.launchIn(backgroundScope)
        activation.discoverSession(CatalogMediaType.LIGHT_NOVEL).states.launchIn(backgroundScope)
        runCurrent()

        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(
            DiscoverPersistenceState.Published(
                generation = 1L,
                provenance = AcquisitionProvenance(TEST_SOURCE_KEY, "persisted", 1L),
                cards = emptyList(),
            ),
        )
        storage.discoverFlows.getValue(CatalogMediaType.LIGHT_NOVEL).emit(
            DiscoverPersistenceState.Published(
                generation = 2L,
                provenance = AcquisitionProvenance(TEST_SOURCE_KEY, "persisted", 2L),
                cards = RuntimeTestData.publishedCards(CatalogMediaType.LIGHT_NOVEL),
            ),
        )
        advanceUntilIdle()

        assertTrue(source.discoverCalls.isEmpty())
    }

    @Test
    fun absentKnownBindingWithoutSourceExposesUnavailableWithoutExecution() = runTest {
        val storage = RuntimeFakeStorage()
        val session = available(storage, TEST_BINDING).discoverSession(CatalogMediaType.MANGA)
        val states = mutableListOf<DiscoverSessionState>()
        session.states.onEach(states::add).launchIn(backgroundScope)
        runCurrent()

        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(DiscoverPersistenceState.Absent)
        runCurrent()
        advanceUntilIdle()

        assertTrue(
            states.any {
                it.persistence == DiscoverPersistenceState.Absent &&
                    it.acquisition == CatalogAcquisitionStatus.Failed(CatalogFailure.SourceUnavailable)
            },
        )
    }

    @Test
    fun switchingMediaBootstrapsOnlyTheExactAbsentScope() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource()
        val activation = available(storage, TEST_BINDING.copy(acquisitionSource = source))
        activation.discoverSession(CatalogMediaType.MANGA).states.launchIn(backgroundScope)
        activation.discoverSession(CatalogMediaType.LIGHT_NOVEL).states.launchIn(backgroundScope)
        runCurrent()

        storage.discoverFlows.getValue(CatalogMediaType.MANGA).emit(
            DiscoverPersistenceState.Published(
                generation = 1L,
                provenance = AcquisitionProvenance(TEST_SOURCE_KEY, "persisted", 1L),
                cards = emptyList(),
            ),
        )
        storage.discoverFlows.getValue(CatalogMediaType.LIGHT_NOVEL).emit(DiscoverPersistenceState.Absent)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf(CatalogMediaType.LIGHT_NOVEL), source.discoverCalls)
    }

    @Test
    fun readFailureIsExposedAsTypedStorageFailure() = runTest {
        val storage = RuntimeFakeStorage().apply { discoverReadFailure = IllegalStateException("read details") }
        val states = mutableListOf<DiscoverSessionState>()
        available(storage, TEST_BINDING).discoverSession(CatalogMediaType.MANGA)
            .states.onEach(states::add).launchIn(backgroundScope)
        runCurrent()
        advanceUntilIdle()

        assertEquals(
            CatalogAcquisitionStatus.Failed(CatalogFailure.Storage(CatalogStorageOperation.READ_DISCOVER)),
            states.single().acquisition,
        )
    }

    private suspend fun TestScope.available(
        storage: RuntimeFakeStorage,
        binding: app.openstory.catalog.runtime.source.CatalogSourceBinding,
    ): CatalogCapabilityActivation.Available {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val activation = CatalogRuntimeFactory(
            binding = binding,
            openStorage = { storage },
            wallClockEpochMs = { 55L },
            dispatchers = CatalogExecutionDispatchers(dispatcher, dispatcher),
        ).createSession().activate()
        return activation as CatalogCapabilityActivation.Available
    }
}
