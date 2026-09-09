package app.openstory.catalog.runtime.acquisition

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.runtime.RecordingSource
import app.openstory.catalog.runtime.RuntimeFakeStorage
import app.openstory.catalog.runtime.TEST_BINDING
import app.openstory.catalog.runtime.testDiscoverAcquisition
import app.openstory.catalog.runtime.testRef
import app.openstory.catalog.runtime.concurrency.CatalogMutationGate
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogAcquisitionExecutorTest {
    @Test
    fun concurrentDiscoverDemandJoinsOneExecutionAndUsesHostProvenance() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val source = RecordingSource(discover = { mediaType ->
            entered.complete(Unit)
            release.await()
            testDiscoverAcquisition(mediaType)
        })
        val executor = executor(storage, source, wallClock = { 912L })

        val first = async { executor.acquireDiscover(CatalogMediaType.MANGA) }
        val second = async { executor.acquireDiscover(CatalogMediaType.MANGA) }
        runCurrent()
        entered.await()

        assertEquals(listOf(CatalogMediaType.MANGA), source.discoverCalls)
        release.complete(Unit)
        assertEquals(CatalogAcquisitionResult.Success, first.await())
        assertEquals(CatalogAcquisitionResult.Success, second.await())
        advanceUntilIdle()

        val command = storage.discoverCommands.single()
        assertEquals(TEST_BINDING.catalogSourceKey, command.provenance.catalogSourceKey)
        assertEquals("host-v7", command.provenance.sourceVersion)
        assertEquals(912L, command.provenance.acquiredAtEpochMs)
        assertEquals(0, executor.activeWorkCount())
    }

    @Test
    fun nonCancellationSourceFailureMapsOnceAndTerminalEntryIsRemoved() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource(discover = { throw IllegalStateException("source detail") })
        val executor = executor(storage, source)

        val result = executor.acquireDiscover(CatalogMediaType.MANGA)
        advanceUntilIdle()

        assertEquals(
            CatalogAcquisitionResult.Failed(CatalogFailure.Acquisition(CatalogOperation.DISCOVER)),
            result,
        )
        assertEquals(1, source.discoverCalls.size)
        assertEquals(0, executor.activeWorkCount())
    }

    @Test
    fun sourceCancellationPropagatesUnchangedAndCleansEntry() = runTest {
        val cancellation = CancellationException("source cancelled")
        val source = RecordingSource(story = { throw cancellation })
        val executor = executor(RuntimeFakeStorage(), source)

        val thrown = try {
            executor.acquireStoryDetail(testRef())
            throw AssertionError("Expected cancellation")
        } catch (error: CancellationException) {
            error
        }
        advanceUntilIdle()

        assertEquals(cancellation.message, thrown.message)
        assertEquals(0, executor.activeWorkCount())
    }

    @Test
    fun rawDiscoverWriteFailureMapsToTypedStorageFailure() = runTest {
        val storage = RuntimeFakeStorage().apply {
            discoverPublishFailure = IllegalStateException("sqlite detail")
        }
        val executor = executor(storage, RecordingSource())

        val result = executor.acquireDiscover(CatalogMediaType.MANGA)
        advanceUntilIdle()

        assertEquals(
            CatalogAcquisitionResult.Failed(CatalogFailure.Storage(CatalogStorageOperation.PUBLISH_DISCOVER)),
            result,
        )
    }

    private fun TestScope.executor(
        storage: RuntimeFakeStorage,
        source: RecordingSource,
        wallClock: () -> Long = { 1L },
    ): CatalogAcquisitionExecutor {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher)
        val importer = CatalogImporter(
            writePort = storage,
            activeStoryPins = ActiveStoryPins(storage, CatalogMutationGate()),
            dispatchers = dispatchers,
        )
        return CatalogAcquisitionExecutor(
            binding = TEST_BINDING.copy(acquisitionSource = source),
            importer = importer,
            wallClockEpochMs = wallClock,
            dispatchers = dispatchers,
            parentScope = backgroundScope,
        )
    }
}
